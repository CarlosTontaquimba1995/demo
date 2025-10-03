package com.example.demo.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String TOKEN_URL = "https://sso-desa.funcionjudicial.gob.ec/realms/notarial/protocol/openid-connect/token";
    private static final String CLIENT_ID = "cj-web-app";
    private static final String USERNAME = "evaluaciones-funcion-judicial";
    private static final String PASSWORD = "Pesnot2024.";
    
    private final WebClient webClient;
    private final AtomicReference<String> accessToken = new AtomicReference<>();
    private Instant tokenExpiration = Instant.MIN;

    public synchronized Mono<String> getAccessToken() {
        if (accessToken.get() == null || Instant.now().isAfter(tokenExpiration)) {
            return fetchNewToken();
        }
        return Mono.just(accessToken.get());
    }
    
    private Mono<String> fetchNewToken() {
        log.info("Fetching new access token");
        log.info("Requesting new token from: {}", TOKEN_URL);
        
        // Build form data with proper encoding
        String formData = String.format(
            "grant_type=password&client_id=%s&username=%s&password=%s",
            CLIENT_ID,
            USERNAME,
            PASSWORD
        );
        
        return webClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .header("Cookie", "BIGipServerKEYCL_P_EVALUACION_8080=!NSHvquXABmxiVeCbmGc3BFjSZLqm0fM2ZtrUnJS9AyI87O9YFNI5+l41WAmTdJSm2Rj6W12SWWAza/A=")
                .bodyValue(formData)
                .retrieve()
                .onStatus(status -> status.isError(), response -> {
                    return response.bodyToMono(String.class)
                        .flatMap(errorBody -> {
                            log.error("Error response from auth server ({}): {}", response.statusCode(), errorBody);
                            return Mono.error(new RuntimeException("Failed to obtain token: " + response.statusCode() + " - " + errorBody));
                        });
                })
                .bodyToMono(String.class)
                .doOnNext(response -> log.debug("Token response: {}", response))
                .flatMap(response -> {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode jsonNode = mapper.readTree(response);
                        String token = jsonNode.get("access_token").asText();
                        long expiresIn = jsonNode.get("expires_in").asLong();
                        
                        accessToken.set(token);
                        // Set token expiration to 90% of actual TTL
                        long adjustedExpiresIn = (long) (expiresIn * 0.9);
                        tokenExpiration = Instant.now().plusSeconds(adjustedExpiresIn);
                        log.info("Token obtained, expires in {} seconds", adjustedExpiresIn);
                        return Mono.just(token);
                    } catch (Exception e) {
                        log.error("Error parsing token response: {}", e.getMessage(), e);
                        return Mono.error(new RuntimeException("Failed to parse token response", e));
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error obtaining access token: {}", e.getMessage());
                    return Mono.error(new RuntimeException("Failed to obtain access token: " + e.getMessage()));
                });
    }
    
}
