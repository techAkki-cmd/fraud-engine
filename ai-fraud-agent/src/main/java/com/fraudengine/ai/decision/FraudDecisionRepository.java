package com.fraudengine.ai.decision;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fraudengine.ai.support.RetryableFraudEvaluationException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class FraudDecisionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final Duration processingLease;

    public FraudDecisionRepository(
            JdbcTemplate jdbcTemplate,
            Clock clock,
            @Value("${app.ai.processing-lease:2m}") Duration processingLease) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.processingLease = processingLease;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DecisionClaim claim(UUID paymentId, String correlationId, ConsumerRecord<String, String> record) {
        Instant now = clock.instant();
        int inserted = jdbcTemplate.update("""
                insert into fraud_agent.fraud_decisions
                    (payment_id, status, correlation_id, source_topic, source_partition,
                     source_offset, created_at, updated_at)
                values (?, 'PROCESSING', ?, ?, ?, ?, ?, ?)
                on conflict (payment_id) do nothing
                """,
                paymentId,
                correlationId,
                record.topic(),
                record.partition(),
                record.offset(),
                Timestamp.from(now),
                Timestamp.from(now));
        if (inserted == 1) {
            return DecisionClaim.acquired(paymentId);
        }

        FraudDecisionRow row = findRow(paymentId)
                .orElseThrow(() -> new IllegalStateException("Decision row disappeared for " + paymentId));
        if (row.status() == FraudDecisionStatus.COMPLETED) {
            return DecisionClaim.completed(paymentId, row.toResult());
        }
        Instant staleBefore = now.minus(processingLease);
        if (row.updatedAt().isBefore(staleBefore)) {
            int reclaimed = jdbcTemplate.update("""
                    update fraud_agent.fraud_decisions
                    set correlation_id = ?,
                        source_topic = ?,
                        source_partition = ?,
                        source_offset = ?,
                        updated_at = ?
                    where payment_id = ?
                      and status = 'PROCESSING'
                      and updated_at = ?
                    """,
                    correlationId,
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    Timestamp.from(now),
                    paymentId,
                    Timestamp.from(row.updatedAt()));
            if (reclaimed == 1) {
                return DecisionClaim.acquired(paymentId);
            }
        }
        throw new RetryableFraudEvaluationException(
                "Payment %s is already being evaluated by another fraud agent".formatted(paymentId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID paymentId, FraudEvaluationResult result) {
        int updated = jdbcTemplate.update("""
                update fraud_agent.fraud_decisions
                set status = 'COMPLETED',
                    decision = ?,
                    risk_score = ?,
                    rules_triggered = ?,
                    reasoning = ?,
                    model = ?,
                    vector_matches = ?,
                    updated_at = ?
                where payment_id = ?
                  and status = 'PROCESSING'
                """,
                result.decision().name(),
                result.riskScore(),
                String.join("\n", result.rulesTriggered()),
                result.reasoning(),
                result.model(),
                result.vectorMatches(),
                Timestamp.from(result.evaluatedAt()),
                paymentId);
        if (updated != 1) {
            throw new RetryableFraudEvaluationException("Lost fraud decision claim for payment " + paymentId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseProcessing(UUID paymentId) {
        jdbcTemplate.update("""
                delete from fraud_agent.fraud_decisions
                where payment_id = ?
                  and status = 'PROCESSING'
                """, paymentId);
    }

    private Optional<FraudDecisionRow> findRow(UUID paymentId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    select payment_id, status, decision, risk_score, rules_triggered, reasoning, model, vector_matches, updated_at
                    from fraud_agent.fraud_decisions
                    where payment_id = ?
                    """, this::mapRow, paymentId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private FraudDecisionRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        String decision = rs.getString("decision");
        return new FraudDecisionRow(
                rs.getObject("payment_id", UUID.class),
                FraudDecisionStatus.valueOf(rs.getString("status")),
                decision == null ? null : FraudDecision.valueOf(decision),
                rs.getObject("risk_score", Integer.class),
                rs.getString("rules_triggered"),
                rs.getString("reasoning"),
                rs.getString("model"),
                rs.getString("vector_matches"),
                rs.getTimestamp("updated_at").toInstant());
    }

    private record FraudDecisionRow(
            UUID paymentId,
            FraudDecisionStatus status,
            FraudDecision decision,
            Integer riskScore,
            String rulesTriggered,
            String reasoning,
            String model,
            String vectorMatches,
            Instant updatedAt) {

        FraudEvaluationResult toResult() {
            return new FraudEvaluationResult(
                    decision,
                    riskScore == null ? 0 : riskScore,
                    rulesTriggered == null || rulesTriggered.isBlank()
                            ? java.util.List.of()
                            : java.util.Arrays.stream(rulesTriggered.split("\\R")).toList(),
                    reasoning,
                    model,
                    vectorMatches,
                    updatedAt);
        }
    }
}
