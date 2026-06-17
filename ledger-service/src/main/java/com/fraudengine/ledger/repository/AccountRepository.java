package com.fraudengine.ledger.repository;

import java.util.Collection;
import java.util.List;

import com.fraudengine.ledger.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account
            from Account account
            where account.accountId in :accountIds
            order by account.accountId asc
            """)
    List<Account> findAllByIdForUpdate(@Param("accountIds") Collection<String> accountIds);
}
