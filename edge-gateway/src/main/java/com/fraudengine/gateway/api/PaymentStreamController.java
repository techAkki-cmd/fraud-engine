package com.fraudengine.gateway.api;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fraudengine.gateway.messaging.PaymentTopics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@RestController
public class PaymentStreamController {

    private static final Logger log = LoggerFactory.getLogger(PaymentStreamController.class);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);
    private static final int STREAM_BUFFER_SIZE = 1_024;
    private static final Set<String> REQUIRED_FIELDS = Set.of(
            "paymentId",
            "amount",
            "currency",
            "accountId",
            "merchantId",
            "destinationAccountId",
            "paymentMethod",
            "occurredAt");

    private final Sinks.Many<String> paymentSink;
    private final ObjectMapper objectMapper;

    @Autowired
    public PaymentStreamController(ObjectMapper objectMapper) {
        this(
                objectMapper,
                Sinks.many().replay().limit(STREAM_BUFFER_SIZE));
    }

    PaymentStreamController(ObjectMapper objectMapper, Sinks.Many<String> paymentSink) {
        this(objectMapper, paymentSink, HEARTBEAT_INTERVAL);
    }

    PaymentStreamController(ObjectMapper objectMapper, Sinks.Many<String> paymentSink, Duration heartbeatInterval) {
        this.objectMapper = objectMapper;
        this.paymentSink = paymentSink;
        this.heartbeatInterval = heartbeatInterval;
    }

    private final Duration heartbeatInterval;

    @GetMapping(path = "/api/v1/stream/payments", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamPayments() {
        Flux<ServerSentEvent<String>> payments = paymentSink.asFlux()
                .map(payload -> ServerSentEvent.<String>builder()
                        .event("payment-evaluation")
                        .id(eventId(payload))
                        .data(payload)
                        .build());

        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ZERO, heartbeatInterval)
                .map(tick -> ServerSentEvent.<String>builder()
                        .event("heartbeat")
                        .comment("keep-alive")
                        .data("{\"type\":\"heartbeat\"}")
                        .build());

        return Flux.merge(payments, heartbeat);
    }

    @KafkaListener(
            topics = { PaymentTopics.PAYMENT_CLEARED, PaymentTopics.PAYMENT_REVIEW, PaymentTopics.PAYMENT_BLOCKED },
            groupId = "${fraud-engine.stream.group-id:edge-gateway-stream-group}")
    public void consumeEvaluation(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            String normalizedPayload = normalize(record);
            Sinks.EmitResult result = paymentSink.tryEmitNext(normalizedPayload);
            if (result.isSuccess()) {
                acknowledgment.acknowledge();
                return;
            }

            if (result == Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
                log.debug(
                        "No SSE subscribers for payment evaluation topic={} partition={} offset={}; acknowledging",
                        record.topic(),
                        record.partition(),
                        record.offset());
                acknowledgment.acknowledge();
                return;
            }

            throw new IllegalStateException("Unable to emit payment evaluation to SSE sink: " + result);
        } catch (JsonProcessingException exception) {
            log.warn(
                    "Skipping malformed payment evaluation topic={} partition={} offset={}: {}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    exception.getOriginalMessage());
            acknowledgment.acknowledge();
        }
    }

    String normalize(ConsumerRecord<String, String> record) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(record.value());
        for (String requiredField : REQUIRED_FIELDS) {
            if (!hasText(root, requiredField)) {
                throw new JsonProcessingException("Missing required payment field: " + requiredField) {
                };
            }
        }

        String status = switch (record.topic()) {
            case PaymentTopics.PAYMENT_CLEARED -> "SAFE";
            case PaymentTopics.PAYMENT_REVIEW -> "REVIEW";
            case PaymentTopics.PAYMENT_BLOCKED -> "BLOCK";
            default -> throw new JsonProcessingException("Unsupported evaluated payment topic: " + record.topic()) {
            };
        };

        ObjectNode event = objectMapper.createObjectNode();
        event.put("paymentId", root.path("paymentId").asText());
        event.put("amount", root.path("amount").asText());
        event.put("currency", root.path("currency").asText());
        event.put("accountId", root.path("accountId").asText());
        event.put("merchantId", root.path("merchantId").asText());
        event.put("destinationAccountId", root.path("destinationAccountId").asText());
        event.put("paymentMethod", root.path("paymentMethod").asText());
        event.put("occurredAt", root.path("occurredAt").asText());
        event.put("status", status);
        event.put("aiReasoning", header(record, "fraud-reason", defaultReason(status)));
        event.put("correlationId", header(record, "correlation-id", root.path("paymentId").asText()));
        putOptionalHeader(event, record, "riskScore", "risk-score");
        putOptionalHeader(event, record, "rulesTriggered", "risk-rules");

        return objectMapper.writeValueAsString(event);
    }

    private String eventId(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (hasText(root, "paymentId")) {
                return root.path("paymentId").asText();
            }
            if (hasText(root, "correlationId")) {
                return root.path("correlationId").asText();
            }
        } catch (JsonProcessingException exception) {
            log.debug("Unable to derive SSE id from payload", exception);
        }
        return null;
    }

    private static boolean hasText(JsonNode root, String fieldName) {
        return root.hasNonNull(fieldName) && !root.path(fieldName).asText().isBlank();
    }

    private static String header(ConsumerRecord<String, String> record, String name, String fallback) {
        Header header = record.headers().lastHeader(name);
        if (header == null || header.value() == null) {
            return fallback;
        }
        String value = new String(header.value(), StandardCharsets.UTF_8).trim();
        return value.isBlank() ? fallback : value;
    }

    private static String defaultReason(String status) {
        if ("BLOCK".equals(status)) {
            return "Payment blocked by the hybrid risk engine.";
        }
        if ("REVIEW".equals(status)) {
            return "Payment requires step-up or manual review.";
        }
        return "Payment cleared by the hybrid risk engine.";
    }

    private static void putOptionalHeader(
            ObjectNode event,
            ConsumerRecord<String, String> record,
            String fieldName,
            String headerName) {
        Header header = record.headers().lastHeader(headerName);
        if (header == null || header.value() == null) {
            return;
        }
        String value = new String(header.value(), StandardCharsets.UTF_8).trim();
        if (value.isBlank()) {
            return;
        }
        if ("riskScore".equals(fieldName)) {
            try {
                event.put(fieldName, Integer.parseInt(value));
                return;
            } catch (NumberFormatException ignored) {
                event.put(fieldName, value);
                return;
            }
        }
        event.put(fieldName, value);
    }
}
