package com.fraudengine.ai.eval;

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.seed-mock-data", havingValue = "true")
public class FraudProfileSeeder implements ApplicationRunner {

    private final VectorSearchClient vectorSearchClient;

    public FraudProfileSeeder(VectorSearchClient vectorSearchClient) {
        this.vectorSearchClient = vectorSearchClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        vectorSearchClient.add(List.of(
                new Document("Rapid successive transfers from a newly created digital wallet to unrelated merchants",
                        Map.of("scenario", "digital_wallet_velocity", "severity", "high", "expected_decision", "FRAUD")),
                new Document("Large first-time cross-account transfer after a dormant account suddenly becomes active",
                        Map.of("scenario", "dormant_account_takeover", "severity", "high", "expected_decision", "FRAUD")),
                new Document("Multiple failed card attempts followed by a successful high-value transfer",
                        Map.of("scenario", "credential_stuffing", "severity", "high", "expected_decision", "FRAUD")),
                new Document("ACH transfer to a known payroll beneficiary from a long-tenured account",
                        Map.of("scenario", "known_beneficiary", "severity", "low", "expected_decision", "SAFE")),
                new Document("Small recurring merchant payment consistent with prior account behavior",
                        Map.of("scenario", "recurring_payment", "severity", "low", "expected_decision", "SAFE"))));
    }
}
