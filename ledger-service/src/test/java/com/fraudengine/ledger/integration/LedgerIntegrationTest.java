package com.fraudengine.ledger.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fraudengine.ledger.domain.Account;
import com.fraudengine.ledger.domain.AuditLogEntity;
import com.fraudengine.ledger.repository.AccountRepository;
import com.fraudengine.ledger.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.config.import=",
        "eureka.client.enabled=false",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.default_schema=ledger",
        "spring.flyway.enabled=true",
        "spring.flyway.create-schemas=true",
        "spring.flyway.default-schema=ledger",
        "spring.flyway.schemas=ledger",
        "spring.kafka.consumer.group-id=ledger-integration-test",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false"
})
class LedgerIntegrationTest {

    private static final int CONCURRENT_WRITERS = 10;
    private static final BigDecimal OPENING_BALANCE = new BigDecimal("100.00");
    private static final BigDecimal DEBIT_AMOUNT = new BigDecimal("1.00");
    private static final String ACCOUNT_ID = "acct-lock-target";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ledger_test")
            .withUsername("ledger_test")
            .withPassword("ledger_test");

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @AfterEach
    void cleanDatabase() {
        accountRepository.deleteAll();
        jdbcTemplate.execute("truncate table ledger.audit_logs restart identity");
    }

    @Test
    void concurrentBalanceUpdatesSerializeThroughPessimisticLock() throws Exception {
        accountRepository.saveAndFlush(new Account(ACCOUNT_ID, OPENING_BALANCE, "USD"));

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_WRITERS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_WRITERS);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Void>> updates = java.util.stream.IntStream.range(0, CONCURRENT_WRITERS)
                .mapToObj(index -> executor.submit((Callable<Void>) () -> {
                    ready.countDown();
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();

                    transactionTemplate.executeWithoutResult(status -> {
                        Account account = accountRepository.findAllByIdForUpdate(List.of(ACCOUNT_ID))
                                .stream()
                                .findFirst()
                                .orElseThrow();
                        account.debit(DEBIT_AMOUNT);
                    });
                    return null;
                }))
                .toList();

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        assertThatCode(() -> {
            for (Future<Void> update : updates) {
                update.get(20, TimeUnit.SECONDS);
            }
        }).doesNotThrowAnyException();

        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        BigDecimal expectedBalance = OPENING_BALANCE.subtract(DEBIT_AMOUNT.multiply(BigDecimal.valueOf(CONCURRENT_WRITERS)));
        Account account = accountRepository.findById(ACCOUNT_ID).orElseThrow();

        assertThat(account.getBalance()).isEqualByComparingTo(expectedBalance);
        assertThat(account.getVersion()).isEqualTo(CONCURRENT_WRITERS);
    }

    @Test
    void accountBalanceUpdatesCreateImmutableAuditRows() {
        UUID paymentId = UUID.randomUUID();
        accountRepository.saveAndFlush(new Account(ACCOUNT_ID, OPENING_BALANCE, "USD"));

        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.queryForObject(
                    "select set_config('app.audit_payment_id', ?, true)",
                    String.class,
                    paymentId.toString());
            jdbcTemplate.queryForObject(
                    "select set_config('app.audit_actor', ?, true)",
                    String.class,
                    "ledger-service");
            Account account = accountRepository.findAllByIdForUpdate(List.of(ACCOUNT_ID))
                    .stream()
                    .findFirst()
                    .orElseThrow();
            account.debit(DEBIT_AMOUNT);
        });

        List<AuditLogEntity> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).hasSize(1);
        AuditLogEntity auditLog = auditLogs.getFirst();
        assertThat(auditLog.getPaymentId()).isEqualTo(paymentId);
        assertThat(auditLog.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(auditLog.getAction()).isEqualTo("BALANCE_DEDUCTED");
        assertThat(auditLog.getActor()).isEqualTo("ledger-service");
        assertThat(auditLog.getTimestamp()).isNotNull();
        assertThat(auditLog.getBalanceBefore()).isEqualByComparingTo("100.00");
        assertThat(auditLog.getBalanceAfter()).isEqualByComparingTo("99.00");

        Long auditId = auditLog.getAuditId();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update ledger.audit_logs set actor = 'tamper' where audit_id = ?",
                auditId))
                .hasMessageContaining("ledger.audit_logs is immutable");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from ledger.audit_logs where audit_id = ?",
                auditId))
                .hasMessageContaining("ledger.audit_logs is immutable");
    }
}
