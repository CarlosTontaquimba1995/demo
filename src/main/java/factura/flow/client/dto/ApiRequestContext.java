package factura.flow.client.dto;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import reactor.core.publisher.Mono;

public record ApiRequestContext(
    String url,
    String token,
    String requestBody
) {
    public <T> Mono<T> applyResiliencePolicies(
        Mono<T> publisher,
        CircuitBreakerRegistry circuitBreakerRegistry,
        RateLimiterRegistry rateLimiterRegistry,
        RetryRegistry retryRegistry
    ) {
        return publisher
            .transform(io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator.of(
                circuitBreakerRegistry.circuitBreaker("externalApi")
            ))
            .transform(io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator.of(
                rateLimiterRegistry.rateLimiter("externalApi")
            ))
            .transform(io.github.resilience4j.reactor.retry.RetryOperator.of(
                retryRegistry.retry("externalApi")
            ));
    }
}
