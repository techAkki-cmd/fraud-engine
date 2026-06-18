package com.fraudengine.ai.eval;

public interface FraudAiClient {

    String complete(String systemPrompt, String userPrompt);
}
