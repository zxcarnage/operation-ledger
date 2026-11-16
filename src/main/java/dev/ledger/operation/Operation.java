package dev.ledger.operation;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "operations")
public class Operation {
    @Id private UUID id;
    @Column(nullable = false, unique = true) private UUID requestId;
    @Column(nullable = false) private UUID accountId;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false, length = 250) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;
    protected Operation() {}
    public void transition(Status target, Instant now) {
        status.requireTransition(target);
        status = target;
        updatedAt = now;
    }
    public boolean matches(CreateOperation request) {
        return accountId.equals(request.accountId()) && amount.compareTo(request.amount()) == 0
                && currency.equals(request.currency()) && description.equals(request.description());
    }
    public UUID getId() { return id; }
    public Status getStatus() { return status; }
    public OperationView view() {
        return new OperationView(id, accountId, amount, currency, description, status, createdAt, updatedAt, version);
    }
}
