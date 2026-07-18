package com.memoria.core.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "un-secret-de-test-largement-suffisant-32-car";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 24);
    }

    @Test
    void genererToken_produit_un_token_dont_on_peut_extraire_lid_de_lutilisateur() {
        Utilisateur utilisateur = new Utilisateur("alice@memoria.fr", "hash");

        String token = jwtService.genererToken(utilisateur);
        Optional<UUID> idExtrait = jwtService.validerEtExtraireUtilisateurId(token);

        assertThat(idExtrait).contains(utilisateur.getId());
    }

    @Test
    void validerEtExtraireUtilisateurId_retourne_vide_pour_un_token_invalide() {
        Optional<UUID> resultat = jwtService.validerEtExtraireUtilisateurId("token-invalide");

        assertThat(resultat).isEmpty();
    }

    @Test
    void validerEtExtraireUtilisateurId_retourne_vide_pour_un_token_signe_avec_un_autre_secret() {
        JwtService autreService = new JwtService("un-autre-secret-completement-different-32car", 24);
        Utilisateur utilisateur = new Utilisateur("alice@memoria.fr", "hash");
        String token = autreService.genererToken(utilisateur);

        Optional<UUID> resultat = jwtService.validerEtExtraireUtilisateurId(token);

        assertThat(resultat).isEmpty();
    }

    @Test
    void validerEtExtraireUtilisateurId_retourne_vide_pour_un_token_expire() {
        JwtService serviceDejaExpire = new JwtService(SECRET, -1);
        Utilisateur utilisateur = new Utilisateur("alice@memoria.fr", "hash");
        String token = serviceDejaExpire.genererToken(utilisateur);

        Optional<UUID> resultat = jwtService.validerEtExtraireUtilisateurId(token);

        assertThat(resultat).isEmpty();
    }
}
