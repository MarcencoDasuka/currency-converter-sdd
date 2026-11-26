package com.converter.alert.service;

import com.converter.alert.entity.AlertOutboxEntity;
import com.converter.alert.repository.AlertOutboxRepository;
import com.converter.common.events.RabbitTopologyConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class AlertOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(AlertOutboxRelay.class);
    private static final int BATCH_SIZE = 25;

    private final AlertOutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public AlertOutboxRelay(
            AlertOutboxRepository outboxRepository,
            RabbitTemplate rabbitTemplate,
            TransactionTemplate transactionTemplate,
            Clock clock
    ) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${alert.outbox-poll-ms:1500}")
    public void pollAndPublish() {
        relayPendingBatch();
    }

    public int relayPendingBatch() {
        List<ClaimedEvent> claimed = claimBatch();
        int count = 0;
        for (ClaimedEvent evt : claimed) {
            try {
                Message msg = MessageBuilder
                        .withBody(evt.payloadJson().getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                        .setMessageId(evt.eventId().toString())
                        .setType(evt.eventType())
                        .build();
                rabbitTemplate.send(RabbitTopologyConstants.EXCHANGE_EVENTS, evt.routingKey(), msg);
                markPublished(evt.id());
                count++;
            } catch (Exception ex) {
                log.warn("Failed to publish alert outbox event {}: {}", evt.eventId(), ex.getMessage());
                markFailed(evt.id());
            }
        }
        return count;
    }

    private List<ClaimedEvent> claimBatch() {
        List<ClaimedEvent> list = transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            List<AlertOutboxEntity> rows = outboxRepository.claimBatch(now, BATCH_SIZE);
            Instant leaseUntil = now.plusSeconds(30);
            for (AlertOutboxEntity row : rows) {
                row.markAttemptFailed(leaseUntil);
            }
            outboxRepository.saveAll(rows);
            return rows.stream()
                    .map(r -> new ClaimedEvent(r.getId(), r.getEventId(), r.getEventType(), r.getRoutingKey(), r.getPayloadJson()))
                    .toList();
        });
        return list != null ? list : List.of();
    }

    private void markPublished(UUID id) {
        transactionTemplate.executeWithoutResult(status -> outboxRepository.findById(id).ifPresent(entity -> {
            entity.markPublished(clock.instant());
            outboxRepository.save(entity);
        }));
    }

    private void markFailed(UUID id) {
        transactionTemplate.executeWithoutResult(status -> outboxRepository.findById(id).ifPresent(entity -> {
            long backoff = Math.min(30L, 2L * Math.max(1, entity.getAttempts()));
            entity.markAttemptFailed(clock.instant().plusSeconds(backoff));
            outboxRepository.save(entity);
        }));
    }

    private record ClaimedEvent(UUID id, UUID eventId, String eventType, String routingKey, String payloadJson) {}
}
