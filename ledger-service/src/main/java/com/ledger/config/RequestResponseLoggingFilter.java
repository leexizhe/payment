package com.ledger.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Emits one wide, structured "canonical log line" per request — the pattern big
 * observability shops (Stripe's canonical log lines, Honeycomb-style wide events)
 * favour over dumping raw payloads: a single high-dimensional JSON event you can
 * slice and dice, carrying the trace id so it joins to its Tempo trace.
 *
 * Bodies are NOT logged by default. They're captured only when a request is
 * sampled ({@code ledger.logging.body-sample-rate}) or when it 5xx's — head
 * sampling plus keep-the-errors, the affordable stand-in for tail sampling. Even
 * then we log payload *sizes* always and contents only on the sampled slice.
 */
@Slf4j
@Component
// Run INSIDE Micrometer's tracing filter (not HIGHEST_PRECEDENCE) so the trace id
// is still in MDC when we emit the event at request completion.
@Order(Ordered.LOWEST_PRECEDENCE)
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final int MAX_BODY_CHARS = 2_000;
    /** Above this, don't parse into the event — fall back to a truncated string. */
    private static final int MAX_BODY_BYTES = 8_192;

    private final ObjectMapper objectMapper;
    private final double bodySampleRate;

    public RequestResponseLoggingFilter(
            ObjectMapper objectMapper,
            @Value("${ledger.logging.body-sample-rate:0.0}") double bodySampleRate) {
        this.objectMapper = objectMapper;
        this.bodySampleRate = bodySampleRate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Decide sampling up front (head sampling). Only sampled requests pay the
        // cost of buffering the request body; everyone else streams through.
        boolean sampled = bodySampleRate > 0 && ThreadLocalRandom.current().nextDouble() < bodySampleRate;
        HttpServletRequest reqToUse = sampled ? new CachedBodyRequestWrapper(request) : request;
        var res = new ContentCachingResponseWrapper(response);

        long start = System.nanoTime();
        Exception error = null;
        try {
            filterChain.doFilter(reqToUse, res);
        } catch (Exception ex) {
            error = ex;
            throw ex;
        } finally {
            long tookMs = (System.nanoTime() - start) / 1_000_000;
            emitEvent(request, reqToUse, res, tookMs, sampled, error);
            // MUST run last, or the buffered response body never reaches the client.
            res.copyBodyToResponse();
        }
    }

    private void emitEvent(HttpServletRequest original,
                           HttpServletRequest reqToUse,
                           ContentCachingResponseWrapper res,
                           long tookMs,
                           boolean sampled,
                           Exception error) {
        int status = res.getStatus();
        // Keep full detail on server errors even if this request wasn't sampled.
        boolean includeBodies = sampled || status >= 500;

        var event = new LinkedHashMap<String, Object>();
        event.put("event", "http.access");
        event.put("method", original.getMethod());
        event.put("route", route(original));
        event.put("status", status);
        event.put("outcome", outcome(status));
        event.put("latencyMs", tookMs);
        putIfPresent(event, "traceId", MDC.get("traceId"));
        putIfPresent(event, "spanId", MDC.get("spanId"));
        putIfPresent(event, "idempotencyKey", original.getHeader("Idempotency-Key"));
        event.put("bytesIn", bytesIn(original, reqToUse));
        event.put("bytesOut", res.getContentSize());
        event.put("sampled", sampled);
        if (error != null) {
            event.put("error", error.getClass().getSimpleName());
        }
        if (includeBodies) {
            if (reqToUse instanceof CachedBodyRequestWrapper cached) {
                putIfPresent(event, "requestBody", bodyValue(cached.getBody()));
            }
            putIfPresent(event, "responseBody", bodyValue(res.getContentAsByteArray()));
        }

        try {
            log.info(objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            // Never let logging break the request.
            log.info("http.access (unserializable): {}", event);
        }
    }

    /** Templated path ("/api/accounts/{id}"), not the raw one, so IDs stay out of logs. */
    private String route(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern != null) {
            return pattern.toString();
        }
        String uri = request.getRequestURI();
        return StringUtils.hasText(request.getQueryString()) ? uri + "?…" : uri;
    }

    private String outcome(int status) {
        if (status >= 500) return "server_error";
        if (status >= 400) return "client_error";
        return "success";
    }

    private long bytesIn(HttpServletRequest original, HttpServletRequest reqToUse) {
        if (reqToUse instanceof CachedBodyRequestWrapper cached) {
            return cached.getBody().length;
        }
        return Math.max(original.getContentLengthLong(), 0);
    }

    /**
     * Embed the body as a parsed JSON node so it nests cleanly in the event
     * ({@code "requestBody":{...}}) instead of a double-escaped string. Falls back
     * to a plain (truncated) string when the body isn't JSON or is oversized.
     */
    private Object bodyValue(byte[] content) {
        if (content.length == 0) {
            return null;
        }
        if (content.length <= MAX_BODY_BYTES) {
            try {
                return objectMapper.readTree(content);
            } catch (IOException notJson) {
                // fall through to the string form
            }
        }
        return body(content);
    }

    private String body(byte[] content) {
        if (content.length == 0) {
            return null;
        }
        // Collapse whitespace so a non-JSON body logs as a single tidy value.
        String body = new String(content, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
        return body.length() > MAX_BODY_CHARS ? body.substring(0, MAX_BODY_CHARS) + "…(truncated)" : body;
    }

    private static void putIfPresent(Map<String, Object> event, String key, String value) {
        if (StringUtils.hasText(value)) {
            event.put(key, value);
        }
    }

    private static void putIfPresent(Map<String, Object> event, String key, Object value) {
        if (value != null) {
            event.put(key, value);
        }
    }

    /** Skip Actuator so Prometheus scrapes don't flood the log every few seconds. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    /**
     * Buffers the request body into a byte[] so it can be both logged and read by
     * the handler. Only used on the sampled slice, so the buffering cost isn't paid
     * on every request.
     */
    private static final class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
        }

        byte[] getBody() {
            return body;
        }

        @Override
        public ServletInputStream getInputStream() {
            var buffer = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return buffer.read();
                }

                @Override
                public boolean isFinished() {
                    return buffer.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
