package com.memoria.core.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(utilisateurRepository, passwordEncoder, jwtService, "");
    }

    @Test
    void inscrire_cree_un_utilisateur_avec_le_mot_de_passe_hashe_et_retourne_un_token() {
        when(utilisateurRepository.existsByEmail("alice@memoria.fr")).thenReturn(false);
        when(passwordEncoder.encode("motdepasse123")).thenReturn("hash-secret");
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.genererToken(any(Utilisateur.class))).thenReturn("un-jwt");

        AuthResponse reponse = authService.inscrire("alice@memoria.fr", "motdepasse123", ModuleMemoria.ENTREPRISE);

        ArgumentCaptor<Utilisateur> captor = ArgumentCaptor.forClass(Utilisateur.class);
        org.mockito.Mockito.verify(utilisateurRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("alice@memoria.fr");
        assertThat(captor.getValue().getMotDePasseHash()).isEqualTo("hash-secret");
        assertThat(reponse.token()).isEqualTo("un-jwt");
        assertThat(reponse.email()).isEqualTo("alice@memoria.fr");
    }

    @Test
    void inscrire_stocke_le_module_choisi() {
        when(utilisateurRepository.existsByEmail("jean@memoria.fr")).thenReturn(false);
        when(passwordEncoder.encode("motdepasse123")).thenReturn("hash-secret");
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.genererToken(any(Utilisateur.class))).thenReturn("un-jwt");

        AuthResponse reponse = authService.inscrire("jean@memoria.fr", "motdepasse123", ModuleMemoria.ECOLE);

        ArgumentCaptor<Utilisateur> captor = ArgumentCaptor.forClass(Utilisateur.class);
        org.mockito.Mockito.verify(utilisateurRepository).save(captor.capture());
        assertThat(captor.getValue().getModule()).isEqualTo(ModuleMemoria.ECOLE);
        assertThat(reponse.module()).isEqualTo(ModuleMemoria.ECOLE);
    }

    @Test
    void inscrire_leve_une_exception_si_lemail_est_deja_utilise() {
        when(utilisateurRepository.existsByEmail("alice@memoria.fr")).thenReturn(true);

        assertThatThrownBy(() -> authService.inscrire("alice@memoria.fr", "motdepasse123", ModuleMemoria.ENTREPRISE))
                .isInstanceOf(EmailDejaUtiliseException.class);
    }

    @Test
    void connecter_retourne_un_token_quand_les_identifiants_sont_corrects() {
        Utilisateur utilisateur = new Utilisateur("alice@memoria.fr", "hash-secret", ModuleMemoria.ENTREPRISE);
        when(utilisateurRepository.findByEmail("alice@memoria.fr")).thenReturn(Optional.of(utilisateur));
        when(passwordEncoder.matches("motdepasse123", "hash-secret")).thenReturn(true);
        when(jwtService.genererToken(utilisateur)).thenReturn("un-jwt");

        AuthResponse reponse = authService.connecter("alice@memoria.fr", "motdepasse123");

        assertThat(reponse.token()).isEqualTo("un-jwt");
        assertThat(reponse.utilisateurId()).isEqualTo(utilisateur.getId());
        assertThat(reponse.module()).isEqualTo(ModuleMemoria.ENTREPRISE);
    }

    @Test
    void connecter_leve_une_exception_si_lemail_est_inconnu() {
        when(utilisateurRepository.findByEmail("inconnu@memoria.fr")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.connecter("inconnu@memoria.fr", "motdepasse123"))
                .isInstanceOf(IdentifiantsInvalidesException.class);
    }

    @Test
    void connecter_leve_une_exception_si_le_mot_de_passe_est_incorrect() {
        Utilisateur utilisateur = new Utilisateur("alice@memoria.fr", "hash-secret", ModuleMemoria.ENTREPRISE);
        when(utilisateurRepository.findByEmail("alice@memoria.fr")).thenReturn(Optional.of(utilisateur));
        when(passwordEncoder.matches("mauvais-mot-de-passe", "hash-secret")).thenReturn(false);

        assertThatThrownBy(() -> authService.connecter("alice@memoria.fr", "mauvais-mot-de-passe"))
                .isInstanceOf(IdentifiantsInvalidesException.class);
    }

    @Test
    void inscrire_accepte_nimporte_quel_domaine_quand_la_restriction_est_desactivee() {
        when(utilisateurRepository.existsByEmail("alice@gmail.com")).thenReturn(false);
        when(passwordEncoder.encode("motdepasse123")).thenReturn("hash-secret");
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.genererToken(any(Utilisateur.class))).thenReturn("un-jwt");

        AuthResponse reponse = authService.inscrire("alice@gmail.com", "motdepasse123", ModuleMemoria.ENTREPRISE);

        assertThat(reponse.email()).isEqualTo("alice@gmail.com");
    }

    @Test
    void inscrire_accepte_un_email_du_domaine_autorise() {
        AuthService authServiceRestreint = new AuthService(utilisateurRepository, passwordEncoder, jwtService, "episen.fr, etu.episen.fr");
        when(utilisateurRepository.existsByEmail("alice@episen.fr")).thenReturn(false);
        when(passwordEncoder.encode("motdepasse123")).thenReturn("hash-secret");
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.genererToken(any(Utilisateur.class))).thenReturn("un-jwt");

        AuthResponse reponse = authServiceRestreint.inscrire("alice@episen.fr", "motdepasse123", ModuleMemoria.ECOLE);

        assertThat(reponse.email()).isEqualTo("alice@episen.fr");
    }

    @Test
    void inscrire_accepte_un_domaine_autorise_sans_tenir_compte_de_la_casse() {
        AuthService authServiceRestreint = new AuthService(utilisateurRepository, passwordEncoder, jwtService, "episen.fr");
        when(utilisateurRepository.existsByEmail("alice@EPISEN.fr")).thenReturn(false);
        when(passwordEncoder.encode("motdepasse123")).thenReturn("hash-secret");
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.genererToken(any(Utilisateur.class))).thenReturn("un-jwt");

        AuthResponse reponse = authServiceRestreint.inscrire("alice@EPISEN.fr", "motdepasse123", ModuleMemoria.ECOLE);

        assertThat(reponse.email()).isEqualTo("alice@EPISEN.fr");
    }

    @Test
    void inscrire_leve_une_exception_si_le_domaine_nest_pas_autorise() {
        AuthService authServiceRestreint = new AuthService(utilisateurRepository, passwordEncoder, jwtService, "episen.fr");

        assertThatThrownBy(() -> authServiceRestreint.inscrire("alice@gmail.com", "motdepasse123", ModuleMemoria.ECOLE))
                .isInstanceOf(DomaineEmailNonAutoriseException.class);
    }
}
