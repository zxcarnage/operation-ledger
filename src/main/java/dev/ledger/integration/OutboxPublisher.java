package dev.ledger.integration;

import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "ledger.outbox-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    public OutboxPublisher(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka) { this.jdbc = jdbc; this.kafka = kafka; }
    @Scheduled(fixedDelayString = "${ledger.outbox-delay-ms:1000}")
    @Transactional(timeout = 30)
    public void publish() {
        // One event per transaction bounds lock duration even when Kafka is unavailable.
        var rows = jdbc.query("select event_id,operation_id,payload from outbox where published_at is null order by created_at,event_id limit 1 for update skip locked",
                (rs, n) -> new Pending(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3)));
        for (Pending row : rows) {
            try {
                kafka.send("operation-events", row.operationId().toString(), row.payload()).get(12, TimeUnit.SECONDS);
                jdbc.update("update outbox set published_at=now(), attempts=attempts+1 where event_id=?", row.eventId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Outbox interrupted", e);
            } catch (Exception e) {
                jdbc.update("update outbox set attempts=attempts+1 where event_id=?", row.eventId());
                log.warn("Outbox publish failed eventId={} operationId={}", row.eventId(), row.operationId(), e);
            }
        }
    }
    private record Pending(UUID eventId, UUID operationId, String payload) {}
}
