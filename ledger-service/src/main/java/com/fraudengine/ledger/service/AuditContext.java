package com.fraudengine.ledger.service;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

@Component
public class AuditContext {

    @PersistenceContext
    private EntityManager entityManager;

    public void paymentMutation(UUID paymentId, String actor) {
        setLocal("app.audit_payment_id", paymentId.toString());
        setLocal("app.audit_actor", actor);
    }

    private void setLocal(String name, String value) {
        entityManager.createNativeQuery("select set_config(:name, :value, true)")
                .setParameter("name", name)
                .setParameter("value", value)
                .getSingleResult();
    }
}
