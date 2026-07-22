package com.memoria.core.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;

    public SecurityConfig(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()
                        // Flux mobile QR code (Phase 2) : une personne scanne un QR code
                        // avec son telephone, sans jamais se connecter. La securite de ce
                        // flux repose sur la confidentialite de l'UUID de session, pas sur
                        // un compte utilisateur - c'est un choix de conception assume, pas
                        // un oubli.
                        .requestMatchers(HttpMethod.GET, "/api/v1/sessions/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/sessions/*/documents").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/sessions/*/documents").permitAll()

                        // Entreprise uniquement -- le moteur commun (sessions, couloirs,
                        // recherche, fils de memoire, transcriptions, resumes generiques)
                        // reste accessible aux deux modules via anyRequest().authenticated().
                        .requestMatchers("/api/v1/engagements/**").hasAuthority("MODULE_ENTREPRISE")
                        .requestMatchers("/api/v1/entreprise/**").hasAuthority("MODULE_ENTREPRISE")
                        .requestMatchers("/api/v1/sessions/*/engagements").hasAuthority("MODULE_ENTREPRISE")
                        .requestMatchers("/api/v1/sessions/*/compte-rendu").hasAuthority("MODULE_ENTREPRISE")

                        // Ecole uniquement
                        .requestMatchers("/api/v1/sessions/*/resume-cours").hasAuthority("MODULE_ECOLE")

                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
