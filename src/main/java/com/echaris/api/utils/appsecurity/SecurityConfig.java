package com.echaris.api.utils.appsecurity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Slf4j
public class SecurityConfig {

    private final ApiSecurityFilter apiSecurityFilter;
    private final JwtFilter jwtFilter;
    private final RestAuthEntryPoint authEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            ApiSecurityFilter apiSecurityFilter,
            JwtFilter jwtFilter,
            RestAuthEntryPoint authEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) {
        this.apiSecurityFilter = apiSecurityFilter;
        this.jwtFilter = jwtFilter;
        this.authEntryPoint = authEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;

        log.info(
                "SecurityConfig dependencies initialized"
        );
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {
        log.info(
                "Configuring Spring Security Filter Chain"
        );

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors ->
                        cors.configurationSource(
                                corsConfigurationSource()
                        )
                )
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .exceptionHandling(eh ->
                        eh.authenticationEntryPoint(authEntryPoint)
                                .accessDeniedHandler(
                                        accessDeniedHandler
                                )
                )
                .authorizeHttpRequests(auth ->
                        auth.requestMatchers(
                                        HttpMethod.OPTIONS,
                                        "/**"
                                )
                                .permitAll()
                                .requestMatchers(
                                        "/api/v1/commitments/public/paystack/webhook",
                                        "/api/v1/utilities/public/**",
                                        "/api/v1/utilities/public",
                                        "/swagger-ui.html",
                                        "/swagger-ui/**",
                                        "/api-docs/**",
                                        "/v3/api-docs/**",
                                        "/v3/api-docs",
                                        "/v3/api-docs.yaml",
                                        "/api/v1/admin/public",
                                        "/api/v1/admin/public/**",
                                        "/api/v1/customer/public",
                                        "/api/v1/customer/public/**",
                                        "/ws",
                                        "/ws/**",
                                        "/ws-native",
                                        "/ws-native/**",
                                        "/ws-sockjs",
                                        "/ws-sockjs/**",
                                        "/api/v1/admin/auth/public/**",
                                        "/api/v1/admin/auth/public/forgot-password",
                                        "/api/v1/admin/auth/public/reset-password"
                                )
                                .permitAll()
                                .anyRequest()
                                .authenticated()
                )
                .addFilterBefore(
                        apiSecurityFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .addFilterAfter(
                        jwtFilter,
                        ApiSecurityFilter.class
                );

        SecurityFilterChain chain = http.build();

        log.info(
                "SecurityFilterChain built successfully: Stateless, CSRF disabled, Public/Protected routes configured"
        );

        return chain;
    }

    @Bean
    public FilterRegistrationBean<ApiSecurityFilter>
    apiSecurityRegistration() {
        var registration =
                new FilterRegistrationBean<>(
                        apiSecurityFilter
                );

        registration.setEnabled(false);

        return registration;
    }

    @Bean
    public FilterRegistrationBean<JwtFilter>
    jwtRegistration() {
        var registration =
                new FilterRegistrationBean<>(
                        jwtFilter
                );

        registration.setEnabled(false);

        return registration;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration cfg
    ) throws Exception {
        log.info(
                "Initializing AuthenticationManager bean"
        );

        return cfg.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource
    corsConfigurationSource() {
        log.info(
                "Configuring CORS rules for API and WebSocket endpoints"
        );

        CorsConfiguration apiCors =
                new CorsConfiguration();

        apiCors.setAllowedOriginPatterns(
                Arrays.asList("*")
        );

        apiCors.setAllowedMethods(
                Arrays.asList(
                        "GET",
                        "POST",
                        "PUT",
                        "DELETE",
                        "OPTIONS",
                        "PATCH"
                )
        );

        apiCors.setAllowedHeaders(
                Arrays.asList("*")
        );

        apiCors.setAllowCredentials(true);

        CorsConfiguration wsCors =
                new CorsConfiguration();

        wsCors.setAllowedOriginPatterns(
                Arrays.asList("*")
        );

        wsCors.setAllowedMethods(
                Arrays.asList("GET", "OPTIONS")
        );

        wsCors.setAllowedHeaders(
                Arrays.asList("*")
        );

        wsCors.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource src =
                new UrlBasedCorsConfigurationSource();

        src.registerCorsConfiguration(
                "/api/**",
                apiCors
        );

        src.registerCorsConfiguration(
                "/ws/**",
                wsCors
        );

        src.registerCorsConfiguration(
                "/ws-sockjs/**",
                wsCors
        );

        return src;
    }
}