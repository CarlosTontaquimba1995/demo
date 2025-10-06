package factura.flow.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Configuration
public class MetricsConfig {

    private final ConcurrentMap<String, KafkaClientMetrics> producerMetrics = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, KafkaClientMetrics> consumerMetrics = new ConcurrentHashMap<>();

    @Bean
    public ProducerFactory.Listener<String, Object> producerMetricsListener() {
        return new ProducerFactory.Listener<>() {
            @Override
            public void producerAdded(String id, Producer<String, Object> producer) {
                String key = "producer." + id;
                producerMetrics.computeIfAbsent(key,
                        k -> new KafkaClientMetrics(producer)).bindTo(registry);
            }

            @Override
            public void producerRemoved(String id, Producer<String, Object> producer) {
                String key = "producer." + id;
                KafkaClientMetrics metrics = producerMetrics.remove(key);
                if (metrics != null) {
                    metrics.close();
                }
            }
        };
    }

    @Bean
    public ConsumerFactory.Listener<String, Object> consumerMetricsListener() {
        return new ConsumerFactory.Listener<>() {
            @Override
            public void consumerAdded(String id, Consumer<String, Object> consumer) {
                String key = "consumer." + id;
                consumerMetrics.computeIfAbsent(key,
                        k -> new KafkaClientMetrics(consumer)).bindTo(registry);
            }

            @Override
            public void consumerRemoved(String id, Consumer<String, Object> consumer) {
                String key = "consumer." + id;
                KafkaClientMetrics metrics = consumerMetrics.remove(key);
                if (metrics != null) {
                    metrics.close();
                }
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings("unchecked")
    public MeterBinder customKafkaProducerMetrics(ProducerFactory<?, ?> producerFactory) {
        // Verificar si es una instancia de DefaultKafkaProducerFactory
        if (producerFactory instanceof DefaultKafkaProducerFactory) {
            // Configurar el listener de métricas con el tipo correcto
            DefaultKafkaProducerFactory<String, Object> typedFactory = (DefaultKafkaProducerFactory<String, Object>) producerFactory;
            typedFactory.addListener(producerMetricsListener());
        }

        // Retornar un MeterBinder vacío ya que las métricas se manejan en los listeners
        return registry -> {
        };
    }

    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public MeterBinder customKafkaConsumerMetrics(ConsumerFactory<?, ?> consumerFactory) {
        if (consumerFactory instanceof DefaultKafkaConsumerFactory) {
            DefaultKafkaConsumerFactory<?, ?> defaultFactory = (DefaultKafkaConsumerFactory<?, ?>) consumerFactory;
            ConsumerFactory.Listener listener = new ConsumerFactory.Listener() {
                @Override
                public void consumerAdded(String id, Consumer consumer) {
                    KafkaClientMetrics metrics = new KafkaClientMetrics(consumer);
                    metrics.bindTo(registry);
                    consumerMetrics.put(id, metrics);
                }

                @Override
                public void consumerRemoved(String id, Consumer consumer) {
                    KafkaClientMetrics metrics = consumerMetrics.remove(id);
                    if (metrics != null) {
                        metrics.close();
                    }
                }
            };
            defaultFactory.addListener(listener);
        }
        return registry -> {
        };
    }

    // Inyectar el registro de métricas
    private final MeterRegistry registry;

    public MetricsConfig(MeterRegistry registry) {
        this.registry = registry;
    }
}
