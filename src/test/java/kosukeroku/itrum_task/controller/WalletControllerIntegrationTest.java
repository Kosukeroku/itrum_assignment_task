package kosukeroku.itrum_task.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kosukeroku.itrum_task.dto.OperationType;
import kosukeroku.itrum_task.dto.WalletRequestDTO;
import kosukeroku.itrum_task.model.Wallet;
import kosukeroku.itrum_task.repository.WalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WalletControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WalletRepository walletRepository;

    private UUID existingWalletId;
    private Wallet existingWallet;

    @BeforeEach
    void setUp() {
        walletRepository.deleteAll();

        existingWallet = new Wallet();
        existingWallet.setId(UUID.fromString("123a4567-b89c-12d3-e456-123456789012"));
        existingWallet.setBalance(new BigDecimal("1000.00"));
        existingWallet.setCreatedAt(LocalDateTime.now());
        existingWallet.setUpdatedAt(LocalDateTime.now());

        existingWallet = walletRepository.save(existingWallet);
        existingWalletId = existingWallet.getId();
    }

    @AfterEach
    void tearDown() {
        walletRepository.deleteAll();
    }

    @Test
    void processOperation_ShouldReturn200AndUpdatedBalance_WhenDepositIsSuccessful() throws Exception {
        // given
        WalletRequestDTO request = WalletRequestDTO.builder()
                .walletId(existingWalletId)
                .operationType(OperationType.DEPOSIT)
                .amount(new BigDecimal("500.00"))
                .build();

        // then
        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existingWalletId.toString()))
                .andExpect(jsonPath("$.balance").value(1500.00));

        Wallet updatedWallet = walletRepository.findById(existingWalletId).orElseThrow();
        assertThat(updatedWallet.getBalance()).isEqualByComparingTo("1500.00"); // 1000 + 500
    }

    @Test
    void processOperation_ShouldReturn200AndUpdatedBalance_WhenWithdrawalIsSuccessful() throws Exception {
        // given
        WalletRequestDTO request = WalletRequestDTO.builder()
                .walletId(existingWalletId)
                .operationType(OperationType.WITHDRAW)
                .amount(new BigDecimal("300.00"))
                .build();

        // then
        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existingWalletId.toString()))
                .andExpect(jsonPath("$.balance").value(700.00));

        Wallet updatedWallet = walletRepository.findById(existingWalletId).orElseThrow();
        assertThat(updatedWallet.getBalance()).isEqualByComparingTo("700.00"); // 1000 - 300
    }

    @Test
    void processOperation_ShouldReturn404_WhenWalletIsNotFound() throws Exception {
        // given
        UUID nonExistentId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        WalletRequestDTO request = WalletRequestDTO.builder()
                .walletId(nonExistentId)
                .operationType(OperationType.DEPOSIT)
                .amount(new BigDecimal("100.00"))
                .build();

        // then
        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Wallet Not Found"))
                .andExpect(jsonPath("$.message").value(containsString(nonExistentId.toString())))
                .andExpect(jsonPath("$.path").value("/api/v1/wallet"));
    }

    @Test
    void processOperation_ShouldReturn400_WhenFundsAreInsufficient() throws Exception {
        // given
        WalletRequestDTO request = WalletRequestDTO.builder()
                .walletId(existingWalletId)
                .operationType(OperationType.WITHDRAW)
                .amount(new BigDecimal("2000.00"))
                .build();

        // then
        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Insufficient Funds"))
                .andExpect(jsonPath("$.message").value(containsString("Balance: 1000")))
                .andExpect(jsonPath("$.message").value(containsString("Requested: 2000")));

        Wallet unchangedWallet = walletRepository.findById(existingWalletId).orElseThrow();
        assertThat(unchangedWallet.getBalance()).isEqualByComparingTo("1000.00"); // verifying balance hasn't changed
    }

    @Test
    void processOperation_ShouldReturn400_WhenWalletIdIsNull() throws Exception {
        // given
        String requestJson = """
                {
                    "operationType": "DEPOSIT",
                    "amount": 100.00
                }
                """;

        // then
        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"))
                .andExpect(jsonPath("$.message").value("Invalid request parameters"));
    }

    @Test
    void processOperation_ShouldReturn400_WhenAmountIsNull() throws Exception {
        // given
        String requestJson = String.format("""
                {
                    "walletId": "%s",
                    "operationType": "DEPOSIT"
                }
                """, existingWalletId);

        // then
        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"))
                .andExpect(jsonPath("$.message").value("Invalid request parameters"));
    }

    @Test
    void processOperation_ShouldReturn400_WhenAmountIsNegative() throws Exception {
        // given
        WalletRequestDTO request = WalletRequestDTO.builder()
                .walletId(existingWalletId)
                .operationType(OperationType.DEPOSIT)
                .amount(new BigDecimal("-100.00"))
                .build();

        // then
        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"))
                .andExpect(jsonPath("$.message").value("Invalid request parameters"));

        Wallet unchangedWallet = walletRepository.findById(existingWalletId).orElseThrow();
        assertThat(unchangedWallet.getBalance()).isEqualByComparingTo("1000.00"); // verifying balance hasn't changed
    }

    @Test
    void processOperation_ShouldReturn400_WhenOperationTypeIsInvalid() throws Exception {
        // given
        String requestJson = String.format("""
                {
                    "walletId": "%s",
                    "operationType": "INVALID",
                    "amount": 100.00
                }
                """, existingWalletId);

        // then
        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid JSON Request"))
                .andExpect(jsonPath("$.message").value("Invalid request format"));
    }

    @Test
    void processOperation_ShouldReturn400_WhenRequestBodyIsInvalid() throws Exception {
        // given
        String malformedJson = "{ invalid json }";

        // then
        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid JSON Request"))
                .andExpect(jsonPath("$.message").value("Invalid request format"));
    }

    @Test
    void getBalance_ShouldReturn200AndBalance_WhenWalletExists() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/{walletId}", existingWalletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existingWalletId.toString()))
                .andExpect(jsonPath("$.balance").value(1000.00));
    }

    @Test
    void getBalance_ShouldReturn404_WhenWalletIsNotFound() throws Exception {
        // given
        UUID nonExistentId = UUID.fromString("00000000-0000-0000-0000-000000000000");

        // then
        mockMvc.perform(get("/api/v1/wallets/{walletId}", nonExistentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Wallet Not Found"))
                .andExpect(jsonPath("$.message").value(containsString(nonExistentId.toString())))
                .andExpect(jsonPath("$.path").value("/api/v1/wallets/" + nonExistentId));
    }

    @Test
    void getBalance_ShouldReturn400_WhenWalletIdIsInvalid() throws Exception {
        String invalidUUID = "invalid-uuid";
        mockMvc.perform(get("/api/v1/wallets/{walletId}", invalidUUID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Request"))
                .andExpect(jsonPath("$.message").value(containsString(invalidUUID)))
                .andExpect(jsonPath("$.path").value("/api/v1/wallets/" + invalidUUID));
    }

}