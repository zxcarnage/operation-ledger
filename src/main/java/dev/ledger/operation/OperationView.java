package dev.ledger.operation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record OperationView(UUID id, UUID accountId, BigDecimal amount, String currency,
                            String description, Status status, Instant createdAt, Instant updatedAt, long version) {}
