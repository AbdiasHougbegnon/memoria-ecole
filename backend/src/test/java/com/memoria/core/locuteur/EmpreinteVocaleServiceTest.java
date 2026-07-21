package com.memoria.core.locuteur;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmpreinteVocaleServiceTest {

    @Mock
    private EmpreinteVocaleRepository empreinteVocaleRepository;

    @Mock
    private IdentificateurLocuteurPort identificateur;

    private EmpreinteVocaleService empreinteVocaleService;

    private static final byte[] AUDIO_SUFFISANT = new byte[20_000];

    @BeforeEach
    void setUp() {
        empreinteVocaleService = new EmpreinteVocaleService(empreinteVocaleRepository, identificateur);
    }

    @Test
    void enregistrerConsentement_leve_une_exception_si_le_consentement_est_absent() {
        UUID utilisateurId = UUID.randomUUID();

        assertThatThrownBy(() -> empreinteVocaleService.enregistrerConsentement(utilisateurId, AUDIO_SUFFISANT, false))
                .isInstanceOf(ConsentementRequisException.class);
        verify(identificateur, never()).enroller(any());
    }

    @Test
    void enregistrerConsentement_leve_une_exception_si_laudio_est_trop_court() {
        UUID utilisateurId = UUID.randomUUID();

        assertThatThrownBy(() -> empreinteVocaleService.enregistrerConsentement(utilisateurId, new byte[10], true))
                .isInstanceOf(AudioEnrollementInsuffisantException.class);
        verify(identificateur, never()).enroller(any());
    }

    @Test
    void enregistrerConsentement_enrole_et_marque_prete_si_azure_reussit() {
        UUID utilisateurId = UUID.randomUUID();
        when(empreinteVocaleRepository.findByUtilisateurId(utilisateurId)).thenReturn(Optional.empty());
        when(identificateur.enroller(AUDIO_SUFFISANT)).thenReturn("profil-externe-123");
        when(empreinteVocaleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EmpreinteVocaleResponse resultat = empreinteVocaleService.enregistrerConsentement(utilisateurId, AUDIO_SUFFISANT, true);

        assertThat(resultat.statut()).isEqualTo(StatutEmpreinteVocale.PRETE);
    }

    @Test
    void enregistrerConsentement_marque_echec_sans_lever_dexception_si_azure_echoue() {
        UUID utilisateurId = UUID.randomUUID();
        when(empreinteVocaleRepository.findByUtilisateurId(utilisateurId)).thenReturn(Optional.empty());
        when(identificateur.enroller(AUDIO_SUFFISANT)).thenThrow(new IdentificationLocuteurException("Azure indisponible"));
        when(empreinteVocaleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EmpreinteVocaleResponse resultat = empreinteVocaleService.enregistrerConsentement(utilisateurId, AUDIO_SUFFISANT, true);

        assertThat(resultat.statut()).isEqualTo(StatutEmpreinteVocale.ECHEC);
    }

    @Test
    void enregistrerConsentement_supprime_lancien_profil_lors_dun_reenrolement() {
        UUID utilisateurId = UUID.randomUUID();
        EmpreinteVocale ancienne = new EmpreinteVocale(utilisateurId);
        ancienne.marquerPrete("ancien-profil");
        when(empreinteVocaleRepository.findByUtilisateurId(utilisateurId)).thenReturn(Optional.of(ancienne));
        when(identificateur.enroller(AUDIO_SUFFISANT)).thenReturn("nouveau-profil");
        when(empreinteVocaleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        empreinteVocaleService.enregistrerConsentement(utilisateurId, AUDIO_SUFFISANT, true);

        verify(identificateur).supprimerProfil("ancien-profil");
        verify(empreinteVocaleRepository).delete(ancienne);
    }

    @Test
    void obtenirStatut_renvoie_absente_si_aucune_empreinte() {
        UUID utilisateurId = UUID.randomUUID();
        when(empreinteVocaleRepository.findByUtilisateurId(utilisateurId)).thenReturn(Optional.empty());

        EmpreinteVocaleResponse resultat = empreinteVocaleService.obtenirStatut(utilisateurId);

        assertThat(resultat.statut()).isNull();
    }

    @Test
    void revoquer_supprime_lempreinte_meme_si_la_suppression_distante_echoue() {
        UUID utilisateurId = UUID.randomUUID();
        EmpreinteVocale empreinte = new EmpreinteVocale(utilisateurId);
        empreinte.marquerPrete("profil-123");
        when(empreinteVocaleRepository.findByUtilisateurId(utilisateurId)).thenReturn(Optional.of(empreinte));
        org.mockito.Mockito.doThrow(new IdentificationLocuteurException("indisponible"))
                .when(identificateur).supprimerProfil("profil-123");

        empreinteVocaleService.revoquer(utilisateurId);

        verify(empreinteVocaleRepository).delete(empreinte);
    }

    @Test
    void revoquer_ne_fait_rien_si_aucune_empreinte() {
        UUID utilisateurId = UUID.randomUUID();
        when(empreinteVocaleRepository.findByUtilisateurId(utilisateurId)).thenReturn(Optional.empty());

        empreinteVocaleService.revoquer(utilisateurId);

        verify(empreinteVocaleRepository, never()).delete(any());
    }
}
