package com.fraudengine.ledger.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fraudengine.ledger.domain.Account;
import com.fraudengine.ledger.domain.FailureCode;
import com.fraudengine.ledger.domain.LedgerEntry;
import com.fraudengine.ledger.exception.BusinessRuleException;
import com.fraudengine.ledger.idempotency.PaymentClaim;
import com.fraudengine.ledger.idempotency.PaymentClaimService;
import com.fraudengine.ledger.messaging.PaymentEvent;
import com.fraudengine.ledger.repository.AccountRepository;
import com.fraudengine.ledger.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionalLedgerService implements LedgerService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PaymentClaimService claimService;
    private final Clock clock;

    public TransactionalLedgerService(
            AccountRepository accountRepository,
            LedgerEntryRepository ledgerEntryRepository,
            PaymentClaimService claimService,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.claimService = claimService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void processPayment(PaymentEvent payment, String correlationId, PaymentClaim claim) {
        if (payment.accountId().equals(payment.destinationAccountId())) {
            throw businessFailure(
                    FailureCode.SAME_ACCOUNT_TRANSFER,
                    "Source and destination accounts must be different");
        }

        BigDecimal amount = MoneyPolicy.normalize(payment.amount());
        List<String> orderedAccountIds = List.of(payment.accountId(), payment.destinationAccountId())
                .stream()
                .sorted(Comparator.naturalOrder())
                .toList();

        Map<String, Account> accounts = accountRepository.findAllByIdForUpdate(orderedAccountIds)
                .stream()
                .collect(Collectors.toMap(Account::getAccountId, Function.identity()));

        Account source = accounts.get(payment.accountId());
        if (source == null) {
            throw businessFailure(
                    FailureCode.SOURCE_ACCOUNT_NOT_FOUND,
                    "Source account does not exist");
        }

        Account destination = accounts.get(payment.destinationAccountId());
        if (destination == null) {
            throw businessFailure(
                    FailureCode.DESTINATION_ACCOUNT_NOT_FOUND,
                    "Destination account does not exist");
        }

        if (!source.getCurrency().equals(payment.currency())
                || !destination.getCurrency().equals(payment.currency())) {
            throw businessFailure(
                    FailureCode.CURRENCY_MISMATCH,
                    "Payment currency must match both account currencies");
        }

        if (source.getBalance().compareTo(amount) < 0) {
            throw businessFailure(
                    FailureCode.INSUFFICIENT_FUNDS,
                    "Source account has insufficient funds");
        }

        source.debit(amount);
        destination.credit(amount);
        ledgerEntryRepository.save(LedgerEntry.success(
                payment.paymentId(),
                payment.accountId(),
                payment.destinationAccountId(),
                amount,
                payment.currency(),
                correlationId,
                clock.instant()));
        claimService.completeOwnedClaim(claim);
    }

    private static BusinessRuleException businessFailure(FailureCode code, String message) {
        return new BusinessRuleException(code, message);
    }
}
