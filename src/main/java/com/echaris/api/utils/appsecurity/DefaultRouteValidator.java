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

    private static final Pattern PRODUCT_CATALOG =
            route("/api/v1/product/private/catalog");

    private static final Pattern PRIVATE_ADDRESSES =
            route("/api/v1/addresses/private");

    private static final Pattern PRIVATE_CART =
            route("/api/v1/cart/private");

    private static final Pattern PRIVATE_MARKETPLACE = route("/api/v1/marketplace/private");
    private static final Pattern ADMIN_MARKETPLACE = route("/api/v1/marketplace/private/admin");

    public static final List<Pattern> ADMIN_ENDPOINTS =
            routes(
                    "/api/v1/commitments/private/admin",
                    "/api/v1/user-payments/private/admin",
                    "/api/v1/marketplace/private/admin",
                    "/api/v1/notifications/private",
                    "/api/v1/product/private",
                    "/api/v1/admin/private",
                    "/api/v1/account-details/private/admin",
                    "/api/v1/addresses/private",
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

                if (uri.equals("/api/v1/user-payments/private")) {
                    return true;
                }

                if (PRIVATE_MARKETPLACE.matcher(uri).matches()) {
                    return !ADMIN_MARKETPLACE.matcher(uri).matches();
                }

                if (PRIVATE_ADDRESSES.matcher(uri).matches() || PRIVATE_CART.matcher(uri).matches()) {
                    return true;
                }

                if ("GET".equals(req.getMethod())
                        && PRODUCT_CATALOG.matcher(uri).matches()) {
                    return true;
                }

                if ("POST".equals(req.getMethod())
                        && uri.equals("/api/v1/product/private/catalog/filter")) {
                    return true;
                }

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