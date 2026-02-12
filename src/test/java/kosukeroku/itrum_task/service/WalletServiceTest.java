package kosukeroku.itrum_task.service;

import kosukeroku.itrum_task.dto.OperationType;
import kosukeroku.itrum_task.dto.WalletRequestDTO;
import kosukeroku.itrum_task.dto.WalletResponseDTO;
import kosukeroku.itrum_task.exception.InsufficientFundsException;
import kosukeroku.itrum_task.exception.WalletNotFoundException;
import kosukeroku.itrum_task.mapper.WalletMapper;
import kosukeroku.itrum_task.model.Wallet;
import kosukeroku.itrum_task.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public @ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletMapper walletMapper;

    @InjectMocks
    private WalletService walletService;

    private UUID walletId;
    private Wallet wallet;
    private WalletResponseDTO walletResponseDTO;

    @BeforeEach
    void setUp() {
        walletId = UUID.fromString("123a4567-b89c-12d3-e456-123456789012");

        wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setBalance(new BigDecimal("1000.00"));
        wallet.setCreatedAt(LocalDateTime.now());
        wallet.setUpdatedAt(LocalDateTime.now());

        walletResponseDTO = WalletResponseDTO.builder()
                .id(walletId)
                .balance(new BigDecimal("1000.00"))
                .build();
    }

    @Test
    void processOperation_ShouldIncreaseBalance_WhenDepositIsSuccessful() {
        // given
        WalletRequestDTO request = WalletRequestDTO.builder()
                .walletId(walletId)
                .operationType(OperationType.DEPOSIT)
                .amount(new BigDecimal("500.00"))
                .build();

        when(walletRepository.findByIdWithLock(walletId)).thenReturn(Optional.of(wallet));
        when(walletMapper.toResponseDto(any(Wallet.class))).thenReturn(walletResponseDTO);

        // when
        WalletResponseDTO response = walletService.processOperation(request);

        // then
        assertThat(response).isNotNull();
        verify(walletRepository).findByIdWithLock(walletId);
        verify(walletMapper).toResponseDto(any(Wallet.class));

        assertThat(wallet.getBalance()).isEqualByComparingTo("1500.00"); // 1000 + 500
    }

    @Test
    void processOperation_ShouldReduceBalance_WhenWithdrawalIsSuccessful() {
        // given
        WalletRequestDTO request = WalletRequestDTO.builder()
                .walletId(walletId)
                .operationType(OperationType.WITHDRAW)
                .amount(new BigDecimal("300.00"))
                .build();

        when(walletRepository.findByIdWithLock(walletId)).thenReturn(Optional.of(wallet));
        when(walletMapper.toResponseDto(any(Wallet.class))).thenReturn(walletResponseDTO);

        // when
        WalletResponseDTO response = walletService.processOperation(request);

        // then
        assertThat(response).isNotNull();
        verify(walletRepository).findByIdWithLock(walletId);
        verify(walletMapper).toResponseDto(any(Wallet.class));

        assertThat(wallet.getBalance()).isEqualByComparingTo("700.00"); // 1000 - 300
    }

    @Test
    void processOperation_ShouldThrowException_WhenWalletIsNotFound() {
        // given
        WalletRequestDTO request = WalletRequestDTO.builder()
                .walletId(walletId)
                .operationType(OperationType.DEPOSIT)
                .amount(new BigDecimal("100.00"))
                .build();

        when(walletRepository.findByIdWithLock(walletId)).thenReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> walletService.processOperation(request))
                .isInstanceOf(WalletNotFoundException.class);

        verify(walletRepository).findByIdWithLock(walletId);
        verifyNoInteractions(walletMapper);
    }

    @Test
    void processOperation_ShouldThrowException_WhenFundsAreInsufficient() {
        // given
        WalletRequestDTO request = WalletRequestDTO.builder()
                .walletId(walletId)
                .operationType(OperationType.WITHDRAW)
                .amount(new BigDecimal("2000.00"))
                .build();

        when(walletRepository.findByIdWithLock(walletId)).thenReturn(Optional.of(wallet));

        // then
        assertThatThrownBy(() -> walletService.processOperation(request))
                .isInstanceOf(InsufficientFundsException.class);

        verify(walletRepository).findByIdWithLock(walletId);
        verify(walletMapper, never()).toResponseDto(any());
        assertThat(wallet.getBalance()).isEqualByComparingTo("1000.00"); // balance unchanged
    }

    @Test
    void getBalance_ShouldReturnBalance_WhenWalletExists() {
        // given
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(walletMapper.toResponseDto(wallet)).thenReturn(walletResponseDTO);

        // when
        WalletResponseDTO response = walletService.getBalance(walletId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(walletId);
        assertThat(response.getBalance()).isEqualByComparingTo("1000.00");

        verify(walletRepository).findById(walletId);
        verify(walletMapper).toResponseDto(wallet);
    }

    @Test
    void getBalance_ShouldThrowException_WhenWalletIsNotFound() {
        // given
        when(walletRepository.findById(walletId)).thenReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> walletService.getBalance(walletId))
                .isInstanceOf(WalletNotFoundException.class);

        verify(walletRepository).findById(walletId);
        verifyNoInteractions(walletMapper);
    }

    @Test
    void processOperation_ShouldReturnCorrectNumbers_WhenHandlingMultipleOperations() {
        // given
        when(walletRepository.findByIdWithLock(walletId)).thenReturn(Optional.of(wallet));
        when(walletMapper.toResponseDto(any(Wallet.class))).thenReturn(walletResponseDTO);

        // when deposit 500
        WalletRequestDTO depositRequest = WalletRequestDTO.builder()
                .walletId(walletId)
                .operationType(OperationType.DEPOSIT)
                .amount(new BigDecimal("500.00"))
                .build();
        walletService.processOperation(depositRequest);

        // then
        assertThat(wallet.getBalance()).isEqualByComparingTo("1500.00");

        // when withdraw 300
        WalletRequestDTO withdrawRequest = WalletRequestDTO.builder()
                .walletId(walletId)
                .operationType(OperationType.WITHDRAW)
                .amount(new BigDecimal("300.00"))
                .build();
        walletService.processOperation(withdrawRequest);

        // then
        assertThat(wallet.getBalance()).isEqualByComparingTo("1200.00");  // 1500 - 300

        // when deposit 200
        WalletRequestDTO depositRequest2 = WalletRequestDTO.builder()
                .walletId(walletId)
                .operationType(OperationType.DEPOSIT)
                .amount(new BigDecimal("200.00"))
                .build();
        walletService.processOperation(depositRequest2);

        // then
        assertThat(wallet.getBalance()).isEqualByComparingTo("1400.00");  // 1200 + 200
    }
}
