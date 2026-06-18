package com.fraudengine.ai.eval;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("mock-ai")
public class MockFraudAiClient implements FraudAiClient {

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        String normalized = userPrompt.toLowerCase();
        if (normalized.contains("paymentmethod=digital_wallet")
                || normalized.matches("(?s).*amount=[A-Z]{3}\\s+(9[0-9]{3,}|[1-9][0-9]{4,})\\.[0-9]{2}.*")) {
            return """
                    {"decision":"FRAUD","reasoning":"Mock policy flagged high-risk digital wallet or fraud-like transaction context."}
                    """;
        }
        return """
                {"decision":"SAFE","reasoning":"Mock policy found no high-risk fraud indicators in the payment context."}
                """;
    }
}
