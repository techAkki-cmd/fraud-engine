package com.fraudengine.ai.eval;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!mock-ai")
public class PgVectorSearchClient implements VectorSearchClient {

    private final VectorStore vectorStore;

    public PgVectorSearchClient(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public List<Document> similarFraudProfiles(String query, int topK) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build());
    }

    @Override
    public void add(List<Document> documents) {
        vectorStore.add(documents);
    }
}
