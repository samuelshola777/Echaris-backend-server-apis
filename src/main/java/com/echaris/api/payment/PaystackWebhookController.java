package com.echaris.api.payment;

import com.echaris.api.commitment.CommitmentService;
import com.echaris.api.utils.exception.InvalidInputException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/commitments")
@RequiredArgsConstructor
public class PaystackWebhookController {

    private final CommitmentService commitmentService;
    private final ObjectMapper objectMapper;
    private final PayStackService payStackService;

    @PostMapping("/public/paystack/webhook")
    public void webhook(
            @RequestBody byte[] body,

            @RequestHeader(
                    value = "x-paystack-signature",
                    required = false
            )
            String legacySignature,

            @RequestHeader(
                    value = "Paystack-Signature",
                    required = false
            )
            String signingHeader
    ) {
        payStackService.verifyWebhookSignature(
                body,
                legacySignature,
                signingHeader
        );

        Map<String, Object> payload;

        try {
            payload = objectMapper.readValue(
                    body,
                    new TypeReference<>() {}
            );

        } catch (java.io.IOException exception) {
            throw new InvalidInputException(
                    "Invalid Paystack event body"
            );
        }

        String event = Objects.toString(
                payload.get("event"),
                ""
        );

        if (!Set.of(
                "charge.success",
                "transfer.success",
                "transfer.failed",
                "transfer.reversed"
        ).contains(event)) {
            return;
        }

        if (!(payload.get("data") instanceof Map<?, ?> data)) {
            throw new InvalidInputException(
                    "Event data is required"
            );
        }

        commitmentService.webhook(event, data);
    }
}