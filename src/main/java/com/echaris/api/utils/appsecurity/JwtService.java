package com.echaris.api.utils.appsecurity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
@Slf4j
public class JwtService {

    @Value("${app.jwt-secret}")
    private String SECRET_KEY;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    public String generateToken(String username, Map<String, Object> extraClaims) {
        return generateToken(extraClaims, username);
    }

    public String generateToken(Map<String, Object> extraClaims, String username) {
        log.info("Generating access token for user: {}", username);
        Map<String, Object> claims = new HashMap<>(extraClaims);
        claims.put("type", "ACCESS_TOKEN");

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24)) // 24 hours
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(String username, Map<String, Object> extraClaims) {
        log.info("Generating refresh token for user: {}", username);
        Map<String, Object> claims = new HashMap<>(extraClaims);
        claims.put("type", "REFRESH_TOKEN");

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(Date.from(LocalDate.now().plusDays(7)
                        .atStartOfDay(ZoneId.systemDefault()).toInstant())) // 7 days
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        return isTokenValid(token) && userDetails != null
                && extractUsername(token).equals(userDetails.getUsername());
    }

    public boolean isTokenValid(String token) {
        try {
            parse(token);
            return true;
        } catch (RuntimeException exception) {
            log.info("Access token rejected. Reason: {}", exception.getMessage());
            return false;
        }
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public Claims parse(String token) {
        Claims claims = extractAllClaims(token);

        if (claims.getExpiration() == null || !claims.getExpiration().after(new Date())
                || claims.getSubject() == null || claims.getSubject().isBlank()
                || !"ACCESS_TOKEN".equals(claims.get("type", String.class))) {
            log.info("Token validation failed: Token is expired, malformed, or not an ACCESS_TOKEN");
            throw new SecurityException("Invalid access token");
        }

        log.info("Access token validated successfully for user: {}", claims.getSubject());
        return claims;
    }

    private Key getSignInKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET_KEY));
    }
}