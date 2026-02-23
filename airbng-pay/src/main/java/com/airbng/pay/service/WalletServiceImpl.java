package com.airbng.pay.service;

import com.airbng.common.lock.DistributedLock;
import com.airbng.pay.domain.*;
import com.airbng.pay.dto.*;
import com.airbng.pay.exception.AccountException;
import com.airbng.pay.exception.WalletException;
import com.airbng.pay.repository.AccountRepository;
import com.airbng.pay.repository.WalletRepository;
import com.airbng.pay.repository.WalletTxRepository;
import com.airbng.platform.security.principal.AirbngPrincipal;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.airbng.platform.common.response.status.BaseResponseStatus.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final WalletTxRepository walletTxRepository;
    private final AccountRepository accountRepository;
    private static final int PAGE_SIZE = 10;
    private static final BigDecimal MIN_TOPUP_AMOUNT = new BigDecimal("1000");
    private final EntityManager em;

    @Override
    public WalletBalanceResponse getBalance(AirbngPrincipal principal) {
        Wallet wallet = walletRepository.findByMemberId(principal.getId())
                .orElseThrow(() -> new WalletException(INVALID_WALLET));
        return WalletBalanceResponse.from(wallet);
    }

    @Transactional(readOnly = true)
    @Override
    public WalletOverviewResponse getOverview(AirbngPrincipal principal) {
        Wallet wallet = walletRepository.findByMemberId(principal.getId())
                .orElseThrow(() -> new WalletException(INVALID_WALLET));
        List<Account> accounts =
                accountRepository.findAllByWalletWalletIdOrderByIsPrimaryDescAccountIdAsc(wallet.getWalletId());
        return WalletOverviewResponse.from(wallet, accounts);
    }

    @DistributedLock(key = "'wallet:' + #principal.id", waitTime = 200, leaseTime = 3000)
    @Transactional
    @Override
    public void topup(AirbngPrincipal principal, String idemKeyRaw, WalletTopupRequest req) {
        UUID idemKey = UUID.fromString(idemKeyRaw);

        Optional<WalletTx> existing = walletTxRepository.findByWalletIdemKey(idemKey);
        if (existing.isPresent()) {throw new WalletException(ALREADY_PROCESSED);}

        Long memberId = principal.getId();

        Long walletId = walletRepository.findWalletIdByMemberId(memberId);
        if (walletId == null) {
            throw new WalletException(INVALID_WALLET);
        }

        BigDecimal amount = req.getBalance();
        if (amount.compareTo(MIN_TOPUP_AMOUNT) < 0) {
            throw new WalletException(INSUFFICIENT_TOPUP);
        }

        int accUpdated = accountRepository.subtractIfEnough(req.getAccountId(), walletId, amount);
        if (accUpdated == 0) {
            throw new AccountException(INSUFFICIENT_BALANCE_ACCOUNT);
        }

        int walletUpdated = walletRepository.addAvailable(walletId, amount);
        if (walletUpdated == 0) {
            throw new WalletException(INVALID_WALLET);
        }

        Wallet walletRef = em.getReference(Wallet.class, walletId);

        walletTxRepository.save(WalletTx.builder()
                .wallet(walletRef)
                .payment(null)
                .walletTxType(WalletTxType.TOPUP)
                .walletTxRole(WalletTxRole.CREDIT)
                .amount(amount)
                .walletIdemKey(idemKey)
                .build()
        );
    }

    @DistributedLock(key = "'wallet:' + #principal.id", waitTime = 200, leaseTime = 3000)
    @Transactional
    @Override
    public void withdraw(AirbngPrincipal principal, String idemKeyRaw, WalletWithdrawRequest req) {
        UUID idemKey = UUID.fromString(idemKeyRaw);
        Optional<WalletTx> existing = walletTxRepository.findByWalletIdemKey(idemKey);
        if (existing.isPresent()) {
            log.info("[페이머니 출금] 이미 진행된 결과");
            throw new WalletException(ALREADY_PROCESSED);
        }

        Long memberId = principal.getId();
        Long walletId = walletRepository.findWalletIdByMemberId(memberId);
        if (walletId == null) {
            throw new WalletException(INVALID_WALLET);
        }

        BigDecimal amount = req.getAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new WalletException(INVALID_WALLET);
        }

        int wUpdated = walletRepository.subtractAvailableIfEnough(walletId, amount);
        if (wUpdated == 0) {
            throw new WalletException(INSUFFICIENT_BALANCE);
        }

        int accUpdated = accountRepository.addBalance(req.getAccountId(), walletId, amount);
        if (accUpdated == 0) {
            throw new AccountException(WALLET_ACCOUNT_MISMATCH);
        }

        Wallet walletRef = em.getReference(Wallet.class, walletId);

        walletTxRepository.save(WalletTx.builder()
                .wallet(walletRef)
                .payment(null)
                .walletTxType(WalletTxType.WITHDRAW)
                .walletTxRole(WalletTxRole.DEBIT)
                .amount(amount)
                .walletIdemKey(idemKey)
                .build()
        );
    }

    @Transactional(readOnly = true)
    @Override
    public WalletHistoryResponse getHistory(AirbngPrincipal principal, Long cursor, WalletTxType type) {
        Wallet wallet = walletRepository.findByMemberId(principal.getId())
                .orElseThrow(() -> new WalletException(INVALID_WALLET));

        WalletTxRole role = null;
        if(WalletTxType.PAYMENT == type) {
            role = WalletTxRole.DEBIT;
        }

        List<WalletTx> fetched = walletTxRepository.findSliceByWalletIdAndCursorDesc(
                wallet.getWalletId(),
                cursor,
                type,
                role,
                PageRequest.of(0, PAGE_SIZE+1));

        boolean hasNext = fetched.size() > PAGE_SIZE;
        if (hasNext) {
            fetched = fetched.subList(0, PAGE_SIZE);
        }
        Long nextCursor = hasNext ? fetched.get(fetched.size() -1).getWalletTxId() : null;

        return WalletHistoryResponse.from(wallet, fetched, nextCursor, hasNext);
    }
}
