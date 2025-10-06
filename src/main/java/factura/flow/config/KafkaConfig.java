package factura.flow.config;

import factura.flow.dto.PendingInvoiceDto;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.lang.Nullable;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
@Configuration
@Slf4j
@RequiredArgsConstructor
public class KafkaConfig {
    
    public static final String PROCESSING_TOPIC = "factura.processing";
    public static final String DLQ_TOPIC = "factura.dlq";
    public static final String GROUP_ID = "factura-group";
    
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.concurrency:3}")
    private int concurrency;
    
    @Value("${kafka.retry.initial-interval:1000}")
    private long initialInterval;
    
    @Value("${kafka.retry.multiplier:2.0}")
    private double multiplier;
    
    @Value("${kafka.retry.max-attempts:3}")
    private int maxAttempts;
    
    @Value("${kafka.retry.max-interval:60000}")
    private long maxInterval;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        
        // Configuración de seguridad SSL
        if (System.getenv("KAFKA_SECURITY_PROTOCOL") != null && 
            System.getenv("KAFKA_SECURITY_PROTOCOL").equalsIgnoreCase("SSL")) {
            configs.put("security.protocol", "SSL");
            configs.put("ssl.truststore.location", System.getenv("KAFKA_SSL_TRUSTSTORE_LOCATION"));
            configs.put("ssl.truststore.password", System.getenv("KAFKA_SSL_TRUSTSTORE_PASSWORD"));
            configs.put("ssl.keystore.location", System.getenv("KAFKA_SSL_KEYSTORE_LOCATION"));
            configs.put("ssl.keystore.password", System.getenv("KAFKA_SSL_KEYSTORE_PASSWORD"));
            configs.put("ssl.key.password", System.getenv("KAFKA_SSL_KEY_PASSWORD"));
        }
        
        configs.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        configs.put(AdminClientConfig.METADATA_MAX_AGE_CONFIG, 300000);
        
        KafkaAdmin admin = new KafkaAdmin(configs);
        admin.setFatalIfBrokerNotAvailable(true);
        admin.setAutoCreate(false);
        
        return admin;
    }
    
    @Bean
    public NewTopic invoiceTopic() {
        return new NewTopic(PROCESSING_TOPIC, 3, (short) 3)  // 3 réplicas para producción
            .configs(Map.of(
                "retention.ms", "1209600000",  // 14 días
                "cleanup.policy", "delete",
                "delete.retention.ms", "86400000",  // 1 día
                "min.insync.replicas", "2"  // Mínimo de réplicas en sincronía
            ));
    }
    
    @Bean
    public NewTopic dlqTopic() {
        return new NewTopic(DLQ_TOPIC, 3, (short) 3)  // 3 réplicas para producción
            .configs(Map.of(
                "retention.ms", "2592000000",  // 30 días
                "cleanup.policy", "compact,delete",
                "delete.retention.ms", "2592000000",  // 30 días
                "min.compaction.lag.ms", "86400000",  // 1 día
                "min.insync.replicas", "2"  // Mínimo de réplicas en sincronía
            ));
    }
    
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        DefaultKafkaProducerFactory<String, Object> factory = new DefaultKafkaProducerFactory<>(producerConfigs());
        
        // Habilitar transacciones
        factory.setTransactionIdPrefix("tx-" + System.getenv("HOSTNAME") + "-");
        
        return factory;
    }
    
    @Bean
    public Map<String, Object> producerConfigs() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // Configuración de seguridad y fiabilidad
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        
        // Configuración de reintentos
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        
        // Configuración de rendimiento
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 100);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        
        // Timeouts
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        
        // Configuración de conexión
        configProps.put(ProducerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, 540000);
        
        // Deshabilitar headers de tipo para reducir el tamaño del mensaje
        configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        
        return configProps;
    }

    @Bean
    public ConsumerFactory<String, String> stringConsumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // Basic configuration
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID + "-health");
        configProps.put(ConsumerConfig.CLIENT_ID_CONFIG, GROUP_ID + "-health-client");
        
        // Deserializer configuration
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        
        // Session and timeout configuration
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 10000);
        configProps.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);
        
        // Auto-commit and offset reset
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        // Performance settings
        configProps.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        configProps.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);
        
        return new DefaultKafkaConsumerFactory<>(configProps);
    }
    
    @Bean
    @Primary
    public ConsumerFactory<String, PendingInvoiceDto> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // Configuración básica
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        configProps.put(ConsumerConfig.CLIENT_ID_CONFIG, GROUP_ID + "-client");
        
        // Configuración de deserializadores
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        
        // Configuración de seguridad y fiabilidad
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        configProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        
        // Configuración de sesión y tiempo de espera
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 45000);
        configProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 15000);
        configProps.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);
        configProps.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 50000);
        
        // Configuración de rendimiento
        configProps.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        configProps.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        
        // Configuración de reconexión
        configProps.put(ConsumerConfig.RECONNECT_BACKOFF_MS_CONFIG, 1000);
        configProps.put(ConsumerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, 10000);
        
        // Configuración de deserialización JSON
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "factura.flow.dto");
        configProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PendingInvoiceDto.class);
        
        return new DefaultKafkaConsumerFactory<>(
            configProps,
            new StringDeserializer(),
            new JsonDeserializer<>(PendingInvoiceDto.class, false)
        );
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            (record, exception) -> {
                log.error("Error al procesar mensaje: {} - {}", 
                    exception.getMessage(), 
                    record,
                    exception);
                // Aquí podrías agregar lógica adicional, como enviar a un tema de errores
            },
            new org.springframework.util.backoff.FixedBackOff(1000L, 3) // Reintentar 3 veces con 1 segundo de espera
        );
        
        // Configurar tipos de excepción que no deben reintentarse
        errorHandler.addNotRetryableExceptions(
            java.lang.IllegalArgumentException.class,
            org.springframework.kafka.support.serializer.DeserializationException.class
        );
        
        return errorHandler;
    }
    
    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // Política de reintentos con backoff exponencial
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(initialInterval);
        backOffPolicy.setMultiplier(multiplier);
        backOffPolicy.setMaxInterval(maxInterval);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        // Política de reintentos simple
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(maxAttempts);
        retryTemplate.setRetryPolicy(retryPolicy);
        
        return retryTemplate;
    }
    
    @Bean
    public CircuitBreaker kafkaCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .permittedNumberOfCallsInHalfOpenState(10)
            .slidingWindowSize(100)
            .minimumNumberOfCalls(10)
            .build();
            
        return CircuitBreaker.of("kafkaCircuitBreaker", config);
    }
    
    @Bean
    @Primary
    public ConcurrentKafkaListenerContainerFactory<String, PendingInvoiceDto> kafkaListenerContainerFactory(
            @Qualifier("consumerFactory") ConsumerFactory<String, PendingInvoiceDto> consumerFactory,
            @Qualifier("kafkaErrorHandler") CommonErrorHandler errorHandler) {
        
        ConcurrentKafkaListenerContainerFactory<String, PendingInvoiceDto> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
            
        // Configuración del contenedor
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(concurrency);
        factory.setBatchListener(false);
        
        // Configuración de reconocimiento de mensajes
        ContainerProperties containerProps = factory.getContainerProperties();
        containerProps.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        containerProps.setSyncCommits(true);
        
        // Configuración de manejo de errores
        factory.setCommonErrorHandler(errorHandler);
        
        return factory;
    }
    
    @Bean
    public HealthIndicator kafkaHealthIndicator(ConsumerFactory<String, String> consumerFactory) {
        return () -> {
            try (Consumer<String, String> consumer = consumerFactory.createConsumer()) {
                consumer.listTopics();
                return Health.up().withDetail("bootstrapServers", bootstrapServers).build();
            } catch (Exception e) {
                return Health.down(e).withDetail("bootstrapServers", bootstrapServers).build();
            }
        };
    }
    
    @Bean
    @Primary
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        KafkaTemplate<String, Object> kafkaTemplate = new KafkaTemplate<>(producerFactory);
        kafkaTemplate.setProducerListener(new ProducerListener<>() {
            @Override
            public void onSuccess(org.apache.kafka.clients.producer.ProducerRecord<String, Object> producerRecord, org.apache.kafka.clients.producer.RecordMetadata recordMetadata) {
                if (recordMetadata != null) {
                    log.debug("Mensaje enviado exitosamente - Tópico: {}, Partición: {}, Offset: {}",
                        recordMetadata.topic(), recordMetadata.partition(), recordMetadata.offset());
                }
            }
            
            @Override
            public void onError(org.apache.kafka.clients.producer.ProducerRecord<String, Object> producerRecord, 
                              @Nullable org.apache.kafka.clients.producer.RecordMetadata recordMetadata, 
                              Exception exception) {
                log.error("Error al enviar mensaje - Tópico: {}", producerRecord.topic(), exception);
            }
        });
        
        return kafkaTemplate;
    }

    @Bean
    public KafkaTemplate<String, Object> dlqKafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
    
    @Bean
    public KafkaTemplate<String, PendingInvoiceDto> pendingInvoiceKafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        // Create a producer factory specifically for PendingInvoiceDto
        DefaultKafkaProducerFactory<String, PendingInvoiceDto> pendingInvoiceProducerFactory = 
            new DefaultKafkaProducerFactory<>(producerFactory.getConfigurationProperties());
            
        KafkaTemplate<String, PendingInvoiceDto> kafkaTemplate = new KafkaTemplate<>(pendingInvoiceProducerFactory);
        kafkaTemplate.setProducerListener(new ProducerListener<>() {
            @Override
            public void onSuccess(org.apache.kafka.clients.producer.ProducerRecord<String, PendingInvoiceDto> producerRecord, 
                                org.apache.kafka.clients.producer.RecordMetadata recordMetadata) {
                if (recordMetadata != null) {
                    log.debug("Mensaje de factura pendiente enviado exitosamente - Tópico: {}, Partición: {}, Offset: {}",
                        recordMetadata.topic(), recordMetadata.partition(), recordMetadata.offset());
                }
            }
            
            @Override
            public void onError(org.apache.kafka.clients.producer.ProducerRecord<String, PendingInvoiceDto> producerRecord, 
                              @Nullable org.apache.kafka.clients.producer.RecordMetadata recordMetadata, 
                              Exception exception) {
                log.error("Error al enviar mensaje de factura pendiente - Tópico: {}", producerRecord.topic(), exception);
            }
        });
        return kafkaTemplate;
    }
}