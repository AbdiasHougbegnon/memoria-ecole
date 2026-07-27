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
                .claim("module", utilisateur.getModule().name())
                .claim("admin", utilisateur.estAdmin())
                .issuedAt(Date.from(maintenant))
                .expiration(Date.from(maintenant.plus(dureeValidite)))
                .signWith(cleSignature)
                .compact();
    }

    public record UtilisateurAuthentifie(UUID utilisateurId, ModuleMemoria module, boolean admin) {
    }

    // Le module (et le statut admin) viennent du JWT, pas d'une relecture en
    // base : le filtre reste stateless (aucun appel DB par requete, deja le
    // cas pour l'email). Limite assumee : un changement de module ou de
    // statut admin ne serait pris en compte qu'a la reemission d'un token
    // (jusqu'a 24h) -- pour l'admin, AdminBootstrapRunner promeut au demarrage
    // mais l'utilisateur deja connecte doit se reconnecter pour en beneficier.
    public Optional<UtilisateurAuthentifie> validerEtExtraire(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(cleSignature)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            UUID utilisateurId = UUID.fromString(claims.getSubject());
            ModuleMemoria module = ModuleMemoria.valueOf(claims.get("module", String.class));
            boolean admin = Boolean.TRUE.equals(claims.get("admin", Boolean.class));
            return Optional.of(new UtilisateurAuthentifie(utilisateurId, module, admin));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
