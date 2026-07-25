package com.memoria.core.gouvernance;

import com.memoria.core.auth.ModuleMemoria;
import com.memoria.core.auth.Utilisateur;
import com.memoria.core.auth.UtilisateurNotFoundException;
import com.memoria.core.auth.UtilisateurRepository;
import com.memoria.core.couloir.Couloir;
import com.memoria.core.couloir.CouloirRepository;
import com.memoria.core.couloir.CouloirService;
import com.memoria.core.couloir.MembreCouloir;
import com.memoria.core.couloir.MembreCouloirRepository;
import com.memoria.core.locuteur.EmpreinteVocaleRepository;
import com.memoria.core.locuteur.EmpreinteVocaleService;
import com.memoria.core.resume.ResumeRepository;
import com.memoria.core.session.Session;
import com.memoria.core.session.SessionRepository;
import com.memoria.core.transcription.TranscriptionRepository;
import com.memoria.ecole.notion.MaitriseNotionRepository;
import com.memoria.ecole.resumecours.ResumeCoursRepository;
import com.memoria.ecole.tuteurvocal.SeanceTutoratRepository;
import com.memoria.ecole.tuteurvocal.TourDialogueTutoratRepository;
import com.memoria.entreprise.compterendu.CompteRenduRepository;
import com.memoria.entreprise.engagement.EngagementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GouvernanceDonneesServiceTest {

    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private EmpreinteVocaleService empreinteVocaleService;
    @Mock private EmpreinteVocaleRepository empreinteVocaleRepository;
    @Mock private SeanceTutoratRepository seanceTutoratRepository;
    @Mock private TourDialogueTutoratRepository tourDialogueTutoratRepository;
    @Mock private MaitriseNotionRepository maitriseNotionRepository;
    @Mock private CouloirRepository couloirRepository;
    @Mock private CouloirService couloirService;
    @Mock private MembreCouloirRepository membreCouloirRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private EngagementRepository engagementRepository;
    @Mock private TranscriptionRepository transcriptionRepository;
    @Mock private ResumeRepository resumeRepository;
    @Mock private CompteRenduRepository compteRenduRepository;
    @Mock private ResumeCoursRepository resumeCoursRepository;
    @Mock private SessionPurgeService sessionPurgeService;
    @Mock private JournalRgpdRepository journalRgpdRepository;

    private GouvernanceDonneesService gouvernanceDonneesService;

    @BeforeEach
    void setUp() {
        gouvernanceDonneesService = new GouvernanceDonneesService(
                utilisateurRepository, empreinteVocaleService, empreinteVocaleRepository, seanceTutoratRepository,
                tourDialogueTutoratRepository, maitriseNotionRepository, couloirRepository, couloirService,
                membreCouloirRepository, sessionRepository, engagementRepository, transcriptionRepository,
                resumeRepository, compteRenduRepository, resumeCoursRepository, sessionPurgeService, journalRgpdRepository
        );
    }

    @Test
    void effacerCompte_leve_une_exception_si_lutilisateur_est_introuvable() {
        UUID idInconnu = UUID.randomUUID();
        when(utilisateurRepository.existsById(idInconnu)).thenReturn(false);

        assertThatThrownBy(() -> gouvernanceDonneesService.effacerCompte(idInconnu))
                .isInstanceOf(UtilisateurNotFoundException.class);
    }

    @Test
    void effacerCompte_revoque_lempreinte_vocale_et_les_donnees_personnelles_exclusives() {
        UUID utilisateurId = UUID.randomUUID();
        when(utilisateurRepository.existsById(utilisateurId)).thenReturn(true);
        when(seanceTutoratRepository.findByUtilisateurId(utilisateurId)).thenReturn(List.of());
        when(couloirRepository.findByProprietaireId(utilisateurId)).thenReturn(List.of());
        when(sessionRepository.findByCreateurId(utilisateurId)).thenReturn(List.of());

        gouvernanceDonneesService.effacerCompte(utilisateurId);

        verify(empreinteVocaleService).revoquer(utilisateurId);
        verify(maitriseNotionRepository).deleteByUtilisateurId(utilisateurId);
        verify(membreCouloirRepository).deleteByUtilisateurId(utilisateurId);
        verify(engagementRepository).anonymiserResponsable(utilisateurId);
        verify(transcriptionRepository).anonymiserSegmentsLocuteur(utilisateurId);
        verify(utilisateurRepository).deleteById(utilisateurId);
    }

    @Test
    void effacerCompte_transfere_la_propriete_dun_couloir_au_membre_le_plus_ancien() {
        UUID utilisateurId = UUID.randomUUID();
        UUID membreRecentId = UUID.randomUUID();
        UUID membreAncienId = UUID.randomUUID();
        Couloir couloir = new Couloir("Ing1-SI", utilisateurId);
        UUID couloirId = couloir.getId();

        MembreCouloir membreRecent = membreAvecDate(membreRecentId, Instant.now());
        MembreCouloir membreAncien = membreAvecDate(membreAncienId, Instant.now().minusSeconds(3600));
        MembreCouloir proprietaire = membreProprietaire(utilisateurId);

        when(utilisateurRepository.existsById(utilisateurId)).thenReturn(true);
        when(seanceTutoratRepository.findByUtilisateurId(utilisateurId)).thenReturn(List.of());
        when(couloirRepository.findByProprietaireId(utilisateurId)).thenReturn(List.of(couloir));
        when(membreCouloirRepository.findByCouloirId(couloirId)).thenReturn(List.of(membreRecent, membreAncien, proprietaire));
        when(sessionRepository.findByCreateurId(utilisateurId)).thenReturn(List.of());

        gouvernanceDonneesService.effacerCompte(utilisateurId);

        verify(couloirService).transfererPropriete(couloirId, membreAncienId, utilisateurId);
        verify(couloirService, never()).supprimerCouloir(any(), any());
    }

    @Test
    void effacerCompte_supprime_le_couloir_si_lutilisateur_en_est_lunique_membre() {
        UUID utilisateurId = UUID.randomUUID();
        Couloir couloir = new Couloir("Solo", utilisateurId);
        UUID couloirId = couloir.getId();
        MembreCouloir proprietaire = membreProprietaire(utilisateurId);

        when(utilisateurRepository.existsById(utilisateurId)).thenReturn(true);
        when(seanceTutoratRepository.findByUtilisateurId(utilisateurId)).thenReturn(List.of());
        when(couloirRepository.findByProprietaireId(utilisateurId)).thenReturn(List.of(couloir));
        when(membreCouloirRepository.findByCouloirId(couloirId)).thenReturn(List.of(proprietaire));
        when(sessionRepository.findByCreateurId(utilisateurId)).thenReturn(List.of());

        gouvernanceDonneesService.effacerCompte(utilisateurId);

        verify(couloirService).supprimerCouloir(couloirId, utilisateurId);
        verify(couloirService, never()).transfererPropriete(any(), any(), any());
    }

    @Test
    void effacerCompte_collecte_les_sessions_personnelles_et_anonymise_les_sessions_partagees() {
        UUID utilisateurId = UUID.randomUUID();
        UUID couloirId = UUID.randomUUID();
        Session personnelle = new Session("Notes perso", utilisateurId, null);
        Session partagee = new Session("Cours", utilisateurId, couloirId);

        when(utilisateurRepository.existsById(utilisateurId)).thenReturn(true);
        when(seanceTutoratRepository.findByUtilisateurId(utilisateurId)).thenReturn(List.of());
        when(couloirRepository.findByProprietaireId(utilisateurId)).thenReturn(List.of());
        when(sessionRepository.findByCreateurId(utilisateurId)).thenReturn(List.of(personnelle, partagee));

        List<UUID> sessionsAPurger = gouvernanceDonneesService.effacerCompte(utilisateurId);

        assertThat(sessionsAPurger).containsExactly(personnelle.getId());
        assertThat(partagee.getCreateurId()).isNull();
        verify(sessionRepository).save(partagee);
        verify(sessionRepository, never()).save(personnelle);
    }

    @Test
    void finaliserEffacement_purge_chaque_session_et_journalise_une_seule_entree() {
        UUID utilisateurId = UUID.randomUUID();
        UUID sessionId1 = UUID.randomUUID();
        UUID sessionId2 = UUID.randomUUID();

        gouvernanceDonneesService.finaliserEffacement(utilisateurId, List.of(sessionId1, sessionId2));

        verify(sessionPurgeService).purgerSessionCompletement(sessionId1);
        verify(sessionPurgeService).nettoyerDependancesExternes(sessionId1);
        verify(sessionPurgeService).purgerSessionCompletement(sessionId2);
        verify(sessionPurgeService).nettoyerDependancesExternes(sessionId2);

        ArgumentCaptor<JournalRgpd> captor = ArgumentCaptor.forClass(JournalRgpd.class);
        verify(journalRgpdRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(TypeActionRgpd.EFFACEMENT_COMPTE);
        assertThat(captor.getValue().getUtilisateurCibleId()).isEqualTo(utilisateurId);
    }

    @Test
    void finaliserEffacement_continue_apres_lechec_de_purge_dune_session() {
        UUID utilisateurId = UUID.randomUUID();
        UUID sessionEnErreur = UUID.randomUUID();
        UUID sessionOk = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new RuntimeException("base indisponible"))
                .when(sessionPurgeService).purgerSessionCompletement(sessionEnErreur);

        gouvernanceDonneesService.finaliserEffacement(utilisateurId, List.of(sessionEnErreur, sessionOk));

        verify(sessionPurgeService, never()).nettoyerDependancesExternes(sessionEnErreur);
        verify(sessionPurgeService).purgerSessionCompletement(sessionOk);
        verify(sessionPurgeService).nettoyerDependancesExternes(sessionOk);
    }

    @Test
    void exporterDonnees_leve_une_exception_si_lutilisateur_est_introuvable() {
        UUID idInconnu = UUID.randomUUID();
        when(utilisateurRepository.findById(idInconnu)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gouvernanceDonneesService.exporterDonnees(idInconnu))
                .isInstanceOf(UtilisateurNotFoundException.class);
    }

    @Test
    void exporterDonnees_assemble_le_profil_et_nexpose_pas_le_profil_externe_vocal() {
        UUID utilisateurId = UUID.randomUUID();
        Utilisateur utilisateur = new Utilisateur("jean@test.fr", "hash", ModuleMemoria.ENTREPRISE);
        when(utilisateurRepository.findById(utilisateurId)).thenReturn(Optional.of(utilisateur));
        when(sessionRepository.findByCreateurId(utilisateurId)).thenReturn(List.of());
        when(engagementRepository.findByResponsableUtilisateurId(utilisateurId)).thenReturn(List.of());
        when(empreinteVocaleRepository.findByUtilisateurId(utilisateurId)).thenReturn(Optional.empty());
        when(seanceTutoratRepository.findByUtilisateurId(utilisateurId)).thenReturn(List.of());
        when(maitriseNotionRepository.findByUtilisateurId(utilisateurId)).thenReturn(List.of());
        when(membreCouloirRepository.findByUtilisateurId(utilisateurId)).thenReturn(List.of());

        ExportDonneesUtilisateur export = gouvernanceDonneesService.exporterDonnees(utilisateurId);

        assertThat(export.email()).isEqualTo("jean@test.fr");
        assertThat(export.empreinteVocale().presente()).isFalse();
    }

    // MembreCouloir n'a aucun point d'injection de date (constructeur =
    // Instant.now()) : un mock Mockito de la classe concrete permet de
    // controler precisement dateAdhesion, meme pattern que pour Engagement
    // dans TableauDeBordEntrepriseServiceTest.
    private static MembreCouloir membreAvecDate(UUID utilisateurId, Instant dateAdhesion) {
        MembreCouloir membre = mock(MembreCouloir.class);
        when(membre.getUtilisateurId()).thenReturn(utilisateurId);
        when(membre.getDateAdhesion()).thenReturn(dateAdhesion);
        return membre;
    }

    // Le proprietaire est toujours filtre avant le tri par date (voir
    // GouvernanceDonneesService.effacerCompte) : getDateAdhesion() n'est
    // jamais appele sur lui, pas la peine de le stubber.
    private static MembreCouloir membreProprietaire(UUID utilisateurId) {
        MembreCouloir membre = mock(MembreCouloir.class);
        when(membre.getUtilisateurId()).thenReturn(utilisateurId);
        return membre;
    }
}
