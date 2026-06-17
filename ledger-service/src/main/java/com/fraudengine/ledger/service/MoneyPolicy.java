package com.fraudengine.ledger.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.fraudengine.ledger.exception.ContractValidationException;

public final class MoneyPolicy {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;

    private MoneyPolicy() {
    }

    public static BigDecimal normalize(BigDecimal amount) {
        if (amount == null) {
            throw new ContractValidationException("Payment amount must not be null");
        }
        BigDecimal normalized = amount.setScale(SCALE, ROUNDING_MODE);
        if (normalized.signum() <= 0 || normalized.precision() > 19) {
            throw new ContractValidationException("Payment amount is outside NUMERIC(19,2) bounds");
        }
        return normalized;
    }
}
