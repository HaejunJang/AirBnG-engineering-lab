package com.airbng.pay.repository;

import com.airbng.pay.domain.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {
    // 데드락 방지 - memberId 오름차순 정렬로 락 획득
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.memberId in :memberIds order by w.walletId asc")
    List<Wallet> findWalletsWithLockByMemberIds(@Param("memberIds") Collection<Long> memberIds);

    //    @Lock(LockModeType.PESSIMISTIC_WRITE)
//    @Query("select w from Wallet w where w.memberId = :memberId")
//    Optional<Wallet> findByMemberIdForUpdate(@Param("memberId") Long memberId);

    @Query("select w.walletId from Wallet w where w.memberId = :memberId")
    Long findWalletIdByMemberId(@Param("memberId") Long memberId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Wallet w set w.balanceAvailable = w.balanceAvailable + :amount where w.walletId = :walletId")
    int addAvailable(@Param("walletId") Long walletId, @Param("amount") BigDecimal amount);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Wallet w set w.balanceAvailable = w.balanceAvailable - :amount where w.walletId = :walletId and w.balanceAvailable >= :amount")
    int subtractAvailableIfEnough(@Param("walletId") Long walletId, @Param("amount") BigDecimal amount);

    Optional<Wallet> findByMemberId(Long memberId);
}