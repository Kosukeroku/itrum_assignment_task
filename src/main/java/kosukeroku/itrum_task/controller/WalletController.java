package kosukeroku.itrum_task.controller;

import jakarta.validation.Valid;
import kosukeroku.itrum_task.dto.WalletRequestDTO;
import kosukeroku.itrum_task.dto.WalletResponseDTO;
import kosukeroku.itrum_task.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @PostMapping("/wallet")
    public ResponseEntity<WalletResponseDTO> processOperation(@Valid @RequestBody WalletRequestDTO request) {
        log.debug("Received request: POST /wallet - {}", request);
        WalletResponseDTO response = walletService.processOperation(request);
        log.debug("Response: {}", response);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/wallets/{walletId}")
    public ResponseEntity<WalletResponseDTO> getBalance(@PathVariable UUID walletId) {
        log.debug("Received request: GET /wallets/{}", walletId);
        WalletResponseDTO response = walletService.getBalance(walletId);
        log.debug("Response: {}", response);
        return ResponseEntity.ok(response);
    }
}