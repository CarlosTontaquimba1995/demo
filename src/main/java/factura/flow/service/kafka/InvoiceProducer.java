package factura.flow.service.kafka;

import factura.flow.config.KafkaConfig;
import factura.flow.dto.PendingInvoiceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Productor de Kafka para el envío de facturas a procesar.
 * Incluye reintentos automáticos y manejo de errores con DLQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceProducer {

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    private static final long SEND_TIMEOUT_MS = 5000; // 5 segundos

    private final KafkaTemplate<String, PendingInvoiceDto> kafkaTemplate;
    private final KafkaTemplate<String, Object> dlqKafkaTemplate;

    /**
     * Envía una factura para su procesamiento con reintentos automáticos.
     * @param invoice La factura a procesar
     */
    @Async
    @Retryable(
        value = { KafkaException.class, TimeoutException.class },
        maxAttempts = MAX_RETRIES,
        backoff = @Backoff(delay = RETRY_DELAY_MS)
    )
    public void sendInvoiceForProcessing(PendingInvoiceDto invoice) {
        String key = String.valueOf(invoice.getIdSolicitudActos());
        
        try {
            log.debug("🚀 Enviando factura {} a Kafka (intento actual: {}/{})", 
                    key, getCurrentRetryCount() + 1, MAX_RETRIES);
            
            // Envía el mensaje con timeout
            CompletableFuture<SendResult<String, PendingInvoiceDto>> future = 
                kafkaTemplate.send(KafkaConfig.PROCESSING_TOPIC, key, invoice);
            
            // Espera síncrona con timeout
            SendResult<String, PendingInvoiceDto> result = future.get(SEND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            
            log.info("✅ Factura {} enviada exitosamente. Partición: {}, Offset: {}", 
                    key, 
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
                    
        } catch (Exception ex) {
            log.error("❌ Error al enviar factura {} (intentos agotados): {}", 
                    key, ex.getMessage());
            throw new KafkaException("Error al enviar factura a Kafka", ex);
        }
    }
    
    /**
     * Método de recuperación cuando se agotan los reintentos.
     * Envía el mensaje a la cola de mensajes fallidos (DLQ).
     */
    @Recover
    public void recover(KafkaException ex, PendingInvoiceDto invoice) {
        String key = String.valueOf(invoice.getIdSolicitudActos());
        log.warn("⚠️ Enviando factura fallida {} a DLQ: {}", key, ex.getMessage());
        
        // Envía a la cola de mensajes fallidos
        try {
            dlqKafkaTemplate.send(KafkaConfig.DLQ_TOPIC, key, invoice)
                .whenComplete((result, throwable) -> {
                    if (throwable == null) {
                        log.info("✅ Factura fallida {} enviada a DLQ. Offset: {}", 
                                key, result.getRecordMetadata().offset());
                    } else {
                        log.error("❌ Error al enviar factura {} a DLQ: {}", key, throwable.getMessage());
                    }
                });
        } catch (Exception e) {
            log.error("❌ Error crítico al enviar factura {} a DLQ: {}", key, e.getMessage());
        }
    }
    
    /**
     * Obtiene el número de reintentos actual del contexto de reintentos.
     * @return Número de reintentos (0 para el primer intento)
     */
    private int getCurrentRetryCount() {
        // En una implementación real, podrías usar un contador de reintentos
        // Aquí devolvemos 0 como valor por defecto
        return 0;
    }
}