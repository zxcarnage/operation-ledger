package dev.ledger.integration;

import dev.ledger.operation.Status;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record StatusMessage(@NotNull UUID eventId, @NotNull UUID operationId, @NotNull Status status,
                            @NotBlank @Size(max = 250) String reason) {}
