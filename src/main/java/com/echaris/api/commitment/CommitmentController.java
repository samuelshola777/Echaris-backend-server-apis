package com.echaris.api.commitment;

import com.echaris.api.commitment.request.CommitmentCreateRequest;
import com.echaris.api.commitment.request.WithdrawalRequest;
import com.echaris.api.commitmentrate.CommitmentRate;
import com.echaris.api.commitmentrate.CommitmentRateService;
import com.echaris.api.commitmentrate.request.RateChangeRequest;
import com.echaris.api.payment.PayStackService;
import com.echaris.api.payment.Transaction;
import com.echaris.api.utils.ApiResponse;
import com.echaris.api.utils.PaginatedData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/commitments")
@RequiredArgsConstructor
public class CommitmentController {

    private final CommitmentService commitmentService;
    private final CommitmentRateService rateService;
    private final PayStackService payStackService;

    @PostMapping("/private")
    public ApiResponse<Commitment> create(@Valid @RequestBody CommitmentCreateRequest request) {
        return new ApiResponse<>(true, "Commitment created; awaiting payment", commitmentService.create(request));
    }

    @GetMapping("/private")
    public ApiResponse<PaginatedData> list(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return new ApiResponse<>(true, "Commitments retrieved", commitmentService.list(page, size));
    }

    @GetMapping("/private/{commitmentId}")
    public ApiResponse<Commitment> get(@PathVariable String commitmentId) {
        return new ApiResponse<>(true, "Commitment retrieved", commitmentService.get(commitmentId));
    }

    @GetMapping("/private/{commitmentId}/returns")
    public ApiResponse<List<CommitmentReturn>> returns(@PathVariable String commitmentId) {
        return new ApiResponse<>(true, "Interest records retrieved", commitmentService.returns(commitmentId));
    }

    @PostMapping("/private/{commitmentId}/funding")
    public ApiResponse<Transaction> fund(@PathVariable String commitmentId, @RequestHeader("Idempotency-Key") String requestKey) {
        return new ApiResponse<>(true, "Funding transaction retrieved", commitmentService.fund(commitmentId, requestKey));
    }

    @PostMapping("/private/{commitmentId}/withdrawals")
    public ApiResponse<Transaction> withdraw(@PathVariable String commitmentId, @RequestHeader("Idempotency-Key") String requestKey, @Valid @RequestBody WithdrawalRequest request) {
        return new ApiResponse<>(true, "Withdrawal transaction retrieved", commitmentService.withdraw(commitmentId, requestKey, request));
    }

    @GetMapping("/private/transactions")
    public ApiResponse<PaginatedData> transactions(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return new ApiResponse<>(true, "Transactions retrieved", commitmentService.transactions(page, size));
    }

    @PostMapping("/private/transactions/{transactionId}/verify")
    public ApiResponse<Transaction> verify(@PathVariable String transactionId) {
        return new ApiResponse<>(true, "Transaction status retrieved", commitmentService.verify(transactionId, false));
    }

    @GetMapping("/private/payments")
    public ApiResponse<PaginatedData> payments(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return new ApiResponse<>(true, "Payments retrieved", commitmentService.payments(page, size));
    }

    @GetMapping("/private/banks")
    public ApiResponse<Map<String, Object>> banks() {
        commitmentService.currentUser();

        return new ApiResponse<>(true, "Banks retrieved", payStackService.getBanks());
    }

    @GetMapping("/private/rate")
    public ApiResponse<CommitmentRate> rate() {
        commitmentService.currentUser();

        return new ApiResponse<>(true, "Current monthly rate retrieved", rateService.current());
    }

    @GetMapping("/private/admin")
    public ApiResponse<PaginatedData> adminList(@RequestParam(required = false) String userId, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return new ApiResponse<>(true, "Commitments retrieved", commitmentService.adminCommitments(userId, page, size));
    }

    @GetMapping("/private/admin/rate")
    public ApiResponse<CommitmentRate> adminRate() {
        commitmentService.requireAdmin();

        return new ApiResponse<>(true, "Current monthly rate retrieved", rateService.current());
    }

    @PatchMapping("/private/admin/rate")
    public ApiResponse<CommitmentRate> changeRate(@Valid @RequestBody RateChangeRequest request) {
        return new ApiResponse<>(true, "Monthly rate updated", rateService.change(request, commitmentService.requireAdmin().getId(), commitmentService.currentIp()));
    }

    @GetMapping("/private/admin/rate/history")
    public ApiResponse<PaginatedData> history(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return new ApiResponse<>(true, "Rate history retrieved", commitmentService.history(page, size));
    }

    @GetMapping("/private/admin/audits")
    public ApiResponse<PaginatedData> audits(@RequestParam(required = false) String userId, @RequestParam(required = false) String event, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return new ApiResponse<>(true, "Audit records retrieved", commitmentService.audits(userId, event, page, size));
    }

    @GetMapping("/private/admin/transactions")
    public ApiResponse<PaginatedData> adminTransactions(@RequestParam(required = false) String userId, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return new ApiResponse<>(true, "Transactions retrieved", commitmentService.adminMoney(false, userId, page, size));
    }

    @PostMapping("/private/admin/transactions/{transactionId}/verify")
    public ApiResponse<Transaction> adminVerify(@PathVariable String transactionId) {
        return new ApiResponse<>(true, "Transaction status retrieved", commitmentService.verify(transactionId, true));
    }

    @GetMapping("/private/admin/payments")
    public ApiResponse<PaginatedData> adminPayments(@RequestParam(required = false) String userId, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return new ApiResponse<>(true, "Payments retrieved", commitmentService.adminMoney(true, userId, page, size));
    }
}