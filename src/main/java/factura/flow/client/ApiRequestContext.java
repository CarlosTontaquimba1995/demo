package factura.flow.client;

public record ApiRequestContext(
    String url,
    String token,
    String requestBody
) {
    public <T> T applyResiliencePolicies(
        reactor.core.publisher.Mono<T> publisher,
        io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry circuitBreakerRegistry,
        io.github.resilience4j.ratelimiter.RateLimiterRegistry rateLimiterRegistry,
        io.github.resilience4j.retry.RetryRegistry retryRegistry
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
            ))
            .block();
    }
}
