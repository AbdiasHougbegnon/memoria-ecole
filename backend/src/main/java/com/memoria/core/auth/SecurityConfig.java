package com.memoria.core.auth;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
@SuppressFBWarnings(
        value = "SPRING_CSRF_PROTECTION_DISABLED",
        justification = "API stateless (SessionCreationPolicy.STATELESS, authentification par JWT "
                + "Bearer, aucun cookie de session) -- CSRF exploite l'envoi automatique de cookies "
                + "par le navigateur, non applicable a une API sans etat consommee via un header "
                + "Authorization explicite. Pratique standard Spring Security pour les APIs "
                + "non-navigateur (cf. documentation officielle Spring Security sur CSRF). Annotation "
                + "posee au niveau classe (pas sur la methode filterChain) : le csrf.disable() vit "
                + "dans un lambda, compile en methode synthetique separee que SpotBugs n'associe pas "
                + "aux annotations de la methode englobante -- seule une annotation de classe couvre "
                + "cette methode generee."
)
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
                        // Sonde de sante Docker/orchestrateur (pas de compte pour verifier
                        // qu'un conteneur repond).
                        .requestMatchers("/actuator/health").permitAll()
                        // Scraping Prometheus : meme raisonnement que /actuator/health --
                        // nginx (frontend) ne relaie que /api/*, jamais /actuator/*, donc
                        // ce endpoint n'est atteignable que depuis le reseau Docker interne
                        // (par prometheus). C'est la frontiere reseau qui protege, pas
                        // l'authentification applicative -- coherent avec speaker-service,
                        // jamais publie vers l'hote non plus. Tout AUTRE endpoint actuator
                        // (env, beans, mappings...) reste couvert par anyRequest().authenticated()
                        // ci-dessous puisqu'il n'est pas expose (management.endpoints.web.exposure.include
                        // ne liste que health,prometheus).
                        .requestMatchers("/actuator/prometheus").permitAll()
                        // Flux mobile QR code (Phase 2) : une personne scanne un QR code
                        // avec son telephone, sans jamais se connecter. La securite de ce
                        // flux repose sur la confidentialite de l'UUID de session, pas sur
                        // un compte utilisateur - c'est un choix de conception assume, pas
                        // un oubli.
                        .requestMatchers(HttpMethod.GET, "/api/v1/sessions/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/sessions/*/documents").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/sessions/*/documents").permitAll()
                        // Inscription Ecole (phase-17b) : un futur etudiant n'a pas encore
                        // de compte, donc pas de JWT -- ces deux endpoints doivent rester
                        // accessibles avant authentification, comme /api/v1/auth/**.
                        .requestMatchers(HttpMethod.GET, "/api/v1/ecole/options-inscription").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/ecole/inscription").permitAll()

                        // Role admin (phase 20) : effacement RGPD au nom d'autrui + consultation
                        // du journal RGPD -- accorde uniquement via memoria.admin.emails-autorises
                        // (voir AuthService/AdminBootstrapRunner), jamais en libre-service.
                        .requestMatchers("/api/v1/admin/**").hasAuthority("ROLE_ADMIN")

                        // Entreprise uniquement -- le moteur commun (sessions, couloirs,
                        // recherche, fils de memoire, transcriptions, resumes generiques)
                        // reste accessible aux deux modules via anyRequest().authenticated().
                        .requestMatchers("/api/v1/engagements/**").hasAuthority("MODULE_ENTREPRISE")
                        .requestMatchers("/api/v1/entreprise/**").hasAuthority("MODULE_ENTREPRISE")
                        .requestMatchers("/api/v1/sessions/*/engagements").hasAuthority("MODULE_ENTREPRISE")
                        .requestMatchers("/api/v1/sessions/*/compte-rendu").hasAuthority("MODULE_ENTREPRISE")

                        // Ecole uniquement
                        .requestMatchers("/api/v1/sessions/*/resume-cours/**").hasAuthority("MODULE_ECOLE")
                        .requestMatchers("/api/v1/matieres/**").hasAuthority("MODULE_ECOLE")
                        .requestMatchers("/api/v1/couloirs/*/matieres").hasAuthority("MODULE_ECOLE")
                        .requestMatchers("/api/v1/seances/**").hasAuthority("MODULE_ECOLE")
                        .requestMatchers("/api/v1/tutorat/**").hasAuthority("MODULE_ECOLE")
                        .requestMatchers("/api/v1/ecole/**").hasAuthority("MODULE_ECOLE")

                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
