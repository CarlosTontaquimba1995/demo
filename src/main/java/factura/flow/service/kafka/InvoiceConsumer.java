package factura.flow.service.kafka;

import factura.flow.client.ExternalApiClient;
import factura.flow.config.KafkaConfig;
import factura.flow.dto.PendingInvoiceDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceConsumer {

    private final ExternalApiClient externalApiClient;
    private final KafkaTemplate<String, Object> dlqKafkaTemplate;

    @KafkaListener(
        topics = KafkaConfig.PROCESSING_TOPIC,
        groupId = KafkaConfig.GROUP_ID,
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retry(name = "externalApi", fallbackMethod = "handleFailure")
    @CircuitBreaker(name = "externalApi", fallbackMethod = "handleFailure")
    public void consume(
        @Payload PendingInvoiceDto invoice,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(KafkaHeaders.OFFSET) long offset,
        Acknowledgment ack
    ) {
        try {
            log.info("📥 Procesando factura ID: {} (offset: {})", 
                    invoice.getIdSolicitudActos(), offset);
            
            // Procesar la factura
            externalApiClient.processInvoice(invoice.getIdSolicitudActos())
                .doOnSuccess(v -> log.info("✅ Factura {} procesada exitosamente", 
                    invoice.getIdSolicitudActos()))
                .doOnError(e -> log.error("❌ Error al procesar factura {}: {}", 
                    invoice.getIdSolicitudActos(), e.getMessage()))
                .block(); // Bloqueamos para manejar el ACK correctamente
                
            // Confirmar el procesamiento del mensaje
            ack.acknowledge();
            log.debug("ACK enviado para factura ID: {}", invoice.getIdSolicitudActos());
            
        } catch (Exception e) {
            log.error("❌ Error crítico al procesar factura {} (offset: {}): {}", 
                    invoice.getIdSolicitudActos(), offset, e.getMessage(), e);
            throw e; // Se reintentará según la configuración de retry
        }
    }

    public void handleFailure(
        PendingInvoiceDto invoice,
        String topic,
        long offset,
        Acknowledgment ack,
        Exception e
    ) {
        log.warn("⚠️ Todos los reintentos fallaron para factura ID: {}. Enviando a DLQ", 
                invoice.getIdSolicitudActos());
        
        try {
            // Crear mensaje de error para DLQ
            Map<String, Object> dlqMessage = Map.of(
                "timestamp", Instant.now().toString(),
                "originalTopic", topic,
                "offset", offset,
                "payload", invoice,
                "error", e.getMessage(),
                "errorType", e.getClass().getName()
            );

            // Enviar a DLQ
            dlqKafkaTemplate.send(KafkaConfig.DLQ_TOPIC, 
                                String.valueOf(invoice.getIdSolicitudActos()), 
                                dlqMessage)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("✅ Mensaje enviado a DLQ para factura ID: {}", 
                            invoice.getIdSolicitudActos());
                    } else {
                        log.error("❌ Error al enviar a DLQ: {}", ex.getMessage());
                    }
                });

            // Confirmar el mensaje original
            ack.acknowledge();
            log.info("✅ ACK enviado para factura fallida ID: {}", 
                    invoice.getIdSolicitudActos());
            
        } catch (Exception ex) {
            log.error("❌ Error crítico en el manejador de fallos para factura {}: {}", 
                    invoice.getIdSolicitudActos(), ex.getMessage(), ex);
            // No hacemos nada, el mensaje se reintentará
        }
    }
}