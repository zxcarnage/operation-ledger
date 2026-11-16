package dev.ledger.operation;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface OperationRepository extends JpaRepository<Operation, UUID> {
    Optional<Operation> findByRequestId(UUID requestId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Operation o where o.id = :id")
    Optional<Operation> lockById(@Param("id") UUID id);
    @Query("select new dev.ledger.operation.OperationView(o.id, o.accountId, o.amount, o.currency, o.description, o.status, o.createdAt, o.updatedAt, o.version) from Operation o where o.accountId = :accountId order by o.createdAt desc, o.id desc")
    List<OperationView> history(@Param("accountId") UUID accountId, Pageable pageable);
}
