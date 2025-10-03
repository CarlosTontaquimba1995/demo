package factura.flow;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Clase principal de la aplicación FacturaFlow.
 * Habilita la programación de tareas y el procesamiento asíncrono con hilos virtuales.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableJpaRepositories(basePackages = "factura.flow.repository")
@EntityScan(basePackages = "factura.flow.entity")
@ComponentScan(basePackages = "factura.flow")
public class FacturaFlow {

	public static void main(String[] args) {
		SpringApplication.run(FacturaFlow.class, args);
	}

	/**
	 * Configura el WebClient para realizar peticiones HTTP a la API externa.
	 * 
	 * @param builder Constructor de WebClient
	 * @param baseUrl URL base de la API externa
	 * @param timeoutMs Tiempo de espera en milisegundos
	 * @return Instancia configurada de WebClient
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
	 * Configura el registro del Circuit Breaker para manejo de fallos.
	 * 
	 * @return Instancia configurada de CircuitBreakerRegistry
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
	 * Configura el registro del Limitador de Tasa para control de tráfico.
	 * 
	 * @return Instancia configurada de RateLimiterRegistry
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
	 * Configura el registro de Reintentos para operaciones fallidas.
	 * 
	 * @return Instancia configurada de RetryRegistry
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
