package com.echaris.api.utils.appsecurity;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest req,
                       HttpServletResponse res,
                       AccessDeniedException ex) throws IOException {
        res.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
        res.setContentType("application/json;charset=UTF-8");

        var body = Map.of(
                "success", false,
                "message", "Forbidden: " + (ex.getMessage() == null ? "Access denied" : ex.getMessage()),
                "path", req.getRequestURI(),
                "timestamp", OffsetDateTime.now().toString()
        );
        System.out.println(ex.getMessage()+" Access Denied Handler called");
        MAPPER.writeValue(res.getOutputStream(), body);
    }
}
