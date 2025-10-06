package factura.flow.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class KafkaMetricsInterceptor implements ProducerInterceptor<String, Object>, ConsumerInterceptor<String, Object> {

    private final Counter producedMessagesCounter;
    private final Counter consumedMessagesCounter;
    private final Timer produceLatencyTimer;
    private final Timer consumeLatencyTimer;

    public KafkaMetricsInterceptor(MeterRegistry meterRegistry) {
        this.producedMessagesCounter = Counter.builder("kafka.messages.produced")
            .description("Número de mensajes producidos")
            .register(meterRegistry);

        this.consumedMessagesCounter = Counter.builder("kafka.messages.consumed")
            .description("Número de mensajes consumidos")
            .register(meterRegistry);

        this.produceLatencyTimer = Timer.builder("kafka.produce.latency")
            .description("Tiempo de producción de mensajes")
            .register(meterRegistry);

        this.consumeLatencyTimer = Timer.builder("kafka.consume.latency")
            .description("Tiempo de procesamiento de mensajes")
            .register(meterRegistry);
    }

    @Override
    public ProducerRecord<String, Object> onSend(ProducerRecord<String, Object> record) {
        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        if (exception == null) {
            producedMessagesCounter.increment();
        }
    }

    @Override
    public ConsumerRecords<String, Object> onConsume(ConsumerRecords<String, Object> records) {
        if (!records.isEmpty()) {
            consumedMessagesCounter.increment(records.count());
        }
        return records;
    }

    @Override
    public void close() {
        // No es necesario hacer nada aquí
    }

    @Override
    public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
        // No es necesario hacer nada aquí
    }

    @Override
    public void configure(Map<String, ?> configs) {
        // No es necesario hacer nada aquí
    }
}
