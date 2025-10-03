package com.example.demo;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.beans.factory.annotation.Value;
import reactor.netty.http.client.HttpClient;
import io.netty.channel.ChannelOption;

import java.time.Duration;

/**
 * Main application class for the Notarial Orchestrator.
 * Enables scheduling and async processing with virtual threads.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableJpaRepositories(basePackages = "com.example.demo.repository")
@EntityScan(basePackages = "com.example.demo.entity")
@ComponentScan(basePackages = "com.example.demo")
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	/**
	 * Configures the WebClient for making HTTP requests to the external API.
	 * 
	 * @param builder The WebClient builder
	 * @return Configured WebClient instance
	 */
	@Bean
	public WebClient webClient(WebClient.Builder builder,
			@Value("${app.external-api.base-url}") String baseUrl,
			@Value("${app.external-api.timeout-ms}") long timeoutMs) {
		return builder
				.baseUrl(baseUrl)
				.clientConnector(new ReactorClientHttpConnector(
						HttpClient.create()
								.responseTimeout(Duration.ofMillis(timeoutMs))
								.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeoutMs)))
				.build();
	}

	/**
	 * Configures the Circuit Breaker registry.
	 */
	@Bean
	public CircuitBreakerRegistry circuitBreakerRegistry() {
		CircuitBreakerConfig config = CircuitBreakerConfig.custom()
				.slidingWindowSize(20)
				.permittedNumberOfCallsInHalfOpenState(5)
				.waitDurationInOpenState(Duration.ofSeconds(30))
				.failureRateThreshold(50)
				.build();

		return CircuitBreakerRegistry.of(config);
	}

	/**
	 * Configures the Rate Limiter registry.
	 */
	@Bean
	public RateLimiterRegistry rateLimiterRegistry() {
		RateLimiterConfig config = RateLimiterConfig.custom()
				.limitForPeriod(100)
				.limitRefreshPeriod(Duration.ofSeconds(1))
				.timeoutDuration(Duration.ofSeconds(1))
				.build();

		return RateLimiterRegistry.of(config);
	}

	/**
	 * Configures the Retry registry.
	 */
	@Bean
	public RetryRegistry retryRegistry() {
		RetryConfig config = RetryConfig.custom()
				.maxAttempts(3)
				.waitDuration(Duration.ofMillis(100))
				.build();

		return RetryRegistry.of(config);
	}
}
