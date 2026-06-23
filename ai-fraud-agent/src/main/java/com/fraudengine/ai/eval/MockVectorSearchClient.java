package com.fraudengine.ai.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.ai.document.Document;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("mock-ai")
public class MockVectorSearchClient implements VectorSearchClient {

    private final List<Document> documents = new CopyOnWriteArrayList<>(List.of(
            new Document("Rapid UPI transfers from a newly created wallet to unrelated beneficiaries",
                    Map.of("scenario", "velocity", "severity", "high", "expected_decision", "BLOCK")),
            new Document("Unusual high-value payout from a dormant salary account to a first-time mule account",
                    Map.of("scenario", "account_takeover", "severity", "high", "expected_decision", "BLOCK")),
            new Document("Normal UPI grocery payment from an established account to a known kirana merchant",
                    Map.of("scenario", "routine", "severity", "low", "expected_decision", "SAFE"))));

    @Override
    public List<Document> similarFraudProfiles(String query, int topK) {
        return documents.stream().limit(topK).toList();
    }

    @Override
    public void add(List<Document> documents) {
        this.documents.addAll(new ArrayList<>(documents));
    }
}
