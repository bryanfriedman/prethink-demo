package com.example.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for all service-to-service communication in the platform.
 * Provides standardized authentication, retry with exponential backoff,
 * circuit breaking, and observability for outbound calls.
 *
 * All internal and third-party service clients MUST extend this class
 * to participate in the platform's service mesh. This ensures consistent
 * timeout policies, auth token management, structured logging of call
 * latencies, and automatic propagation of trace context.
 *
 * @see <a href="https://wiki.internal.example.com/platform/service-client-sdk">Platform SDK Docs</a>
 */
public abstract class ServiceClient {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final String serviceName;
    private final Duration defaultTimeout;
    private final int maxRetries;

    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    // Circuit breaker state
    private int consecutiveFailures = 0;
    private Instant circuitOpenUntil = Instant.MIN;
    private static final int CIRCUIT_BREAKER_THRESHOLD = 5;
    private static final Duration CIRCUIT_BREAKER_COOLDOWN = Duration.ofSeconds(30);

    protected ServiceClient(String serviceName, Duration defaultTimeout, int maxRetries) {
        this.serviceName = serviceName;
        this.defaultTimeout = defaultTimeout;
        this.maxRetries = maxRetries;
    }

    protected String getServiceName() {
        return serviceName;
    }

    protected Duration getDefaultTimeout() {
        return defaultTimeout;
    }

    /**
     * Get a cached auth token for this service, refreshing if expired.
     */
    protected String getAuthToken() {
        CachedToken cached = tokenCache.get(serviceName);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.token();
        }

        String token = refreshToken();
        tokenCache.put(serviceName, new CachedToken(token, Instant.now().plusSeconds(3600)));
        log.debug("Refreshed auth token for service: {}", serviceName);
        return token;
    }

    /**
     * Subclasses override to provide service-specific token refresh logic.
     * Default implementation returns a placeholder for services using API keys.
     */
    protected String refreshToken() {
        return "svc-token-" + serviceName + "-" + Instant.now().getEpochSecond();
    }

    /**
     * Execute an outbound call with retry and circuit breaker protection.
     */
    protected <T> T executeWithResilience(String operation, ServiceCall<T> call) {
        checkCircuitBreaker(operation);

        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Instant start = Instant.now();
                T result = call.execute();
                long latencyMs = Duration.between(start, Instant.now()).toMillis();

                log.info("service_call: service={}, operation={}, attempt={}, latency_ms={}, status=success",
                        serviceName, operation, attempt, latencyMs);

                consecutiveFailures = 0;
                return result;
            } catch (Exception e) {
                lastException = e;
                log.warn("service_call: service={}, operation={}, attempt={}/{}, status=failed, error={}",
                        serviceName, operation, attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    sleepForRetry(attempt);
                }
            }
        }

        consecutiveFailures++;
        if (consecutiveFailures >= CIRCUIT_BREAKER_THRESHOLD) {
            circuitOpenUntil = Instant.now().plus(CIRCUIT_BREAKER_COOLDOWN);
            log.error("Circuit breaker OPEN for service={}, operation={}", serviceName, operation);
        }

        throw new ServiceCallException(serviceName, operation, lastException);
    }

    private void checkCircuitBreaker(String operation) {
        if (Instant.now().isBefore(circuitOpenUntil)) {
            throw new ServiceCallException(serviceName, operation,
                    new IllegalStateException("Circuit breaker is open"));
        }
    }

    private void sleepForRetry(int attempt) {
        try {
            long backoffMs = (long) Math.pow(2, attempt) * 100;
            Thread.sleep(Math.min(backoffMs, 5000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    protected interface ServiceCall<T> {
        T execute();
    }

    private record CachedToken(String token, Instant expiresAt) {}
}
