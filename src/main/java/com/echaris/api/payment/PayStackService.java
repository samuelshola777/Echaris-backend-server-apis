package com.echaris.api.payment;

import com.echaris.api.utils.exception.InvalidInputException;
import com.echaris.api.utils.exception.InvalidOperationException;
import com.echaris.api.utils.exception.ProviderPaymentNotFoundException;
import com.echaris.api.utils.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
public class PayStackService {

    private static final ParameterizedTypeReference<Map<String, Object>>
            RESPONSE_TYPE = new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final String secretKey;

    @Value("${paystack.webhook.signing-secret:}")
    private String signingSecret;

    public PayStackService(
            @Value("${paystack.base.url}") String baseUrl,
            @Value("${paystack.secret.key}") String secretKey
    ) {
        this.secretKey = secretKey;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeaders(
                        headers -> headers.setBearerAuth(secretKey)
                )
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();
    }

    public Map<String, Object> initializePayment(
            Map<String, Object> body
    ) {
        return sendRequest(
                HttpMethod.POST,
                "/transaction/initialize",
                body
        );
    }

    public Map<String, Object> verifyPayment(String reference) {
        return sendRequest(
                HttpMethod.GET,
                "/transaction/verify/{reference}",
                null,
                required(reference, "Payment reference")
        );
    }

    public Map<String, Object> resolveAccount(
            String accountNumber,
            String bankCode
    ) {
        return sendRequest(
                HttpMethod.GET,
                "/bank/resolve?account_number={account}&bank_code={bank}",
                null,
                required(accountNumber, "Account number"),
                required(bankCode, "Bank code")
        );
    }

    public Map<String, Object> getBanks() {
        return sendRequest(
                HttpMethod.GET,
                "/bank?country=nigeria&perPage=100",
                null
        );
    }

    public Map<String, Object> createRecipient(
            Map<String, Object> body
    ) {
        return sendRequest(
                HttpMethod.POST,
                "/transferrecipient",
                body
        );
    }

    public Map<String, Object> initiateTransfer(
            Map<String, Object> body
    ) {
        return sendRequest(
                HttpMethod.POST,
                "/transfer",
                body
        );
    }

    public Map<String, Object> verifyTransfer(String reference) {
        return sendRequest(
                HttpMethod.GET,
                "/transfer/verify/{reference}",
                null,
                required(reference, "Transfer reference")
        );
    }

    private Map<String, Object> sendRequest(
            HttpMethod method,
            String path,
            Map<String, Object> body,
            Object... uriVariables
    ) {
        try {
            RestClient.RequestBodySpec request = restClient
                    .method(method)
                    .uri(path, uriVariables);

            if (body != null) {
                request.contentType(MediaType.APPLICATION_JSON)
                        .body(body);
            }

            Map<String, Object> response = request
                    .retrieve()
                    .body(RESPONSE_TYPE);

            if (response == null || response.isEmpty()) {
                throw new InvalidOperationException(
                        "Paystack returned an empty response"
                );
            }

            if (method == HttpMethod.GET
                    && transactionNotFound(response)) {

                throw new ProviderPaymentNotFoundException(
                        "Paystack"
                );
            }

            return response;

        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();

            if (method == HttpMethod.GET
                    && (status == 400 || status == 404)) {

                Map<String, Object> errorResponse = null;

                try {
                    errorResponse =
                            exception.getResponseBodyAs(RESPONSE_TYPE);
                } catch (RuntimeException ignored) {
                    // Unknown responses do not prove payment failure.
                }

                if (transactionNotFound(errorResponse)) {
                    throw new ProviderPaymentNotFoundException(
                            "Paystack"
                    );
                }
            }

            throw new InvalidOperationException(
                    "Paystack returned HTTP "
                            + status
                            + ". Payment status is unconfirmed."
            );

        } catch (RestClientException exception) {
            throw new InvalidOperationException(
                    "Unable to communicate with Paystack. "
                            + "Payment status is unconfirmed."
            );
        }
    }

    private boolean transactionNotFound(
            Map<String, Object> response
    ) {
        if (response == null
                || response.get("data") != null
                || !"false".equalsIgnoreCase(
                Objects.toString(response.get("status"), "")
        )) {
            return false;
        }

        String message = Objects.toString(
                        response.get("message"),
                        ""
                )
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[.!]+$", "");

        return Set.of(
                "transaction reference not found",
                "transaction not found"
        ).contains(message);
    }

    private String required(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException(
                    field + " is required"
            );
        }

        return value.trim();
    }

    public void verifyWebhookSignature(
            byte[] body,
            String legacyHeader,
            String signingHeader
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

            byte[] signed =
                    new byte[prefix.length + body.length];

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

            byte[] expected = hmac(
                    signingSecret,
                    signed
            );

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
                hmac(secretKey, body),
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