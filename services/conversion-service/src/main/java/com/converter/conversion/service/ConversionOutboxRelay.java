package com.converter.conversion.service;

import com.converter.common.events.RabbitTopologyConstants;
import com.converter.conversion.entity.OutboxEventEntity;
import com.converter.conversion.repository.OutboxEventRepository;
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

/**
 * Transactional Outbox Relay:
 * 1) Short TX claims unlocked rows via SELECT ... FOR UPDATE SKIP LOCKED and sets a 30s lease.
 * 2) Publishes to RabbitMQ OUTSIDE any DB transaction.
 * 3) Short TX marks rows published or backs off on broker outage.
 */
@Component
public class ConversionOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(ConversionOutboxRelay.class);
    private static final int BATCH_SIZE = 25;

    private final OutboxEventRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ConversionOutboxRelay(
            OutboxEventRepository outboxRepository,
            RabbitTemplate rabbitTemplate,
            TransactionTemplate transactionTemplate,
            Clock clock
    ) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${conversion.outbox-poll-ms:1500}")
    public void pollAndPublish() {
        relayPendingBatch();
    }

    public int relayPendingBatch() {
        List<ClaimedOutboxEvent> claimed = claimBatch();
        int publishedCount = 0;
        for (ClaimedOutboxEvent evt : claimed) {
            try {
                Message message = MessageBuilder
                        .withBody(evt.payloadJson().getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                        .setMessageId(evt.eventId().toString())
                        .setType(evt.eventType())
                        .build();
                rabbitTemplate.send(RabbitTopologyConstants.EXCHANGE_EVENTS, evt.routingKey(), message);
                markPublished(evt.id());
                publishedCount++;
            } catch (Exception ex) {
                log.warn("Failed to publish outbox event {}: {}", evt.eventId(), ex.getMessage());
                markFailed(evt.id());
            }
        }
        return publishedCount;
    }

    private List<ClaimedOutboxEvent> claimBatch() {
        List<ClaimedOutboxEvent> result = transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            List<OutboxEventEntity> rows = outboxRepository.claimBatch(now, BATCH_SIZE);
            Instant leaseUntil = now.plusSeconds(30);
            for (OutboxEventEntity row : rows) {
                row.markAttemptFailed(leaseUntil);
            }
            outboxRepository.saveAll(rows);
            return rows.stream()
                    .map(r -> new ClaimedOutboxEvent(r.getId(), r.getEventId(), r.getEventType(), r.getRoutingKey(), r.getPayloadJson()))
                    .toList();
        });
        return result != null ? result : List.of();
    }

    private void markPublished(UUID id) {
        transactionTemplate.executeWithoutResult(status -> outboxRepository.findById(id).ifPresent(entity -> {
            entity.markPublished(clock.instant());
            outboxRepository.save(entity);
        }));
    }

    private void markFailed(UUID id) {
        transactionTemplate.executeWithoutResult(status -> outboxRepository.findById(id).ifPresent(entity -> {
            long backoffSeconds = Math.min(30L, 2L * Math.max(1, entity.getAttempts()));
            entity.markAttemptFailed(clock.instant().plusSeconds(backoffSeconds));
            outboxRepository.save(entity);
        }));
    }

    private record ClaimedOutboxEvent(
            UUID id,
            UUID eventId,
            String eventType,
            String routingKey,
            String payloadJson
    ) {}
}
