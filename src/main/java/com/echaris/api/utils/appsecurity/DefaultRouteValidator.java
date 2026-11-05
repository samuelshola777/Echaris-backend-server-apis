package com.echaris.api.utils.appsecurity;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Component
public class DefaultRouteValidator {

    private static final Logger log =
            LoggerFactory.getLogger(
                    DefaultRouteValidator.class
            );

    private static Pattern route(
            String routePrefix
    ) {
        return Pattern.compile(
                "^"
                        + Pattern.quote(routePrefix)
                        + "(?:/.*)?$"
        );
    }

    private static List<Pattern> routes(
            String... routePrefixes
    ) {
        return Arrays.stream(routePrefixes)
                .distinct()
                .map(DefaultRouteValidator::route)
                .toList();
    }

    public static final List<Pattern> ADMIN_ENDPOINTS =
            routes(
                    "/api/v1/commitments/private/admin",
                    "/api/v1/notifications/private",
                    "/api/v1/product/private",
                    "/api/v1/admin/private",
                    "/api/v1/customer/private/admin",
                    "/api/v1/vendor/private/admin",
                    "/api/v1/files/private",
                    "/api/v1/dashboard/private",
                    "/api/v1/dashboard/private/admin",
                    "/api/v1/dashboard"
            );

    public DefaultRouteValidator() {
        log.info(
                "[DRV-INIT] Admin patterns={}",
                ADMIN_ENDPOINTS.size()
        );
    }

    public Predicate<HttpServletRequest> isCustomerSecured =
            req -> {
                String uri = req.getRequestURI();

                if (uri.equals(
                        "/api/v1/commitments/private"
                ) || uri.startsWith(
                        "/api/v1/commitments/private/"
                )) {
                    return !uri.equals(
                            "/api/v1/commitments/private/admin"
                    ) && !uri.startsWith(
                            "/api/v1/commitments/private/admin/"
                    );
                }

                if (uri.equals(
                        "/api/v1/notifications/private"
                ) || uri.startsWith(
                        "/api/v1/notifications/private/"
                )) {
                    return true;
                }

                return (
                        uri.equals(
                                "/api/v1/customer/private"
                        ) || uri.startsWith(
                                "/api/v1/customer/private/"
                        )
                )
                        && !uri.equals(
                        "/api/v1/customer/private/admin"
                )
                        && !uri.startsWith(
                        "/api/v1/customer/private/admin/"
                );
            };

    public Predicate<HttpServletRequest> isAdminSecured =
            req -> matches(
                    req,
                    ADMIN_ENDPOINTS,
                    "admin"
            );

    private boolean matches(
            HttpServletRequest request,
            List<Pattern> patterns,
            String groupName
    ) {
        String uri = request.getRequestURI();

        return patterns.stream()
                .anyMatch(pattern ->
                        pattern.matcher(uri).matches()
                );
    }
}