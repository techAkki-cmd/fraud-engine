package com.fraudengine.ai.messaging;

import com.fraudengine.ai.decision.DecisionClaim;
import com.fraudengine.ai.decision.FraudDecisionRepository;
import com.fraudengine.ai.decision.FraudEvaluationResult;
import com.fraudengine.ai.eval.FraudEvaluationService;
import com.fraudengine.ai.payment.DecodedPayment;
import com.fraudengine.ai.payment.PaymentEventDecoder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class FraudAgentConsumer {

    private static final Logger log = LoggerFactory.getLogger(FraudAgentConsumer.class);

    private final PaymentEventDecoder decoder;
    private final FraudDecisionRepository decisionRepository;
    private final FraudEvaluationService evaluationService;
    private final FraudDecisionPublisher publisher;

    public FraudAgentConsumer(
            PaymentEventDecoder decoder,
            FraudDecisionRepository decisionRepository,
            FraudEvaluationService evaluationService,
            FraudDecisionPublisher publisher) {
        this.decoder = decoder;
        this.decisionRepository = decisionRepository;
        this.evaluationService = evaluationService;
        this.publisher = publisher;
    }

    @KafkaListener(
            topics = PaymentTopics.PAYMENT_INGEST,
            groupId = "${spring.kafka.consumer.group-id}")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        DecodedPayment decoded = decoder.decode(record);
        DecisionClaim claim = decisionRepository.claim(decoded.payment().paymentId(), decoded.correlationId(), record);

        if (claim.completed()) {
            publisher.publish(record, decoded, claim.result());
            acknowledgment.acknowledge();
            log.info(
                    "Re-published stored fraud decision paymentId={} decision={} sourceOffset={}",
                    decoded.payment().paymentId(),
                    claim.result().decision(),
                    record.offset());
            return;
        }

        try {
            FraudEvaluationResult result = evaluationService.evaluate(decoded.payment());
            decisionRepository.complete(decoded.payment().paymentId(), result);
            publisher.publish(record, decoded, result);
            acknowledgment.acknowledge();
            log.info(
                    "Published fraud decision paymentId={} decision={} sourceOffset={}",
                    decoded.payment().paymentId(),
                    result.decision(),
                    record.offset());
        } catch (RuntimeException exception) {
            decisionRepository.releaseProcessing(decoded.payment().paymentId());
            throw exception;
        }
    }
}
