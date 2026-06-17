package com.fraudengine.ledger.domain;

public enum FailureCode {
    INSUFFICIENT_FUNDS,
    SOURCE_ACCOUNT_NOT_FOUND,
    DESTINATION_ACCOUNT_NOT_FOUND,
    SAME_ACCOUNT_TRANSFER,
    CURRENCY_MISMATCH
}
