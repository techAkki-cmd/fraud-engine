package com.fraudengine.ledger.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.fraudengine.ledger.domain.Account;
import com.fraudengine.ledger.domain.FailureCode;
import com.fraudengine.ledger.domain.LedgerEntry;
import com.fraudengine.ledger.exception.BusinessRuleException;
import com.fraudengine.ledger.idempotency.PaymentClaim;
import com.fraudengine.ledger.idempotency.PaymentClaimService;
import com.fraudengine.ledger.messaging.PaymentEvent;
import com.fraudengine.ledger.messaging.PaymentMethod;
import com.fraudengine.ledger.repository.AccountRepository;
import com.fraudengine.ledger.repository.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionalLedgerServiceTests {

    private AccountRepository accountRepository;
    private LedgerEntryRepository ledgerEntryRepository;
    private PaymentClaimService claimService;
    private AuditContext auditContext;
    private TransactionalLedgerService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        ledgerEntryRepository = mock(LedgerEntryRepository.class);
        claimService = mock(PaymentClaimService.class);
        auditContext = mock(AuditContext.class);
        service = new TransactionalLedgerService(
                accountRepository,
                ledgerEntryRepository,
                claimService,
                auditContext,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void locksAccountsInLexicographicalOrderAndPostsTheTransfer() {
        PaymentEvent payment = payment("z-source", "a-destination", "10.00");
        PaymentClaim claim = PaymentClaim.acquired(payment.paymentId(), UUID.randomUUID());
        Account source = new Account("z-source", new BigDecimal("100.00"), "USD");
        Account destination = new Account("a-destination", new BigDecimal("25.00"), "USD");
        when(accountRepository.findAllByIdForUpdate(List.of("a-destination", "z-source")))
                .thenReturn(List.of(destination, source));

        service.processPayment(payment, "correlation-1", claim);

        assertThat(source.getBalance()).isEqualByComparingTo("90.00");
        assertThat(destination.getBalance()).isEqualByComparingTo("35.00");
        verify(auditContext).paymentMutation(payment.paymentId(), "ledger-service");
        verify(claimService).completeOwnedClaim(claim);
        ArgumentCaptor<LedgerEntry> entryCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getPaymentId()).isEqualTo(payment.paymentId());
    }

    @Test
    void rejectsInsufficientFundsBeforeMutatingEitherAccount() {
        PaymentEvent payment = payment("source", "destination", "10.00");
        PaymentClaim claim = PaymentClaim.acquired(payment.paymentId(), UUID.randomUUID());
        Account source = new Account("source", new BigDecimal("5.00"), "USD");
        Account destination = new Account("destination", new BigDecimal("25.00"), "USD");
        when(accountRepository.findAllByIdForUpdate(List.of("destination", "source")))
                .thenReturn(List.of(destination, source));

        assertThatThrownBy(() -> service.processPayment(payment, "correlation-2", claim))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(error -> ((BusinessRuleException) error).getFailureCode())
                .isEqualTo(FailureCode.INSUFFICIENT_FUNDS);

        assertThat(source.getBalance()).isEqualByComparingTo("5.00");
        assertThat(destination.getBalance()).isEqualByComparingTo("25.00");
        verify(ledgerEntryRepository, never()).save(any());
        verify(claimService, never()).completeOwnedClaim(claim);
    }

    @Test
    void rejectsCurrencyMismatches() {
        PaymentEvent payment = payment("source", "destination", "10.00");
        PaymentClaim claim = PaymentClaim.acquired(payment.paymentId(), UUID.randomUUID());
        Account source = new Account("source", new BigDecimal("100.00"), "USD");
        Account destination = new Account("destination", new BigDecimal("25.00"), "EUR");
        when(accountRepository.findAllByIdForUpdate(List.of("destination", "source")))
                .thenReturn(List.of(destination, source));

        assertThatThrownBy(() -> service.processPayment(payment, "correlation-3", claim))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(error -> ((BusinessRuleException) error).getFailureCode())
                .isEqualTo(FailureCode.CURRENCY_MISMATCH);
    }

    @Test
    void usesBankersRoundingAtTwoDecimalPlaces() {
        assertThat(MoneyPolicy.normalize(new BigDecimal("10.005")))
                .isEqualByComparingTo("10.00");
        assertThat(MoneyPolicy.normalize(new BigDecimal("10.015")))
                .isEqualByComparingTo("10.02");
    }

    private static PaymentEvent payment(String source, String destination, String amount) {
        return new PaymentEvent(
                UUID.randomUUID(),
                source,
                destination,
                "merchant-1",
                new BigDecimal(amount),
                "USD",
                PaymentMethod.BANK_TRANSFER,
                Instant.parse("2025-12-31T23:59:00Z"),
                null);
    }
}
