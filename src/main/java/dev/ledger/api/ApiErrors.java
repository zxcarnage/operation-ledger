package dev.ledger.api;

import dev.ledger.integration.AccountClient.AccountUnavailableException;
import dev.ledger.operation.*;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);
    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemDetail> missing(NotFoundException e) { return problem(404, e.getMessage()); }
    @ExceptionHandler({ConflictException.class, OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    ResponseEntity<ProblemDetail> conflict(Exception e) { return problem(409, "Operation conflicts with current state: " + (e instanceof ConflictException ? e.getMessage() : "concurrent modification")); }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class, MissingRequestHeaderException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ProblemDetail> invalid(Exception e) { return problem(400, "Invalid request parameters or body"); }
    @ExceptionHandler(AccountUnavailableException.class)
    ResponseEntity<ProblemDetail> unavailable(AccountUnavailableException e) { return problem(503, e.getMessage()); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e) {
        log.error("Unhandled request failure", e);
        return problem(500, "Internal server error");
    }
    private ResponseEntity<ProblemDetail> problem(int status, String detail) {
        var body = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), detail);
        body.setProperty("requestId", MDC.get("requestId"));
        return ResponseEntity.status(status).body(body);
    }
}
