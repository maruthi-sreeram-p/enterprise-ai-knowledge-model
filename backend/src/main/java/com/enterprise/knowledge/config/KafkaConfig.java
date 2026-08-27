package com.enterprise.knowledge.config;

import com.enterprise.knowledge.ingestion.DocumentUploadedEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka wiring for asynchronous document ingestion.
 *
 * Everything here is conditional on {@code app.ingestion.mode=KAFKA}, so a
 * developer running the default in-process mode needs no broker at all.
 */
@Configuration
@ConditionalOnProperty(name = "app.ingestion.mode", havingValue = "KAFKA")
public class KafkaConfig {

    private final KafkaProperties kafkaProperties;

    public KafkaConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    /**
     * Three partitions so that ingestion can be scaled to three consumers later
     * without repartitioning. One replica, because the development compose file
     * runs a single broker; a real deployment would raise this.
     */
    @Bean
    public NewTopic documentUploadedTopic(AppProperties properties) {
        return TopicBuilder.name(properties.getIngestion().getTopic())
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public ProducerFactory<String, DocumentUploadedEvent> ingestionProducerFactory() {
        Map<String, Object> config = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // The consumer is told the type explicitly below, so the type header adds
        // nothing and only couples the message to this application's class names.
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, DocumentUploadedEvent> kafkaTemplate(
            ProducerFactory<String, DocumentUploadedEvent> ingestionProducerFactory) {
        return new KafkaTemplate<>(ingestionProducerFactory);
    }

    @Bean
    public ConsumerFactory<String, DocumentUploadedEvent> ingestionConsumerFactory() {
        Map<String, Object> config = new HashMap<>(kafkaProperties.buildConsumerProperties(null));

        // Wrapping the value deserializer means a malformed message surfaces as a
        // handled error rather than killing the listener container in a loop.
        JsonDeserializer<DocumentUploadedEvent> valueDeserializer =
                new JsonDeserializer<>(DocumentUploadedEvent.class, false);
        valueDeserializer.addTrustedPackages("com.enterprise.knowledge.ingestion");

        return new DefaultKafkaConsumerFactory<>(
                config,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                new ErrorHandlingDeserializer<>(valueDeserializer));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DocumentUploadedEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, DocumentUploadedEvent> ingestionConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, DocumentUploadedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(ingestionConsumerFactory);
        // Matches the partition count so each partition gets its own consumer
        // thread within a single instance.
        factory.setConcurrency(3);
        return factory;
    }
}
