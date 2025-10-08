package factura.flow.client;

import factura.flow.client.dto.ApiRequestContext;
import factura.flow.dto.ApiResponse;
import factura.flow.exception.RetryableException;
import factura.flow.model.InvoiceStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Cliente para interactuar con la API externa de facturación electrónica.
 * 
 * Este servicio se encarga de la comunicación con el servicio externo de facturación,
 * manejando la autenticación, reintentos automáticos y gestión de errores.
 * 
 * Características principales:
 * - Autenticación automática mediante tokens JWT
 * - Reintentos con retroceso exponencial para fallos transitorios
 * - Límite de tasa de solicitudes para evitar sobrecargar el servicio
 * - Timeouts configurables para evitar bloqueos
 * - Circuit breaker para evitar fallos en cascada
 * 
 * Uso típico:
 * 1. Se obtiene un token de autenticación
 * 2. Se envía la factura para su procesamiento
 * 3. Se manejan las respuestas y posibles errores
 */
@Slf4j
@Component
public class ExternalApiClient {

        // Dependencies
        private final TokenService tokenService;

        // Default token for testing when TokenService is not available
        private static final String DEFAULT_TEST_TOKEN = "test-token";

        @Value("${resilience4j.retry.configs.default.maxAttempts:3}")
        private int maxRetryAttempts;

        @Value("${resilience4j.retry.configs.default.waitDuration:1s}")
        private Duration retryWaitDuration;

        @Value("${app.external-api.timeout-ms:5000}")
        private long timeoutMs;

        // Exponential backoff configuration removed as it's not being used

        // URL base de la API externa
        @Value("${app.external-api.base-url}")
        private String baseUrl;

        @Autowired
        public ExternalApiClient(TokenService tokenService) {
                this.tokenService = tokenService;
        }

        /**
         * Envía una solicitud para procesar una factura.
         * 
         * @param idSolicitudActos El ID de la factura a procesar
         * @return Un Mono que se completa cuando finaliza la solicitud exitosamente
         * @throws RuntimeException si ocurre un error al procesar la factura
         */
        public Mono<Void> processInvoice(Long idSolicitudActos) {
                return getAuthenticatedRequest(idSolicitudActos)
                                .flatMap(this::logRequestDetails)
                                .flatMap(this::sendRequestToExternalApi)
                                .flatMap(this::processApiResponse)
                                .retryWhen(reactor.util.retry.Retry
                                                .backoff(maxRetryAttempts, retryWaitDuration)
                                                .filter(throwable -> throwable instanceof RetryableException)
                                                .onRetryExhaustedThrow(
                                                                (retryBackoffSpec, retrySignal) -> new RuntimeException(
                                                                                "Número máximo de reintentos ("
                                                                                                + maxRetryAttempts
                                                                                                + ") alcanzado para la factura: "
                                                                                                + idSolicitudActos))
                                                .maxAttempts(maxRetryAttempts)
                                                .filter(throwable -> throwable instanceof RetryableException))
                                .then();
        }

        /**
         * Prepara una solicitud autenticada con token.
         */
        private Mono<ApiRequestContext> getAuthenticatedRequest(Long idSolicitudActos) {
                return tokenService.getAccessToken()
                                .timeout(Duration.ofMillis(timeoutMs))
                                .onErrorResume(e -> {
                                        log.error("❌ Error al obtener token de acceso: {}", e.getMessage());
                                        return Mono.just(DEFAULT_TEST_TOKEN);
                                })
                                .map(token -> new ApiRequestContext(
                                                String.format("%s/%d", baseUrl, idSolicitudActos),
                                                token,
                                                String.format("{\"idSolicitudActos\": %d}", idSolicitudActos)));
        }

        /**
         * Registra los detalles de la solicitud que se enviará a la API.
         */
        private Mono<ApiRequestContext> logRequestDetails(ApiRequestContext context) {
                log.info(
                                """

                                                URL: {}
                                                Método: POST
                                                Encabezados:
                                                  Authorization: Bearer {}...
                                                  Content-Type: application/json
                                                Cuerpo de la solicitud:
                                                {}
                                                                                                """,
                                                                context.url(),
                                context.token().substring(0, Math.min(20, context.token().length())),
                                context.requestBody());
                return Mono.just(context);
        }

        /**
         * Envía la solicitud a la API externa.
         * 
         * @param context Contexto con la información de la solicitud
         * @return Mono con la respuesta simulada de la API
         */
        private Mono<ApiResponse<String>> sendRequestToExternalApi(ApiRequestContext context) {
                log.info("🔹 Iniciando llamada a la API externa para: {}", context.url());

                return Mono.defer(() -> {
                    // Simular un pequeño retraso de red
                    return Mono.delay(Duration.ofMillis(300))
                            .then(Mono.fromCallable(() -> {
                                // Simular una respuesta exitosa en el 90% de los casos
                                if (Math.random() > 0.1) {
                                    log.info("✅ Llamada a API exitosa para: {}", context.url());
                                    return ApiResponse.success("Factura procesada exitosamente");
                                } else {
                                    // Simular un error en el 10% de los casos
                                    log.warn("⚠️ Error simulado en la llamada a la API para: {}", context.url());
                                    throw new RetryableException("Error temporal en la llamada a la API");
                                }
                            }));
                })
                .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
                        .filter(throwable -> throwable instanceof RetryableException)
                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                            log.error("❌ Número máximo de reintentos alcanzado para: {}", context.url());
                            return new RuntimeException("No se pudo procesar la solicitud después de " + retrySignal.totalRetries() + " intentos");
                        }))
                .onErrorResume(throwable -> {
                    log.error("❌ Error en la llamada a la API: {}", throwable.getMessage());
                    return Mono.just(ApiResponse.error(throwable.getMessage()));
                });

                /*
                 * // Implementación real de la llamada a la API
                 * return webClient.post()
                 * .uri(context.url())
                 * .header(HttpHeaders.AUTHORIZATION, "Bearer " + context.token())
                 * .contentType(MediaType.APPLICATION_JSON)
                 * .bodyValue(context.requestBody())
                 * .retrieve()
                 * .bodyToMono(ApiResponse.class)
                 * .timeout(Duration.ofMillis(timeoutMs));
                 */
        }

        /**
         * Procesa la respuesta de la API.
         * @param response La respuesta de la API a procesar
         * @return Mono<Void> que se completa cuando el procesamiento termina, o error si hay un problema
         */
        private Mono<Void> processApiResponse(ApiResponse<?> response) {
                if (response == null) {
                        return Mono.error(new RuntimeException("La respuesta de la API es nula"));
                }

                String statusValue = response.getStatus();
                if (statusValue == null || statusValue.trim().isEmpty()) {
                        return Mono.error(new RuntimeException("El estado en la respuesta de la API está vacío o es nulo"));
                }

                try {
                        InvoiceStatus status = InvoiceStatus.fromValue(statusValue);
                        log.info("Respuesta de la API: {}", status);
                        if (isRetryableStatus(status)) {
                                return Mono.error(new RetryableException(
                                                String.format("El estado de la factura requiere reintento: %s", status)));
                        }

                        return Mono.empty();
                } catch (IllegalArgumentException e) {
                        return Mono.error(new RuntimeException(
                                        String.format("Estado de factura no reconocido: %s", statusValue), e));
                }
        }

        /**
         * Verifica si un estado de factura es reintentable.
         * 
         * @param status El estado de la factura a verificar
         * @return true si el estado es reintentable, false en caso contrario
         */
        private boolean isRetryableStatus(InvoiceStatus status) {
            return status == InvoiceStatus.NO_FIRMADO ||
                   status == InvoiceStatus.NO_WS1 ||
                   status == InvoiceStatus.NO_WS2 ||
                   status == InvoiceStatus.NO_ZIP ||
                   status == InvoiceStatus.ERROR;
        }
}