package com.echaris.api.commitmentduration;

import com.echaris.api.commitmentduration.request.CommitmentDurationRequest;
import com.echaris.api.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/commitments")
@RequiredArgsConstructor
public class CommitmentDurationController {

    private final CommitmentDurationService durationService;

    @GetMapping("/private/durations")
    public ApiResponse<List<CommitmentDuration>> available() {
        return new ApiResponse<>(
                true,
                "Available durations retrieved",
                durationService.available()
        );
    }

    @GetMapping("/private/admin/durations")
    public ApiResponse<List<CommitmentDuration>> listAll() {
        return new ApiResponse<>(
                true,
                "Commitment durations retrieved",
                durationService.listAll()
        );
    }

    @PostMapping("/private/admin/durations")
    public ApiResponse<CommitmentDuration> create(
            @Valid @RequestBody CommitmentDurationRequest request
    ) {
        return new ApiResponse<>(
                true,
                "Commitment duration created",
                durationService.create(request)
        );
    }

    @PutMapping("/private/admin/durations/{durationId}")
    public ApiResponse<CommitmentDuration> update(
            @PathVariable String durationId,
            @Valid @RequestBody CommitmentDurationRequest request
    ) {
        return new ApiResponse<>(
                true,
                "Commitment duration updated",
                durationService.update(durationId, request)
        );
    }

    @DeleteMapping("/private/admin/durations/{durationId}")
    public ApiResponse<Void> remove(
            @PathVariable String durationId
    ) {
        durationService.remove(durationId);

        return new ApiResponse<>(
                true,
                "Commitment duration removed",
                null
        );
    }
}