package kosukeroku.itrum_task.service;

import kosukeroku.itrum_task.dto.OperationType;
import kosukeroku.itrum_task.dto.WalletRequestDTO;
import kosukeroku.itrum_task.dto.WalletResponseDTO;
import kosukeroku.itrum_task.exception.InsufficientFundsException;
import kosukeroku.itrum_task.exception.WalletNotFoundException;
import kosukeroku.itrum_task.mapper.WalletMapper;
import kosukeroku.itrum_task.model.Wallet;
import kosukeroku.itrum_task.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletMapper walletMapper;

    @Transactional
    public WalletResponseDTO processOperation(WalletRequestDTO request) {
        log.debug("Processing operation: walletId = {}, type = {}, amount = {}",
                request.getWalletId(), request.getOperationType(), request.getAmount());

        Wallet wallet = walletRepository.findById(request.getWalletId()) //getting a wallet with pessimistic lock
                .orElseThrow(() -> {
                    log.warn("Wallet not found: {}", request.getWalletId());
                    return new WalletNotFoundException(request.getWalletId());
                });

        log.debug("Current balance: {}", wallet.getBalance());

        if (request.getOperationType() == OperationType.DEPOSIT) { //processing either deposit or withdrawal operation
            deposit(wallet, request.getAmount());
            log.debug("Deposit successful. New balance: {}", wallet.getBalance());
        } else {
            withdraw(wallet, request.getAmount());
            log.debug("Withdrawal successful. New balance: {}", wallet.getBalance());
        }

        return walletMapper.toResponseDto(wallet);
    }

    @Transactional(readOnly = true)
    public WalletResponseDTO getBalance(UUID walletId) {
        log.debug("Getting balance for wallet: {}", walletId);

        Wallet wallet = walletRepository.findByIdWithoutBlocking(walletId) //reading without lock
                .orElseThrow(() -> {
                    log.warn("Wallet not found: {}", walletId);
                    return new WalletNotFoundException(walletId);
                });

        log.debug("Current balance for wallet {}: {}", walletId, wallet.getBalance());

        return walletMapper.toResponseDto(wallet);
    }

    private void deposit(Wallet wallet, BigDecimal amount) {
        log.debug("Depositing {} to wallet {}", amount, wallet.getId());
        wallet.setBalance(wallet.getBalance().add(amount));
    }

    private void withdraw(Wallet wallet, BigDecimal amount) {
        log.debug("Withdrawing {} from wallet {}", amount, wallet.getId());
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(wallet.getId(), wallet.getBalance(), amount);
        }
        wallet.setBalance(wallet.getBalance().subtract(amount));
    }
}
