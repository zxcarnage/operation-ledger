package dev.ledger.api;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || !requestId.matches("[a-zA-Z0-9-]{1,64}")) requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-Id", requestId);
        long started = System.nanoTime();
        try { chain.doFilter(request, response); }
        finally {
            log.info("method={} path={} status={} durationMs={}", request.getMethod(), request.getRequestURI(),
                    response.getStatus(), (System.nanoTime() - started) / 1_000_000);
            MDC.remove("requestId");
        }
    }
}
