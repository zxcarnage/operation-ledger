package dev.ledger.operation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class StatusTest {
    static Stream<org.junit.jupiter.params.provider.Arguments> transitions() {
        return Stream.of(Status.values()).flatMap(from -> Stream.of(Status.values()).map(to ->
                org.junit.jupiter.params.provider.Arguments.of(from, to,
                        (from == Status.NEW && (to == Status.PROCESSING || to == Status.CANCELLED)) ||
                        (from == Status.PROCESSING && (to == Status.COMPLETED || to == Status.FAILED)))));
    }
    @ParameterizedTest(name = "{0} -> {1}: allowed={2}")
    @MethodSource("transitions")
    void enforcesTransitionMatrix(Status from, Status to, boolean allowed) {
        if (allowed) assertDoesNotThrow(() -> from.requireTransition(to));
        else assertThrows(ConflictException.class, () -> from.requireTransition(to));
    }
}
