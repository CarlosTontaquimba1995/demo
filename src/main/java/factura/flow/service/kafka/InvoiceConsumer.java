package factura.flow.service.kafka;

import factura.flow.client.ExternalApiClient;
import factura.flow.config.KafkaConfig;
import factura.flow.dto.PendingInvoiceDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.transaction.KafkaTransactionManager;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
public class InvoiceConsumer implements InitializingBean {

    private final ExternalApiClient externalApiClient;
    private final KafkaTemplate<String, Object> dlqKafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final TransactionTemplate transactionTemplate;

    public InvoiceConsumer(ExternalApiClient externalApiClient, 
                         KafkaTemplate<String, Object> dlqKafkaTemplate,
                         MeterRegistry meterRegistry,
                         KafkaTransactionManager<String, Object> kafkaTransactionManager) {
        this.externalApiClient = externalApiClient;
        this.dlqKafkaTemplate = dlqKafkaTemplate;
        this.meterRegistry = meterRegistry;
        
        // Configurar TransactionTemplate con el KafkaTransactionManager
        this.transactionTemplate = new TransactionTemplate(kafkaTransactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // Métricas personalizadas
    private static final String METRIC_PREFIX = "kafka.consumer.";
    private static final String PROCESSING_TIME_METRIC = METRIC_PREFIX + "processing.time";
    private static final String MESSAGES_PROCESSED_METRIC = METRIC_PREFIX + "messages.processed";
    private static final String MESSAGES_ERROR_METRIC = METRIC_PREFIX + "messages.error";
    private static final String MESSAGES_IGNORED_METRIC = METRIC_PREFIX + "messages.ignored";

    @Override
    public void afterPropertiesSet() {
        // Inicializar contadores en 0
        meterRegistry.counter(MESSAGES_PROCESSED_METRIC, "status", "total");
        meterRegistry.counter(MESSAGES_ERROR_METRIC, "status", "error");
        meterRegistry.counter(MESSAGES_IGNORED_METRIC, "status", "ignored");
    }

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
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment ack
    ) {
        long startTime = System.currentTimeMillis();
        String invoiceId = invoice != null ? String.valueOf(invoice.getIdSolicitudActos()) : "null";

        try {
            // 1. Validar mensaje
            if (invoice == null || invoice.getIdSolicitudActos() == null) {
                log.warn("⚠️ Mensaje inválido recibido (offset: {})", offset);
                meterRegistry.counter(MESSAGES_IGNORED_METRIC, "reason", "invalid").increment();
                ack.acknowledge();
                return;
            }

            // 2. Ignorar mensajes antiguos (más de 24 horas)
            long messageAge = System.currentTimeMillis() - timestamp;
            long maxAgeMs = 24 * 60 * 60 * 1000; // 24 horas en milisegundos
            if (messageAge > maxAgeMs) {
                log.warn("⏭️ Ignorando mensaje antiguo ({} horas de antigüedad): ID {} (offset: {})",
                        messageAge / 3600000, invoiceId, offset);
                meterRegistry.counter(MESSAGES_IGNORED_METRIC, "reason", "stale").increment();
                ack.acknowledge();
                return;
            }

            log.info("📥 Iniciando procesamiento - Factura ID: {} (offset: {})", invoiceId, offset);

            // 3. Procesar la factura con métricas de tiempo
            Timer.Sample timer = Timer.start(meterRegistry);
            try {
                externalApiClient.processInvoice(invoice.getIdSolicitudActos())
                        .doOnSuccess(v -> {
                            log.info("✅ Factura {} procesada exitosamente (offset: {})", invoiceId, offset);
                            meterRegistry.counter(MESSAGES_PROCESSED_METRIC, "status", "success").increment();
                        })
                        .doOnError(e -> {
                            log.error("❌ Error al procesar factura {} (offset: {}): {}",
                                    invoiceId, offset, e.getMessage());
                            meterRegistry.counter(MESSAGES_ERROR_METRIC, "type", "processing").increment();
                        })
                        .block();
            } finally {
                // Registrar tiempo de procesamiento
                timer.stop(meterRegistry.timer(PROCESSING_TIME_METRIC, "status", "processed"));
            }
                
            // 4. Confirmar procesamiento
            ack.acknowledge();
            log.debug("✅ ACK enviado para factura ID: {} (offset: {})", invoiceId, offset);
            
        } catch (Exception e) {
            meterRegistry.counter(MESSAGES_ERROR_METRIC, "type", "critical").increment();
            log.error("❌ Error crítico al procesar factura ID: {} (offset: {}): {}",
                    invoiceId, offset, e.getMessage(), e);
            throw e;
        } finally {
            // 5. Métricas y limpieza
            long processingTime = System.currentTimeMillis() - startTime;
            log.debug("⏱️ Tiempo de procesamiento para factura ID: {}: {} ms",
                    invoiceId, processingTime);
        }
    }

    @Transactional(transactionManager = "kafkaTransactionManager")
    public void handleFailure(
        @Payload PendingInvoiceDto invoice,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(KafkaHeaders.OFFSET) long offset,
        @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
        Acknowledgment ack,
        Throwable e
    ) {
        String invoiceId = String.valueOf(invoice.getIdSolicitudActos());
        log.warn("⚠️ Todos los reintentos fallaron para factura ID: {}. Enviando a DLQ", invoiceId);
        
        try {
            // Ejecutar en una transacción
            transactionTemplate.execute(status -> {
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

                    // Enviar a DLQ dentro de la transacción
                    dlqKafkaTemplate.send(KafkaConfig.DLQ_TOPIC, invoiceId, dlqMessage).get();
                    log.info("✅ Mensaje enviado a DLQ para factura ID: {}", invoiceId);
                    
                    // Confirmar el mensaje original
                    ack.acknowledge();
                    log.info("✅ ACK enviado para factura fallida ID: {}", invoiceId);
                    
                    return null;
                } catch (Exception ex) {
                    log.error("❌ Error al procesar mensaje fallido para factura {}: {}", 
                            invoiceId, ex.getMessage(), ex);
                    status.setRollbackOnly();
                    throw new RuntimeException("Error al procesar mensaje fallido", ex);
                }
            });
        } catch (Exception ex) {
            log.error("❌ Error crítico en el manejador de fallos para factura {}: {}", 
                    invoiceId, ex.getMessage(), ex);
            // El mensaje no se confirma y se reintentará según la política de reintentos
        }
    }
}