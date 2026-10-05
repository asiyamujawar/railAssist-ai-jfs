package com.trainconcierge.auth;

import com.trainconcierge.exception.ErrorCode;
import com.trainconcierge.exception.UnauthorizedException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.expiration-ms}") long jwtExpirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        this.expirationMs = jwtExpirationMs;
    }

    public String generateToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.debug("JWT token is expired");
            throw new UnauthorizedException("JWT token has expired.", ErrorCode.AUTH_TOKEN_EXPIRED);
        } catch (UnsupportedJwtException ex) {
            log.debug("JWT token is unsupported");
            throw new UnauthorizedException("JWT token is unsupported.", ErrorCode.AUTH_TOKEN_INVALID);
        } catch (MalformedJwtException ex) {
            log.debug("JWT token is malformed");
            throw new UnauthorizedException("JWT token is malformed.", ErrorCode.AUTH_TOKEN_INVALID);
        } catch (SignatureException ex) {
            log.debug("JWT signature validation failed");
            throw new UnauthorizedException("JWT signature validation failed.", ErrorCode.AUTH_TOKEN_INVALID);
        } catch (IllegalArgumentException ex) {
            log.debug("JWT claims string is empty");
            throw new UnauthorizedException("JWT claims string is empty.", ErrorCode.AUTH_TOKEN_INVALID);
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
