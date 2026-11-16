package dev.ledger.operation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
public class OperationTransactions {
    private final OperationRepository repository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public OperationTransactions(OperationRepository repository, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }
    @Transactional(timeout = 5)
    public OperationService.Registration register(UUID key, CreateOperation request) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        int inserted = jdbc.update("""
                insert into operations(id,request_id,account_id,amount,currency,description,status,created_at,updated_at)
                values (?,?,?,?,?,?,'NEW',?,?) on conflict(request_id) do nothing
                """, id, key, request.accountId(), request.amount(), request.currency(), request.description(),
                Timestamp.from(now), Timestamp.from(now));
        Operation operation = repository.findByRequestId(key).orElseThrow();
        if (!operation.matches(request)) throw new ConflictException("Idempotency key was used with another payload");
        if (inserted == 1) recordChange(operation, null, "Registered", now);
        return new OperationService.Registration(operation.view(), inserted == 1);
    }
    @Transactional(timeout = 5)
    public OperationView change(UUID id, Status target, String reason) {
        Operation operation = repository.lockById(id).orElseThrow(NotFoundException::new);
        Status previous = operation.getStatus();
        Instant now = Instant.now();
        operation.transition(target, now);
        repository.flush();
        recordChange(operation, previous, reason, now);
        return operation.view();
    }
    private void recordChange(Operation operation, Status previous, String reason, Instant now) {
        jdbc.update("insert into status_history(operation_id,from_status,to_status,reason,changed_at) values (?,?,?,?,?)",
                operation.getId(), previous == null ? null : previous.name(), operation.getStatus().name(), reason, Timestamp.from(now));
        UUID eventId = UUID.randomUUID();
        try {
            String payload = mapper.writeValueAsString(new OperationEvent(eventId, operation.getId(),
                    operation.getStatus(), operation.view().version(), now));
            jdbc.update("insert into outbox(event_id,operation_id,payload) values (?,?,?)", eventId, operation.getId(), payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize operation event", e);
        }
    }
    public record OperationEvent(UUID eventId, UUID operationId, Status status, long version, Instant occurredAt) {}
}
