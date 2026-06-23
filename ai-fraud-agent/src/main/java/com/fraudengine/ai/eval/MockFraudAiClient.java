package com.fraudengine.ai.eval;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("mock-ai")
public class MockFraudAiClient implements FraudAiClient {

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        String normalized = userPrompt.toLowerCase();
        if (normalized.contains("decision=block")) {
            return """
                    {"reasoning":"Rules-based blocking was triggered by stacked high-risk signals such as UPI velocity, mule-account destination risk, or unusual INR payment context."}
                    """;
        }
        if (normalized.contains("decision=review")) {
            return """
                    {"reasoning":"Rules-based review was triggered by moderate India-market risk signals that require step-up verification before ledger posting."}
                    """;
        }
        return """
                {"reasoning":"Rules-based scoring found only familiar low-risk payment indicators, so the INR payment can proceed to ledger posting."}
                """;
    }
}
