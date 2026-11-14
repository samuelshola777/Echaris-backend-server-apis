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

    private static final Logger log = LoggerFactory.getLogger(DefaultRouteValidator.class);

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Pattern route(String prefix) {
        return Pattern.compile("^" + Pattern.quote(prefix) + "(?:/.*)?$");
    }

    private static List<Pattern> routes(String... prefixes) {
        return Arrays.stream(prefixes).distinct().map(DefaultRouteValidator::route).toList();
    }

    private static boolean under(String uri, String prefix) {
        return uri.equals(prefix) || uri.startsWith(prefix + "/");
    }

    // ------------------------------------------------------------------
    // ADMIN endpoints
    // ------------------------------------------------------------------

    public static final List<Pattern> ADMIN_ENDPOINTS = routes(
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

    // ------------------------------------------------------------------
    // Shared / prefixed route patterns
    // ------------------------------------------------------------------

    private static final Pattern PRODUCT_CATALOG   = route("/api/v1/product/private/catalog");
    private static final Pattern PRIVATE_ADDRESSES = route("/api/v1/addresses/private");
    private static final Pattern PRIVATE_CART      = route("/api/v1/cart/private");
    private static final Pattern PRIVATE_MARKETPLACE = route("/api/v1/marketplace/private");
    private static final Pattern ADMIN_MARKETPLACE   = route("/api/v1/marketplace/private/admin");

    public DefaultRouteValidator() {
        log.info("[DRV-INIT] Admin patterns={}", ADMIN_ENDPOINTS.size());
    }

    // ------------------------------------------------------------------
    // CUSTOMER endpoints
    // ------------------------------------------------------------------

    public Predicate<HttpServletRequest> isCustomerSecured = req -> {
        String uri = req.getRequestURI();
        String method = req.getMethod();

        if (uri.equals("/api/v1/user-payments/private")) return true;

        // --- Marketplace: all private customer routes, but not /private/admin ---
        if (under(uri, "/api/v1/marketplace")) {
            return !under(uri, "/api/v1/marketplace/private/admin");
        }

        // --- Product: public + private, but not /private/admin ---
        if (under(uri, "/api/v1/product")) {
            return !under(uri, "/api/v1/product/private/admin");
        }

        if (PRIVATE_ADDRESSES.matcher(uri).matches() || PRIVATE_CART.matcher(uri).matches()) return true;

        if ("GET".equals(method) && PRODUCT_CATALOG.matcher(uri).matches()) return true;

        if ("POST".equals(method) && uri.equals("/api/v1/product/private/catalog/filter")) return true;

        if (under(uri, "/api/v1/commitments/private")) {
            return !under(uri, "/api/v1/commitments/private/admin");
        }

        if (under(uri, "/api/v1/notifications/private")) return true;

        return under(uri, "/api/v1/customer/private")
                && !under(uri, "/api/v1/customer/private/admin");
    };

    // ------------------------------------------------------------------
    // ADMIN gate
    // ------------------------------------------------------------------

    public Predicate<HttpServletRequest> isAdminSecured = req ->
            matches(req, ADMIN_ENDPOINTS);

    private boolean matches(HttpServletRequest request, List<Pattern> patterns) {
        String uri = request.getRequestURI();
        return patterns.stream().anyMatch(p -> p.matcher(uri).matches());
    }
}