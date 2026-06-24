package com.fraudengine.gateway.config;

import java.util.Map;

import com.fraudengine.gateway.messaging.PaymentTopics;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.apache.kafka.clients.admin.NewTopic;
import reactor.kafka.sender.SenderOptions;

@Configuration
public class KafkaProducerConfig {

    @Bean
    ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate(
            KafkaProperties kafkaProperties) {
        Map<String, Object> properties = kafkaProperties.buildProducerProperties(null);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        SenderOptions<String, String> senderOptions = SenderOptions
                .<String, String>create(properties)
                .maxInFlight(5)
                .stopOnError(true);
        return new ReactiveKafkaProducerTemplate<>(senderOptions);
    }

    @Bean
    NewTopic paymentIngestTopic() {
        return TopicBuilder.name(PaymentTopics.PAYMENT_INGEST)
                .partitions(12)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic paymentClearedTopic() {
        return TopicBuilder.name(PaymentTopics.PAYMENT_CLEARED)
                .partitions(12)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic paymentReviewTopic() {
        return TopicBuilder.name(PaymentTopics.PAYMENT_REVIEW)
                .partitions(12)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic paymentBlockedTopic() {
        return TopicBuilder.name(PaymentTopics.PAYMENT_BLOCKED)
                .partitions(12)
                .replicas(1)
                .build();
    }
}
