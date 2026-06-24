package com.fraudengine.ai.config;

import java.time.Duration;

import com.fraudengine.ai.messaging.PaymentTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration(proxyBeanMethods = false)
public class KafkaConfig {

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

    @Bean
    NewTopic paymentIngestDeadLetterTopic() {
        return TopicBuilder.name(PaymentTopics.PAYMENT_INGEST_DLT)
                .partitions(12)
                .replicas(1)
                .build();
    }

    @Bean
    DefaultErrorHandler fraudAgentErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(PaymentTopics.PAYMENT_INGEST_DLT, record.partition()));
        recoverer.setAppendOriginalHeaders(true);
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(Duration.ofSeconds(10));

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(4);
        backOff.setInitialInterval(500L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(5_000L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.setAckAfterHandle(true);
        errorHandler.setCommitRecovered(true);
        errorHandler.setLogLevel(KafkaException.Level.WARN);
        return errorHandler;
    }
}
