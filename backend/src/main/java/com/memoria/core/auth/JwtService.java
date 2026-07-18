package com.memoria.core.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey cleSignature;
    private final Duration dureeValidite;

    public JwtService(
            @Value("${memoria.jwt.secret}") String secret,
            @Value("${memoria.jwt.expiration-heures}") long expirationHeures
    ) {
        this.cleSignature = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.dureeValidite = Duration.ofHours(expirationHeures);
    }

    public String genererToken(Utilisateur utilisateur) {
        Instant maintenant = Instant.now();
        return Jwts.builder()
                .subject(utilisateur.getId().toString())
                .claim("email", utilisateur.getEmail())
                .issuedAt(Date.from(maintenant))
                .expiration(Date.from(maintenant.plus(dureeValidite)))
                .signWith(cleSignature)
                .compact();
    }

    public Optional<UUID> validerEtExtraireUtilisateurId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(cleSignature)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
