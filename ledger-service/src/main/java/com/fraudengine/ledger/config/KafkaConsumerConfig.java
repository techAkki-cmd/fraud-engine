package com.fraudengine.ledger.config;

import java.time.Duration;

import com.fraudengine.ledger.messaging.PaymentTopics;
import org.springframework.kafka.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.apache.kafka.clients.admin.NewTopic;

@Configuration(proxyBeanMethods = false)
public class KafkaConsumerConfig {

    @Bean
    NewTopic paymentClearedTopic() {
        return TopicBuilder.name(PaymentTopics.PAYMENT_CLEARED)
                .partitions(12)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic paymentClearedDeadLetterTopic() {
        return TopicBuilder.name(PaymentTopics.PAYMENT_CLEARED_DLT)
                .partitions(12)
                .replicas(1)
                .build();
    }

    @Bean
    DefaultErrorHandler paymentConsumerErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        PaymentTopics.PAYMENT_CLEARED_DLT,
                        record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(Duration.ofSeconds(10));
        recoverer.setAppendOriginalHeaders(true);

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(4);
        backOff.setInitialInterval(500L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(5_000L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.setCommitRecovered(true);
        errorHandler.setAckAfterHandle(true);
        errorHandler.setLogLevel(KafkaException.Level.WARN);
        return errorHandler;
    }
}
