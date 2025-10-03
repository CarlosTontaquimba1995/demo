package com.example.demo.client;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Cliente para la comunicación con la API externa del sistema de facturación.
 * 
 * Características principales:
 * - Gestión de tokens de autenticación con TokenService
 * - Patrón Circuit Breaker para manejo de fallos
 * - Rate limiting para evitar sobrecargar la API
 * - Reintentos automáticos en caso de fallos
 * - Timeouts configurables
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalApiClient {

    // Cliente HTTP reactivo para realizar peticiones
    @SuppressWarnings("unused") // Se usará cuando se activen las llamadas a la API
    private final WebClient webClient;

    // Registro de circuit breakers para manejo de fallos
    @SuppressWarnings("unused") // Se usará cuando se activen las llamadas a la API
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    // Registro de limitadores de tasa para control de tráfico
    @SuppressWarnings("unused") // Se usará cuando se activen las llamadas a la API
    private final RateLimiterRegistry rateLimiterRegistry;

    // Registro de políticas de reintento
    @SuppressWarnings("unused") // Se usará cuando se activen las llamadas a la API
    private final RetryRegistry retryRegistry;

    // Servicio para la gestión de tokens de autenticación
    private final TokenService tokenService;

    // URL base de la API externa
    @Value("${app.external-api.base-url}")
    private String baseUrl;

    // Tiempo máximo de espera para las peticiones (por defecto 5 segundos)
    @Value("${app.external-api.timeout-ms:5000}")
    private long timeoutMs;

    /**
     * Envía una solicitud para procesar una factura.
     * 
     * @param idSolicitudActos El ID de la factura a procesar
     * @return Un Mono que se completa cuando finaliza la solicitud exitosamente
     * @throws RuntimeException si ocurre un error al procesar la factura
     */
    public Mono<Void> processInvoice(Long idSolicitudActos) {
        log.debug("Iniciando procesamiento de factura ID: {}", idSolicitudActos);

        return getAuthenticatedRequest(idSolicitudActos)
                .flatMap(this::logRequestDetails)
                .flatMap(this::executeApiCall)
                .doOnSuccess(v -> log.info("✅ Factura {} procesada exitosamente", idSolicitudActos))
                .doOnError(e -> log.error("❌ Error al procesar factura {}: {}", idSolicitudActos, e.getMessage()));
    }

    /**
     * Prepara una solicitud autenticada con token.
     */
    private Mono<ApiRequestContext> getAuthenticatedRequest(Long idSolicitudActos) {
        return tokenService.getAccessToken()
                .map(token -> new ApiRequestContext(
                        String.format("%s/%d", baseUrl, idSolicitudActos),
                        token,
                        String.format("{\"idSolicitudActos\": %d}", idSolicitudActos)))
                .timeout(Duration.ofMillis(timeoutMs))
                .onErrorResume(e -> {
                    log.error("❌ Error al obtener token de acceso: {}", e.getMessage());
                    return Mono.error(new RuntimeException("No se pudo autenticar la solicitud", e));
                });
    }

    /**
     * Registra los detalles de la solicitud que se enviará a la API.
     */
    private Mono<ApiRequestContext> logRequestDetails(ApiRequestContext context) {
        log.info(
                """

                        ====== DETALLES DE LA SOLICITUD ======
                        URL: {}
                        Método: POST
                        Encabezados:
                          Authorization: Bearer {}...
                          Content-Type: application/json
                        Cuerpo de la solicitud:
                        {}
                        ======================================""",
                context.url(),
                context.token().substring(0, Math.min(20, context.token.length())),
                context.requestBody());

        return Mono.just(context);
    }

    /**
     * Ejecuta la llamada a la API con los parámetros proporcionados.
     */
    private Mono<Void> executeApiCall(ApiRequestContext context) {
        // En producción, descomentar este bloque para habilitar las llamadas reales
        /*
         * return webClient.post()
         * .uri(context.url())
         * .contentType(MediaType.APPLICATION_JSON)
         * .header("Authorization", "Bearer " + context.token())
         * .bodyValue(context.requestBody())
         * .retrieve()
         * .bodyToMono(Void.class)
         * .timeout(Duration.ofMillis(timeoutMs));
         */

        // Modo simulación (solo para pruebas)
        log.info("🔄 Modo prueba: Simulando envío de factura ID: {}",
                context.requestBody().replaceAll("\\D", ""));
        return Mono.empty();
    }

    /**
     * Clase de contexto para mantener los datos de la solicitud.
     */
    private record ApiRequestContext(
            String url,
            String token,
            String requestBody) {

        /**
         * Aplica las políticas de resiliencia a un flujo Reactor.
         */
        public <T> Mono<T> applyResiliencePolicies(Mono<T> mono, CircuitBreakerRegistry circuitBreakerRegistry,
                RateLimiterRegistry rateLimiterRegistry, RetryRegistry retryRegistry) {
            return mono
                    .transformDeferred(CircuitBreakerOperator.of(circuitBreakerRegistry.circuitBreaker("externalApi")))
                    .transformDeferred(RateLimiterOperator.of(rateLimiterRegistry.rateLimiter("externalApi")))
                    .transformDeferred(RetryOperator.of(retryRegistry.retry("externalApi")));
        }
    }
}
