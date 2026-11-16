package dev.ledger.operation;

import dev.ledger.integration.AccountClient;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class OperationService {
    private final OperationRepository repository;
    private final AccountClient accounts;
    private final OperationTransactions transactions;
    public OperationService(OperationRepository repository, AccountClient accounts, OperationTransactions transactions) {
        this.repository = repository;
        this.accounts = accounts;
        this.transactions = transactions;
    }
    public Registration register(UUID requestId, CreateOperation request) {
        // A replay must work even if the account service is temporarily unavailable.
        var existing = repository.findByRequestId(requestId);
        if (existing.isPresent()) {
            if (!existing.get().matches(request)) throw new ConflictException("Idempotency key was used with another payload");
            return new Registration(existing.get().view(), false);
        }
        // No database transaction or connection is held during the HTTP call.
        accounts.requireActive(request.accountId(), request.currency());
        return transactions.register(requestId, request);
    }
    public record Registration(OperationView operation, boolean created) {}
}
