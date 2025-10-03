package com.example.demo.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Client for communicating with the external API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalApiClient {

    private final WebClient webClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final RetryRegistry retryRegistry;

    @Value("${app.external-api.timeout-ms:5000}")
    private long timeoutMs;

    /**
     * Sends a request to process an invoice.
     * 
     * @param idSolicitudActos The ID of the invoice to process
     * @return A Mono that completes when the request is done
     */
    public Mono<Void> processInvoice(Long idSolicitudActos) {
        log.debug("Sending request to process invoice: {}", idSolicitudActos);
        
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("externalApi");
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("externalApi");
        Retry retry = retryRegistry.retry("externalApi");
        
        return Mono.defer(() -> makeApiCall(idSolicitudActos))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RateLimiterOperator.of(rateLimiter))
                .transformDeferred(RetryOperator.of(retry))
                .onErrorResume(RequestNotPermitted.class, e -> {
                    log.warn("Request not permitted by rate limiter for invoice {}: {}", idSolicitudActos, e.getMessage());
                    return Mono.error(new RuntimeException("Rate limit exceeded for invoice: " + idSolicitudActos));
                });
    }
    
    private Mono<Void> makeApiCall(Long idSolicitudActos) {
        return webClient.post()
                .uri("/{idSolicitudActos}", idSolicitudActos)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(
                    status -> !status.is2xxSuccessful(),
                    response -> {
                        log.error("Failed to process invoice {}: {}", idSolicitudActos, response.statusCode());
                        return Mono.error(new RuntimeException("Failed to process invoice: " + response.statusCode()));
                    }
                )
                .bodyToMono(Void.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .doOnSuccess(v -> log.info("Successfully processed invoice: {}", idSolicitudActos))
                .doOnError(e -> log.error("Error processing invoice: {}", idSolicitudActos, e));
    }
}
