package factura.flow.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class TracingConfig {

    @Bean
    public ProducerFactory<String, Object> tracingAwareProducerFactory(
            MeterRegistry meterRegistry) {
        
        Map<String, Object> configs = new HashMap<>();
        
        // Configuración básica del productor
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // Configuración de métricas
        configs.put("metrics.recording.level", "DEBUG");
        configs.put("metrics.sample.window.ms", 30000);
        
        // Asegurarse de que el productor tenga un ID de cliente único
        if (!configs.containsKey(ProducerConfig.CLIENT_ID_CONFIG)) {
            configs.put(ProducerConfig.CLIENT_ID_CONFIG, "producer-" + System.currentTimeMillis());
        }
        
        // Crear fábrica con métricas habilitadas
        DefaultKafkaProducerFactory<String, Object> factory = 
            new DefaultKafkaProducerFactory<>(configs);
            
        // Registrar métricas si el registro de métricas está disponible
        if (meterRegistry != null) {
            factory.addListener(new ProducerFactory.Listener<>() {
                @Override
                public void producerAdded(String id, org.apache.kafka.clients.producer.Producer<String, Object> producer) {
                    // Registrar métricas personalizadas aquí si es necesario
                }
            });
        }
        
        return factory;
    }
}
