package com.echaris.api.utils.appsecurity;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Component
public class RouteValidator {

    private static final String PRODUCT_CATALOG = "/api/v1/product/private/catalog";
    private static final Pattern PRODUCT_CATALOG_READ =
            Pattern.compile("^" + PRODUCT_CATALOG + "(?:/[^/]+)?$");

    private static final Pattern PRODUCT_CATEGORY_READ =
            Pattern.compile("^/api/v1/product/private/categories/[^/]+$");

    public static final List<Pattern> openApiEndpoints =
            List.of(
                    Pattern.compile("^/api/v1/commitments/public/paystack/webhook$"),
                    Pattern.compile("^/api/v1/customer/public(?:/.*)?$"),
                    Pattern.compile("^/api/v1/admin/public(?:/.*)?$"),
                    Pattern.compile("^/api/v1/utilities/public(?:/.*)?$"),
                    Pattern.compile("^/api/v1/system/public(?:/.*)?$"),
                    Pattern.compile("^/swagger-ui\\.html$"),
                    Pattern.compile("^/swagger-ui(?:/.*)?$"),
                    Pattern.compile("^/v3/api-docs(?:/.*)?$"),
                    Pattern.compile("^/v3/api-docs\\.yaml$"),
                    Pattern.compile("^/api-docs(?:/.*)?$"),
                    Pattern.compile("^/ws(?:/.*)?$"),
                    Pattern.compile("^/ws-native(?:/.*)?$"),
                    Pattern.compile("^/ws-sockjs(?:/.*)?$")
            );

    public final Predicate<HttpServletRequest> isPublic =
            request -> isPublicProductRead(request.getMethod(), request.getRequestURI())
                    || openApiEndpoints.stream()
                    .anyMatch(pattern -> pattern.matcher(request.getRequestURI()).matches());

    public final Predicate<HttpServletRequest> isSecured =
            request -> !isPublic.test(request);

    static boolean isPublicProductRead(String method, String path) {
        return (("GET".equals(method) || "HEAD".equals(method))
                && (PRODUCT_CATALOG_READ.matcher(path).matches()
                || PRODUCT_CATEGORY_READ.matcher(path).matches()))
                || ("POST".equals(method) && (PRODUCT_CATALOG + "/filter").equals(path));
    }
}