package dev.ledger.integration;

import dev.ledger.operation.*;
import jakarta.validation.Validator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

@Service
public class StatusMessageHandler {
    private final JdbcTemplate jdbc;
    private final OperationTransactions transactions;
    private final Validator validator;
    public StatusMessageHandler(JdbcTemplate jdbc, OperationTransactions transactions, Validator validator) {
        this.jdbc = jdbc; this.transactions = transactions; this.validator = validator;
    }
    @Transactional(timeout = 5)
    public void handle(StatusMessage message) {
        if (!validator.validate(message).isEmpty()) throw new IllegalArgumentException("Invalid status event");
        String hash = hash(message.operationId() + "|" + message.status() + "|" + message.reason());
        int inserted = jdbc.update("insert into processed_messages(event_id,payload_hash) values (?,?) on conflict(event_id) do nothing",
                message.eventId(), hash);
        if (inserted == 0) {
            String previous = jdbc.queryForObject("select payload_hash from processed_messages where event_id=?", String.class, message.eventId());
            if (!hash.equals(previous)) throw new IllegalArgumentException("Event id reused with another payload");
            return;
        }
        transactions.change(message.operationId(), message.status(), message.reason());
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
