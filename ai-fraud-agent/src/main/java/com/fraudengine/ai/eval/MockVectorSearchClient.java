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
            new Document("Rapid successive transfers from a newly created digital wallet",
                    Map.of("scenario", "velocity", "severity", "high", "expected_decision", "FRAUD")),
            new Document("Unusual high-value transfer to a first-time merchant from a dormant account",
                    Map.of("scenario", "account_takeover", "severity", "high", "expected_decision", "FRAUD")),
            new Document("Normal recurring bill payment from established account to known merchant",
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
