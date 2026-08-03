package com.ticketbox.api.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Component
public class JwtUtils {

    private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);

    @Value("${jwt.access-token}")
    private String accessTokenSecret;

    @Value("${jwt.access-token-expiration}")
    private String accessTokenExpirationRaw;

    @Value("${jwt.refresh-token}")
    private String refreshTokenSecret;

    @Value("${jwt.refresh-token-expiration}")
    private String refreshTokenExpirationRaw;

    private Key accessKey;
    private Key refreshKey;
    private long accessTokenExpiration;
    private long refreshTokenExpiration;

    @PostConstruct
    public void init() {
        this.accessKey = getSecretKey(accessTokenSecret);
        this.refreshKey = getSecretKey(refreshTokenSecret);
        this.accessTokenExpiration = parseExpiration(accessTokenExpirationRaw);
        this.refreshTokenExpiration = parseExpiration(refreshTokenExpirationRaw);
    }

    private Key getSecretKey(String secret) {
        byte[] keyBytes;
        try {
            keyBytes = java.util.HexFormat.of().parseHex(secret);
        } catch (IllegalArgumentException e) {
            try {
                keyBytes = Decoders.BASE64.decode(secret);
            } catch (IllegalArgumentException ex) {
                keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            }
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private long parseExpiration(String expression) {
        if (expression == null || expression.isBlank()) {
            return 3600000L; // default 1 hour
        }
        try {
            return Long.parseLong(expression.trim());
        } catch (NumberFormatException e) {
            if (expression.contains("*")) {
                try {
                    String[] parts = expression.split("\\*");
                    long result = 1;
                    for (String part : parts) {
                        result *= Long.parseLong(part.trim());
                    }
                    return result;
                } catch (NumberFormatException ex) {
                    // Fall back to logging error below
                }
            }
            log.error("Failed to parse token expiration expression: {}", expression, e);
            return 3600000L; // default 1 hour
        }
    }

    public long getAccessTokenExpirationMillis() {
        return accessTokenExpiration;
    }

    public String generateAccessToken(UUID id, String role) {
        String jti = UUID.randomUUID().toString();
        return Jwts.builder()
                .setId(jti)
                .setSubject(id.toString())
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(accessKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(UUID id, String role) {
        String jti = UUID.randomUUID().toString();
        return Jwts.builder()
                .setId(jti)
                .setSubject(id.toString())
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(refreshKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateAccessToken(String token) {
        return validateToken(token, accessKey);
    }

    public boolean validateRefreshToken(String token) {
        return validateToken(token, refreshKey);
    }

    private boolean validateToken(String token, Key key) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT token is unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT token is malformed: {}", e.getMessage());
        } catch (SignatureException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
        }
        return false;
    }

    public UUID extractIdFromAccessToken(String token) {
        return UUID.fromString(extractClaim(token, accessKey, Claims::getSubject));
    }

    public UUID extractIdFromRefreshToken(String token) {
        return UUID.fromString(extractClaim(token, refreshKey, Claims::getSubject));
    }

    public String extractJtiFromAccessToken(String token) {
        return extractClaim(token, accessKey, Claims::getId);
    }

    public String extractJtiFromRefreshToken(String token) {
        return extractClaim(token, refreshKey, Claims::getId);
    }

    public String extractRoleFromAccessToken(String token) {
        return extractClaim(token, accessKey, claims -> claims.get("role", String.class));
    }

    public String extractRoleFromRefreshToken(String token) {
        return extractClaim(token, refreshKey, claims -> claims.get("role", String.class));
    }

    public long getRemainingTtlMillisFromAccessToken(String token) {
        Date expiration = extractClaim(token, accessKey, Claims::getExpiration);
        long diff = expiration.getTime() - System.currentTimeMillis();
        return Math.max(diff, 0);
    }

    public long getRemainingTtlMillisFromRefreshToken(String token) {
        Date expiration = extractClaim(token, refreshKey, Claims::getExpiration);
        long diff = expiration.getTime() - System.currentTimeMillis();
        return Math.max(diff, 0);
    }

    public <T> T extractClaim(String token, Key key, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token, key);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token, Key key) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}