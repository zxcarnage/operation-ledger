package dev.ledger.api;

import dev.ledger.operation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;

@Validated
@RestController
@RequestMapping("/api/v1/operations")
public class OperationController {
    private final OperationService service;
    private final OperationTransactions transactions;
    private final OperationRepository repository;
    private final JdbcTemplate jdbc;
    public OperationController(OperationService service, OperationTransactions transactions,
                               OperationRepository repository, JdbcTemplate jdbc) {
        this.service = service; this.transactions = transactions; this.repository = repository; this.jdbc = jdbc;
    }
    @PostMapping
    public ResponseEntity<OperationView> register(@RequestHeader("Idempotency-Key") UUID key,
                                                @Valid @RequestBody CreateOperation request) {
        var result = service.register(key, request);
        return result.created() ? ResponseEntity.created(URI.create("/api/v1/operations/" + result.operation().id())).body(result.operation())
                : ResponseEntity.ok(result.operation());
    }
    @GetMapping("/{id}")
    public OperationView get(@PathVariable UUID id) { return find(id).view(); }

    @GetMapping
    public List<OperationView> list(@RequestParam UUID accountId,
                                    @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return repository.history(accountId, PageRequest.of(page, size));
    }
    @PatchMapping("/{id}/status")
    public OperationView change(@PathVariable UUID id, @Valid @RequestBody ChangeStatus request) {
        return transactions.change(id, request.status(), request.reason());
    }
    @PostMapping("/{id}/cancel")
    public OperationView cancel(@PathVariable UUID id, @Valid @RequestBody Cancel request) {
        return transactions.change(id, Status.CANCELLED, request.reason());
    }
    @GetMapping("/{id}/history")
    public List<HistoryEntry> history(@PathVariable UUID id) {
        find(id);
        return jdbc.query("select from_status,to_status,reason,changed_at from status_history where operation_id=? order by id",
                (rs, n) -> new HistoryEntry(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant()), id);
    }
    @GetMapping("/{id}/transitions")
    public List<Status> transitions(@PathVariable UUID id) { return find(id).getStatus().next().stream().sorted().toList(); }

    @GetMapping("/summary")
    public List<Summary> summary(@RequestParam UUID accountId) {
        return jdbc.query("select status,count(*) from operations where account_id=? group by status order by status",
                (rs, n) -> new Summary(rs.getString(1), rs.getLong(2)), accountId);
    }
    private Operation find(UUID id) { return repository.findById(id).orElseThrow(NotFoundException::new); }
    public record ChangeStatus(@NotNull Status status, @NotBlank @Size(max = 250) String reason) {}
    public record Cancel(@NotBlank @Size(max = 250) String reason) {}
    public record HistoryEntry(String fromStatus, String toStatus, String reason, Instant changedAt) {}
    public record Summary(String status, long count) {}
}
