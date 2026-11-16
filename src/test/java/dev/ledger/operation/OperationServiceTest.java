package dev.ledger.operation;

import dev.ledger.integration.AccountClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperationServiceTest {
    @Mock OperationRepository repository;
    @Mock AccountClient accounts;
    @Mock OperationTransactions transactions;
    @InjectMocks OperationService service;
    final UUID key = UUID.randomUUID();
    final CreateOperation request = new CreateOperation(UUID.randomUUID(), new BigDecimal("12.50"), "EUR", "Invoice");
    @Test void validatesAccountBeforeWriting() {
        when(repository.findByRequestId(key)).thenReturn(Optional.empty());
        service.register(key, request);
        var ordered = inOrder(accounts, transactions);
        ordered.verify(accounts).requireActive(request.accountId(), request.currency());
        ordered.verify(transactions).register(key, request);
    }
    @Test void unavailableAccountDoesNotStartDatabaseWrite() {
        when(repository.findByRequestId(key)).thenReturn(Optional.empty());
        doThrow(new AccountClient.AccountUnavailableException()).when(accounts).requireActive(any(), any());
        assertThrows(AccountClient.AccountUnavailableException.class, () -> service.register(key, request));
        verifyNoInteractions(transactions);
    }
    @Test void replayDoesNotCallAccountService() {
        var existing = mock(Operation.class);
        when(repository.findByRequestId(key)).thenReturn(Optional.of(existing));
        when(existing.matches(request)).thenReturn(true);
        assertFalse(service.register(key, request).created());
        verifyNoInteractions(accounts, transactions);
    }
    @Test void changedPayloadIsRejected() {
        var existing = mock(Operation.class);
        when(repository.findByRequestId(key)).thenReturn(Optional.of(existing));
        when(existing.matches(request)).thenReturn(false);
        assertThrows(ConflictException.class, () -> service.register(key, request));
        verifyNoInteractions(accounts, transactions);
    }
}
