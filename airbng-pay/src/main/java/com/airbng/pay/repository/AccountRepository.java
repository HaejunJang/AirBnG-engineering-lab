package com.airbng.pay.repository;

import com.airbng.pay.domain.Account;
import com.airbng.pay.domain.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    boolean existsByWalletWalletIdAndAccountNumber(Long walletId, String accountNumber);
    boolean existsByWallet(Wallet wallet);
    List<Account> findAllByWalletWalletIdOrderByIsPrimaryDescAccountIdAsc(Long walletId);
    boolean existsByWalletWalletIdAndAccountId(Long walletId, Long accountId);
    Optional<Account> findFirstByWallet_WalletId(Long walletId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountId = :accountId and a.wallet.walletId = :walletId")
    Optional<Account> findForUpdate(@Param("accountId") Long accountId, @Param("walletId") Long walletId);

    /**
     * 충전시 계좌 잔액 + wallet 소속 원자적 처리
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Account a set a.balance = a.balance - :amount where a.accountId = :accountId and a.wallet.walletId = :walletId and a.balance >= :amount")
    int subtractIfEnough(@Param("accountId") Long accountId, @Param("walletId") Long walletId, @Param("amount") BigDecimal amount);

    /**
     * 출금시 계좌 잔액 증가
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Account a set a.balance = a.balance + :amount where a.accountId = :accountId and a.wallet.walletId = :walletId")
    int addBalance(@Param("accountId") Long accountId, @Param("walletId") Long walletId, @Param("amount") BigDecimal amount);

    @Lock(LockModeType.OPTIMISTIC)
    @Query("""
        update Account a
        set a.isPrimary = (a.accountId = :newPrimaryId)
        where a.wallet.walletId = :walletId
        and (a.accountId = :newPrimaryId or a.isPrimary = true)
    """)
    @Modifying
    int updatePrimaryAccount(@Param("walletId") Long walletId, @Param("newPrimaryId") Long newPrimaryId);

}
