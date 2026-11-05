package com.echaris.api.utils.appsecurity;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtFilter extends OncePerRequestFilter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JwtService jwtService;
    private final DefaultRouteValidator routeValidator;
    private final RouteValidator publicRouteValidator;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        log.info("JwtFilter processing request for URI: {}", request.getRequestURI());

        if (publicRouteValidator.isPublic.test(request)) {
            log.info("Bypassing JWT validation for public route: {}", request.getRequestURI());
            chain.doFilter(request, response);
            return;
        }

        Object value = request.getAttribute("validatedClaims");
        if (!(value instanceof Claims claims)) {
            log.info("JWT rejection: No validated claims found for URI: {}", request.getRequestURI());
            reject(response, 401, "Authentication is required");
            return;
        }

        String role = claims.get("userType", String.class);
        String userRole = (role == null || role.isBlank()) ? "UNKNOWN" : role;

        log.info("Evaluating route access for user role: {} on URI: {}", userRole, request.getRequestURI());

        boolean allowed = switch (userRole) {
            case "CUSTOMER" -> routeValidator.isCustomerSecured.test(request);
            case "ADMIN" -> routeValidator.isAdminSecured.test(request);
            default -> false;
        };

        if (!allowed) {
            log.info("JWT rejection: Role {} is not permitted to access URI: {}", userRole, request.getRequestURI());
            reject(response, 403, "Account is not permitted to access this endpoint");
            return;
        }

        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        log.info("Route access rejected. Status: {}, Message: {}", status, message);
        response.setStatus(status);
        response.setContentType("application/json");
        OBJECT_MAPPER.writeValue(response.getWriter(), new ApiResponse<>(false, message, null));
    }
}