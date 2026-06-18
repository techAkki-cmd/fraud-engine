package com.fraudengine.ai.eval;

import java.util.List;

import org.springframework.ai.document.Document;

public interface VectorSearchClient {

    List<Document> similarFraudProfiles(String query, int topK);

    void add(List<Document> documents);
}
