package com.memoria.core.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

// Volontairement pas un @Component : instancie a la main dans SecurityConfig et
// branche uniquement dans la chaine Spring Security. En bean autonome, Spring
// Boot l'enregistrerait aussi comme filtre servlet global (hors chaine Security),
// ce qui l'aurait execute deux fois par requete.
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIXE_BEARER = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String enTete = request.getHeader("Authorization");
        if (enTete != null && enTete.startsWith(PREFIXE_BEARER)) {
            String token = enTete.substring(PREFIXE_BEARER.length());
            Optional<JwtService.UtilisateurAuthentifie> utilisateurAuthentifie = jwtService.validerEtExtraire(token);
            utilisateurAuthentifie.ifPresent(u -> {
                var authorities = List.of(new SimpleGrantedAuthority("MODULE_" + u.module().name()));
                var authentication = new UsernamePasswordAuthenticationToken(u.utilisateurId(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        filterChain.doFilter(request, response);
    }
}
