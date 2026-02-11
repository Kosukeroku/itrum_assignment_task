package kosukeroku.itrum_task.exception;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(UUID walletId, BigDecimal balance, BigDecimal requested) {
        super(String.format("Insufficient funds for wallet %s. Balance: %s, Requested: %s",
                walletId, balance, requested));
    }
}