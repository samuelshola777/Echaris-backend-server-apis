package com.echaris.api.payment;

import com.echaris.api.utils.exception.UnauthorizedException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

public final class PaystackSignature {

    private PaystackSignature() {
    }

    public static void verify(
            byte[] body,
            String legacyHeader,
            String signingHeader,
            String apiSecret,
            String signingSecret
    ) {
        if (signingHeader != null
                || (signingSecret != null
                && !signingSecret.isBlank())) {

            if (signingHeader == null
                    || signingSecret == null
                    || signingSecret.isBlank()) {
                deny();
            }

            String timestamp = null;

            for (String part : signingHeader.split(",")) {
                if (part.trim().startsWith("t=")) {
                    timestamp = part.trim().substring(2);
                }
            }

            long seconds;

            try {
                seconds = Long.parseLong(timestamp);
            } catch (RuntimeException exception) {
                deny();
                return;
            }

            long now = Instant.now().getEpochSecond();

            if (seconds < now - 300 || seconds > now + 300) {
                deny();
            }

            byte[] prefix = (timestamp + ".")
                    .getBytes(StandardCharsets.UTF_8);

            byte[] signed = new byte[prefix.length + body.length];

            System.arraycopy(
                    prefix,
                    0,
                    signed,
                    0,
                    prefix.length
            );

            System.arraycopy(
                    body,
                    0,
                    signed,
                    prefix.length,
                    body.length
            );

            byte[] expected = hmac(signingSecret, signed);

            for (String part : signingHeader.split(",")) {
                String value = part.trim();

                if (value.startsWith("v1=")) {
                    int colon = value.indexOf(':');

                    if (colon > 3
                            && matches(
                            expected,
                            value.substring(colon + 1)
                    )) {
                        return;
                    }
                }
            }

            deny();

        } else if (!matches(
                hmac(apiSecret, body),
                legacyHeader
        )) {
            deny();
        }
    }

    private static byte[] hmac(
            String secret,
            byte[] body
    ) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");

            mac.init(
                    new SecretKeySpec(
                            secret.getBytes(StandardCharsets.UTF_8),
                            "HmacSHA512"
                    )
            );

            return mac.doFinal(body);

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Webhook verification could not be performed",
                    exception
            );
        }
    }

    private static boolean matches(
            byte[] expected,
            String value
    ) {
        return value != null
                && value.matches("[0-9a-f]{128}")
                && MessageDigest.isEqual(
                expected,
                HexFormat.of().parseHex(value)
        );
    }

    private static void deny() {
        throw new UnauthorizedException(
                "Invalid Paystack signature"
        );
    }
}