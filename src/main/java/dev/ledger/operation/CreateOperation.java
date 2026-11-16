package dev.ledger.operation;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
public record CreateOperation(
        @NotNull UUID accountId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotNull @Pattern(regexp = "EUR|USD|MDL|RUB") String currency,
        @NotBlank @Size(max = 250) String description) {}
