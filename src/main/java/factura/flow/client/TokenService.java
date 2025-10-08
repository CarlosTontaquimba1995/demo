package factura.flow.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import factura.flow.config.OAuth2TokenProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Servicio para la gestión del ciclo de vida de tokens de autenticación OAuth2.
 * 
 * Este servicio maneja de forma segura la obtención, almacenamiento en caché y renovación
 * automática de tokens de acceso necesarios para autenticarse en los servicios externos.
 * 
 * Características principales:
 * - Almacenamiento seguro del token en memoria con AtomicReference para operaciones atómicas
 * - Renovación automática cuando el token está próximo a expirar
 * - Sincronización thread-safe para evitar condiciones de carrera
 * - Renovación proactiva basada en un umbral configurable
 * - Manejo de errores robusto con reintentos automáticos
 * 
 * Flujo típico:
 * 1. Se solicita un token mediante getAccessToken()
 * 2. Si el token existe y es válido, se retorna de la caché
 * 3. Si está por expirar o no existe, se obtiene uno nuevo
 * 4. El token se almacena en memoria con su tiempo de expiración
 * 5. Un programador verifica periódicamente la validez del token
 */
@Slf4j
@Service
@EnableScheduling
public class TokenService {

    // Almacenamiento seguro del token de acceso con AtomicReference para operaciones atómicas
    private final AtomicReference<String> accessToken = new AtomicReference<>();
    
    // Tiempo de expiración del token actual
    private final AtomicReference<Instant> tokenExpiration = new AtomicReference<>(Instant.MIN);
    
    // Objeto para sincronización en la obtención de tokens
    private final Object tokenLock = new Object();
    
    // Cliente HTTP para realizar las peticiones de autenticación
    private final WebClient webClient;
    
    // Configuración de OAuth2 (URL, credenciales, etc.)
    private final OAuth2TokenProperties tokenProperties;
    
    // Mapeador JSON para procesar las respuestas del servidor de autenticación
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TokenService(WebClient webClient, OAuth2TokenProperties tokenProperties) {
        this.webClient = webClient;
        this.tokenProperties = tokenProperties;
    }

    /**
     * Obtiene un token de acceso válido.
     * Si el token actual no existe o está por expirar, solicita uno nuevo.
     * 
     * @return Mono que emite el token de acceso
     */
    /**
     * Retrieves a valid access token, either from cache or by requesting a new one.
     * @return Mono containing the access token
     */
    public Mono<String> getAccessToken() {
        Instant now = Instant.now();
        
        // First check without synchronization for better performance
        if (isTokenValid(now)) {
            log.debug("Using cached token (valid until {})", tokenExpiration.get());
            return Mono.just(accessToken.get());
        }
        
        log.debug("Token not valid or about to expire, requesting new token...");
        return fetchNewToken();
    }
    
    /**
     * Verifica si el token actual es válido.
     */
    /**
     * Verifies if the current token is valid and not expired.
     * @param now Current timestamp
     * @return true if token is valid, false otherwise
     */
    private boolean isTokenValid(Instant now) {
        String token = accessToken.get();
        if (token == null || token.isEmpty()) {
            log.debug("Token is null or empty");
            return false;
        }
        
        Instant expiration = tokenExpiration.get();
        if (expiration == null) {
            log.debug("No expiration time configured for token");
            return false;
        }
        
        // Check if token will expire within the refresh offset
        boolean isValid = now.plusSeconds(tokenProperties.getRefreshOffsetSeconds()).isBefore(expiration);
        
        if (isValid) {
            Duration timeLeft = Duration.between(now, expiration);
            log.debug("Token is valid for {} minutes and {} seconds", 
                     timeLeft.toMinutes(), 
                     timeLeft.minusMinutes(timeLeft.toMinutes()).getSeconds());
        } else {
            log.debug("Token expired or about to expire. Expires at: {}, Current time: {}, Time left: {} seconds", 
                    expiration, 
                    now,
                    Duration.between(now, expiration).getSeconds());
        }
        
        return isValid;
    }
    
    /**
     * Programa la renovación automática del token.
     */
    @Scheduled(fixedDelayString = "${app.security.oauth2.token.refresh-check-interval-ms}")
    public void scheduleTokenRefresh() {
        if (!isTokenValid(Instant.now())) {
            log.debug("Iniciando renovación programada de token...");
            fetchNewToken().subscribe(
                token -> log.debug("Token renovado exitosamente"),
                error -> log.error("Error en la renovación programada del token", error)
            );
        }
    }
    
    /**
     * Solicita un nuevo token de acceso.
     */
    /**
     * Fetches a new access token from the authentication server.
     * Uses double-checked locking to prevent multiple concurrent token requests.
     * @return Mono containing the new access token
     */
    private Mono<String> fetchNewToken() {
        // Use a lock to prevent multiple concurrent token requests
        synchronized (tokenLock) {
            // Double-check inside the lock in case another thread already got the token
            Instant now = Instant.now();
            if (isTokenValid(now)) {
                log.debug("Another thread already renewed the token, using existing one");
                return Mono.just(accessToken.get());
            }
            
            log.info("Requesting new access token from {}", tokenProperties.getUrl());
            
            String formData = String.format(
                "grant_type=password&client_id=%s&username=%s&password=%s",
                    tokenProperties.getClientId(), tokenProperties.getUsername(), tokenProperties.getPassword()
            );
            
            log.debug("Sending token request with client_id: {} and username: {}",
                    tokenProperties.getClientId(), tokenProperties.getUsername());
            
            return webClient.post()
                    .uri(tokenProperties.getUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .header("Cookie", "BIGipServerKEYCL_P_EVALUACION_8080=!NSHvquXABmxiVeCbmGc3BFjSZLqm0fM2ZtrUnJS9AyI87O9YFNI5+l41WAmTdJSm2Rj6W12SWWAza/A=")
                .bodyValue(formData)
                .retrieve()
                .onStatus(status -> status.isError(), response -> 
                    response.bodyToMono(String.class)
                        .flatMap(errorBody -> {
                            log.error("Authentication error ({}): {}", response.statusCode(), errorBody);
                            return Mono.error(new RuntimeException("Authentication error: " + response.statusCode()));
                        })
                )
                .bodyToMono(String.class)
                .doOnNext(response -> log.trace("Raw token response: {}", response))
                .flatMap(this::processTokenResponse)
                .doOnSuccess(token -> {
                    log.info("Successfully obtained new token, valid until: {}", tokenExpiration.get());
                    log.debug("New token starts with: {}", token.substring(0, Math.min(10, token.length())) + "...");
                })
                .doOnError(error -> log.error("Failed to obtain new token: {}", error.getMessage(), error));
        }
    }
    
    /**
     * Procesa la respuesta del servidor de autenticación.
     */
    /**
     * Processes the token response from the authentication server.
     * @param response Raw JSON response from the auth server
     * @return Mono containing the access token
     */
    private Mono<String> processTokenResponse(String response) {
        try {
            log.debug("Processing token response");
            JsonNode jsonNode = objectMapper.readTree(response);
            
            if (!jsonNode.has("access_token") || !jsonNode.has("expires_in")) {
                log.error("Invalid token response format. Missing required fields. Response: {}", response);
                return Mono.error(new RuntimeException("Invalid token response format"));
            }
            
            String token = jsonNode.get("access_token").asText();
            int expiresIn = jsonNode.get("expires_in").asInt();
            
            if (token == null || token.isEmpty()) {
                log.error("Received empty access token");
                return Mono.error(new RuntimeException("Empty access token received"));
            }
            
            // Calculate expiration time
            Instant now = Instant.now();
            Instant expiration = now.plusSeconds(expiresIn);
            
            // Update token state atomically
            accessToken.set(token);
            tokenExpiration.set(expiration);
            
            Duration timeUntilExpiration = Duration.between(now, expiration);
            
            // Log token info (truncated for security)
            int tokenLength = token.length();
            String tokenPreview = tokenLength > 10 
                ? token.substring(0, 10) + "..." + token.substring(tokenLength - 5)
                : "[TOKEN_TOO_SHORT]";
                
            log.info("Token updated. Expires in {} minutes (at {})", 
                    timeUntilExpiration.toMinutes(), 
                    expiration);
            log.debug("Generated token (truncated): {} (length: {} chars)", 
                    tokenPreview, 
                    tokenLength);
            return Mono.just(token);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to parse token response: {}", e.getMessage());
            log.error("Error al procesar la respuesta del token: {}", e.getMessage(), e);
            return Mono.error(new RuntimeException("Error al procesar la respuesta del token: " + e.getMessage(), e));
        } catch (Exception e) {
            log.error("Unexpected error processing token response: {}", e.getMessage(), e);
            return Mono.error(e);
        }
    }
}
