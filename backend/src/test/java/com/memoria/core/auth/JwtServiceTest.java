package com.memoria.core.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "un-secret-de-test-largement-suffisant-32-car";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 24);
    }

    @Test
    void genererToken_produit_un_token_dont_on_peut_extraire_lid_et_le_module_de_lutilisateur() {
        Utilisateur utilisateur = new Utilisateur("alice@memoria.fr", "hash", ModuleMemoria.ENTREPRISE);

        String token = jwtService.genererToken(utilisateur);
        Optional<JwtService.UtilisateurAuthentifie> resultat = jwtService.validerEtExtraire(token);

        assertThat(resultat).isPresent();
        assertThat(resultat.get().utilisateurId()).isEqualTo(utilisateur.getId());
        assertThat(resultat.get().module()).isEqualTo(ModuleMemoria.ENTREPRISE);
    }

    @Test
    void genererToken_propage_le_statut_admin() {
        Utilisateur utilisateur = new Utilisateur("admin@memoria.fr", "hash", ModuleMemoria.ENTREPRISE);
        utilisateur.promouvoirAdmin();

        String token = jwtService.genererToken(utilisateur);
        Optional<JwtService.UtilisateurAuthentifie> resultat = jwtService.validerEtExtraire(token);

        assertThat(resultat).isPresent();
        assertThat(resultat.get().admin()).isTrue();
    }

    @Test
    void genererToken_indique_non_admin_par_defaut() {
        Utilisateur utilisateur = new Utilisateur("alice@memoria.fr", "hash", ModuleMemoria.ENTREPRISE);

        String token = jwtService.genererToken(utilisateur);
        Optional<JwtService.UtilisateurAuthentifie> resultat = jwtService.validerEtExtraire(token);

        assertThat(resultat).isPresent();
        assertThat(resultat.get().admin()).isFalse();
    }

    @Test
    void genererToken_propage_le_module_ecole() {
        Utilisateur utilisateur = new Utilisateur("jean@memoria.fr", "hash", ModuleMemoria.ECOLE);

        String token = jwtService.genererToken(utilisateur);
        Optional<JwtService.UtilisateurAuthentifie> resultat = jwtService.validerEtExtraire(token);

        assertThat(resultat).isPresent();
        assertThat(resultat.get().module()).isEqualTo(ModuleMemoria.ECOLE);
    }

    @Test
    void validerEtExtraire_retourne_vide_pour_un_token_invalide() {
        Optional<JwtService.UtilisateurAuthentifie> resultat = jwtService.validerEtExtraire("token-invalide");

        assertThat(resultat).isEmpty();
    }

    @Test
    void validerEtExtraire_retourne_vide_pour_un_token_signe_avec_un_autre_secret() {
        JwtService autreService = new JwtService("un-autre-secret-completement-different-32car", 24);
        Utilisateur utilisateur = new Utilisateur("alice@memoria.fr", "hash", ModuleMemoria.ENTREPRISE);
        String token = autreService.genererToken(utilisateur);

        Optional<JwtService.UtilisateurAuthentifie> resultat = jwtService.validerEtExtraire(token);

        assertThat(resultat).isEmpty();
    }

    @Test
    void validerEtExtraire_retourne_vide_pour_un_token_expire() {
        JwtService serviceDejaExpire = new JwtService(SECRET, -1);
        Utilisateur utilisateur = new Utilisateur("alice@memoria.fr", "hash", ModuleMemoria.ENTREPRISE);
        String token = serviceDejaExpire.genererToken(utilisateur);

        Optional<JwtService.UtilisateurAuthentifie> resultat = jwtService.validerEtExtraire(token);

        assertThat(resultat).isEmpty();
    }
}
