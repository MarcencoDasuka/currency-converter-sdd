package com.converter.notification.messaging;

import com.converter.common.events.PlatformEvent;
import com.converter.common.events.RabbitTopologyConstants;
import com.converter.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public NotificationEventListener(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitTopologyConstants.QUEUE_NOTIFICATIONS, ackMode = "MANUAL")
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        boolean redelivered = Boolean.TRUE.equals(message.getMessageProperties().isRedelivered());

        PlatformEvent event;
        try {
            String raw = new String(message.getBody(), StandardCharsets.UTF_8);
            event = objectMapper.readValue(raw, PlatformEvent.class);
            if (event == null || event.eventId() == null || event.userId() == null
                    || event.eventType() == null || event.eventType().isBlank()) {
                throw new IllegalArgumentException("Missing required eventId, userId, or eventType");
            }
        } catch (Exception ex) {
            log.warn("Poison/malformed message rejected to DLQ (deliveryTag={}): {}", deliveryTag, ex.getMessage());
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        if (event.correlationId() != null && !event.correlationId().isBlank()) {
            MDC.put("request_id", event.correlationId());
        }
        try {
            notificationService.recordFromEvent(event);
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            log.error("Failed persisting notification for event {} (redelivered={}): {}",
                    event.eventId(), redelivered, ex.getMessage());
            channel.basicNack(deliveryTag, false, !redelivered);
        } finally {
            MDC.remove("request_id");
        }
    }
}
