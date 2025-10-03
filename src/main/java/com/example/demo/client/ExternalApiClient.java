package com.example.demo.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
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

    @SuppressWarnings("unused") // Will be used when API calls are enabled
    private final WebClient webClient;
    @SuppressWarnings("unused") // Will be used when API calls are enabled
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    @SuppressWarnings("unused") // Will be used when API calls are enabled
    private final RateLimiterRegistry rateLimiterRegistry;
    @SuppressWarnings("unused") // Will be used when API calls are enabled
    private final RetryRegistry retryRegistry;
    private final TokenService tokenService;

    @Value("${app.external-api.base-url}")
    private String baseUrl;

    @Value("${app.external-api.timeout-ms:5000}")
    private long timeoutMs;

    /**
     * Sends a request to process an invoice.
     * 
     * @param idSolicitudActos The ID of the invoice to process
     * @return A Mono that completes when the request is done
     */
    public Mono<Void> processInvoice(Long idSolicitudActos) {
        log.debug("Preparing to process invoice: {}", idSolicitudActos);

        return tokenService.getAccessToken()
                .flatMap(token -> {
                    String url = String.format("%s/%d", baseUrl, idSolicitudActos);
                    String requestBody = "{\"idSolicitudActos\": " + idSolicitudActos + "}";

                    // Log the request details in a clean format
                    log.info("""
                                                        \n=== API Request Details (Dry Run) ===
                            URL: {}
                            Method: POST
                            Headers:
                              Authorization: Bearer {}
                              Content-Type: application/json
                            Request Body:
                            {}
                            ===============================""",
                            url,
                            token.substring(0, Math.min(20, token.length())) + "...",
                            requestBody);

                    log.info("Dry run complete - No API call was made for invoice ID: {}", idSolicitudActos);
                    return Mono.<Void>empty();

                    /*
                     * Uncomment this block to enable actual API calls
                     * log.info("Sending request to external API for invoice: {}",
                     * idSolicitudActos);
                     * return webClient.post()
                     * .uri(url)
                     * .header("Authorization", "Bearer " + token)
                     * .contentType(MediaType.APPLICATION_JSON)
                     * .bodyValue(requestBody)
                     * .retrieve()
                     * .onStatus(
                     * status -> status.is4xxClientError() || status.is5xxServerError(),
                     * response -> response.bodyToMono(String.class)
                     * .flatMap(error -> {
                     * log.error("Error processing invoice {}: {}", idSolicitudActos, error);
                     * return Mono.error(new RuntimeException(
                     * "Failed to process invoice: " + response.statusCode()));
                     * }))
                     * .bodyToMono(Void.class)
                     * .transformDeferred(CircuitBreakerOperator.of(circuitBreakerRegistry.
                     * circuitBreaker("externalApi")))
                     * .transformDeferred(RateLimiterOperator.of(rateLimiterRegistry.rateLimiter(
                     * "externalApi")))
                     * .transformDeferred(RetryOperator.of(retryRegistry.retry("externalApi")));
                     */
                })
                .timeout(Duration.ofMillis(timeoutMs))
                .doOnSuccess(v -> log.info("Successfully processed invoice: {}", idSolicitudActos))
                .onErrorResume(e -> {
                    log.error("Error in invoice processing: {}", idSolicitudActos, e);
                    return Mono.error(new RuntimeException("Failed to process invoice: " + e.getMessage(), e));
                });
    }
}
