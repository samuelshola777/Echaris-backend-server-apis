package com.echaris.api.utils.appsecurity;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;


@Component
public class RestAuthEntryPoint implements AuthenticationEntryPoint {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest req,
                         HttpServletResponse res,
                         AuthenticationException ex) throws IOException {
        res.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 401
        res.setContentType("application/json;charset=UTF-8");
        // Optional: hint for clients
        res.setHeader("WWW-Authenticate", "Bearer");

        var body = Map.of(
                "success", false,
                "message", "Oops! Not you ",
                "path", req.getRequestURI(),
                "timestamp", OffsetDateTime.now().toString()
        );

        MAPPER.writeValue(res.getOutputStream(), body);
    }
}
