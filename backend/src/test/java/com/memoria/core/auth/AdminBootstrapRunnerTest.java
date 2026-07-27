package com.memoria.core.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Test
    void run_promeut_un_compte_existant_liste_dans_la_config() {
        Utilisateur utilisateur = new Utilisateur("admin@memoria.fr", "hash", ModuleMemoria.ENTREPRISE);
        when(utilisateurRepository.findByEmailIgnoreCase("admin@memoria.fr")).thenReturn(Optional.of(utilisateur));
        AdminBootstrapRunner runner = new AdminBootstrapRunner(utilisateurRepository, "admin@memoria.fr");

        runner.run(null);

        assertThat(utilisateur.estAdmin()).isTrue();
        verify(utilisateurRepository).save(utilisateur);
    }

    @Test
    void run_ne_fait_rien_si_le_compte_est_deja_admin() {
        Utilisateur utilisateur = new Utilisateur("admin@memoria.fr", "hash", ModuleMemoria.ENTREPRISE);
        utilisateur.promouvoirAdmin();
        when(utilisateurRepository.findByEmailIgnoreCase("admin@memoria.fr")).thenReturn(Optional.of(utilisateur));
        AdminBootstrapRunner runner = new AdminBootstrapRunner(utilisateurRepository, "admin@memoria.fr");

        runner.run(null);

        verify(utilisateurRepository, never()).save(utilisateur);
    }

    @Test
    void run_ne_fait_rien_si_aucun_compte_ne_correspond_encore() {
        when(utilisateurRepository.findByEmailIgnoreCase("futur-admin@memoria.fr")).thenReturn(Optional.empty());
        AdminBootstrapRunner runner = new AdminBootstrapRunner(utilisateurRepository, "futur-admin@memoria.fr");

        runner.run(null);

        verify(utilisateurRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void run_ne_leve_rien_quand_la_liste_est_vide() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(utilisateurRepository, "");

        runner.run(null);

        verify(utilisateurRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
