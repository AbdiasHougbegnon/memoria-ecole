package com.memoria.entreprise.compterendu;

import com.memoria.core.auth.ModuleMemoria;
import com.memoria.core.auth.Utilisateur;
import com.memoria.core.auth.UtilisateurRepository;
import com.memoria.core.session.Session;
import com.memoria.core.session.SessionNotFoundException;
import com.memoria.core.session.SessionService;
import com.memoria.core.transcription.SegmentLocuteur;
import com.memoria.core.transcription.Transcription;
import com.memoria.core.transcription.TranscriptionRepository;
import com.memoria.core.transcription.TranscriptionStatut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompteRenduServiceTest {

    @Mock
    private CompteRenduRepository compteRenduRepository;

    @Mock
    private TranscriptionRepository transcriptionRepository;

    @Mock
    private GenerateurCompteRenduPort generateurCompteRendu;

    @Mock
    private SessionService sessionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private UtilisateurRepository utilisateurRepository;

    private CompteRenduService compteRenduService;

    @BeforeEach
    void setUp() {
        compteRenduService = new CompteRenduService(
                compteRenduRepository, transcriptionRepository, generateurCompteRendu, sessionService,
                eventPublisher, utilisateurRepository
        );
    }

    @Test
    void obtenirOuGenererCompteRendu_genere_et_sauvegarde_a_partir_des_transcriptions_reussies() {
        UUID sessionId = UUID.randomUUID();
        List<Transcription> transcriptions = List.of(
                new Transcription(sessionId, 0, "Bonjour a tous.", TranscriptionStatut.REUSSIE),
                new Transcription(sessionId, 1, null, TranscriptionStatut.ECHEC),
                new Transcription(sessionId, 2, "Nous avons decide X.", TranscriptionStatut.REUSSIE)
        );
        when(sessionService.obtenirSession(sessionId)).thenReturn(new Session("Reunion"));
        when(compteRenduRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(transcriptions);
        when(generateurCompteRendu.genererCompteRendu("Bonjour a tous.\nNous avons decide X.\n"))
                .thenReturn(new CompteRenduGenere(
                        "Synthese de la reunion.",
                        List.of("Decision X actee"),
                        List.of(new ActionExtraite("Envoyer le mail", "Intervenant 2", "vendredi"))
                ));
        when(compteRenduRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompteRendu resultat = compteRenduService.obtenirOuGenererCompteRendu(sessionId);

        ArgumentCaptor<CompteRendu> captor = ArgumentCaptor.forClass(CompteRendu.class);
        verify(compteRenduRepository).save(captor.capture());
        CompteRendu compteRendu = captor.getValue();
        assertThat(compteRendu.getSessionId()).isEqualTo(sessionId);
        assertThat(compteRendu.getSynthese()).isEqualTo("Synthese de la reunion.");
        assertThat(compteRendu.getDecisions()).containsExactly("Decision X actee");
        assertThat(compteRendu.getActions()).hasSize(1);
        assertThat(compteRendu.getActions().get(0).getDescription()).isEqualTo("Envoyer le mail");
        assertThat(compteRendu.getActions().get(0).getResponsable()).isEqualTo("Intervenant 2");
        assertThat(compteRendu.getActions().get(0).getEcheance()).isEqualTo("vendredi");
        assertThat(compteRendu.getSegmentsSources()).containsExactly(0, 2);
        assertThat(compteRendu.getStatut()).isEqualTo(StatutCompteRendu.REUSSI);
        assertThat(resultat).isEqualTo(compteRendu);
        verify(eventPublisher).publishEvent(new CompteRenduGenereEvent(sessionId));
    }

    @Test
    void obtenirOuGenererCompteRendu_resout_le_responsable_identifie() {
        UUID sessionId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Utilisateur utilisateur = new Utilisateur("alice@test.fr", "hash", ModuleMemoria.ENTREPRISE);
        utilisateur.renseignerNom("Alice Martin");
        List<Transcription> transcriptions = List.of(
                new Transcription(sessionId, 0, "Bonjour", TranscriptionStatut.REUSSIE,
                        List.of(new SegmentLocuteur(1, "Bonjour, je m'en occupe", 0, 2000).avecIdentification(utilisateurId, 0.9)))
        );
        when(sessionService.obtenirSession(sessionId)).thenReturn(new Session("Reunion"));
        when(compteRenduRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(transcriptions);
        when(utilisateurRepository.findById(utilisateurId)).thenReturn(Optional.of(utilisateur));
        when(generateurCompteRendu.genererCompteRendu("Alice Martin : Bonjour, je m'en occupe\n"))
                .thenReturn(new CompteRenduGenere(
                        "Synthese.", List.of(),
                        List.of(new ActionExtraite("Envoyer le mail", "Alice Martin", null))
                ));
        when(compteRenduRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompteRendu resultat = compteRenduService.obtenirOuGenererCompteRendu(sessionId);

        assertThat(resultat.getActions().get(0).getResponsable()).isEqualTo("Alice Martin");
        assertThat(resultat.getActions().get(0).getResponsableUtilisateurId()).isEqualTo(utilisateurId);
    }

    @Test
    void obtenirOuGenererCompteRendu_marque_echec_quand_le_generateur_echoue() {
        UUID sessionId = UUID.randomUUID();
        List<Transcription> transcriptions = List.of(
                new Transcription(sessionId, 0, "Bonjour.", TranscriptionStatut.REUSSIE)
        );
        when(sessionService.obtenirSession(sessionId)).thenReturn(new Session("Reunion"));
        when(compteRenduRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(transcriptions);
        when(generateurCompteRendu.genererCompteRendu(any()))
                .thenThrow(new GenerationCompteRenduException("Azure OpenAI indisponible"));
        when(compteRenduRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompteRendu resultat = compteRenduService.obtenirOuGenererCompteRendu(sessionId);

        assertThat(resultat.getStatut()).isEqualTo(StatutCompteRendu.ECHEC);
        assertThat(resultat.getSynthese()).isNull();
        assertThat(resultat.getDecisions()).isEmpty();
        assertThat(resultat.getActions()).isEmpty();
        assertThat(resultat.getSegmentsSources()).containsExactly(0);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void obtenirOuGenererCompteRendu_renvoie_le_compte_rendu_deja_en_cache_sans_regenerer() {
        UUID sessionId = UUID.randomUUID();
        CompteRendu dejaGenere = new CompteRendu(
                sessionId, "Deja fait", List.of(), List.of(), List.of(0), StatutCompteRendu.REUSSI
        );
        when(sessionService.obtenirSession(sessionId)).thenReturn(new Session("Reunion"));
        when(compteRenduRepository.findBySessionId(sessionId)).thenReturn(Optional.of(dejaGenere));

        CompteRendu resultat = compteRenduService.obtenirOuGenererCompteRendu(sessionId);

        assertThat(resultat).isSameAs(dejaGenere);
        verify(generateurCompteRendu, never()).genererCompteRendu(any());
        verify(transcriptionRepository, never()).findBySessionIdOrderByNumeroSequenceAsc(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void obtenirOuGenererCompteRendu_leve_une_exception_si_aucune_transcription_na_reussi() {
        UUID sessionId = UUID.randomUUID();
        when(sessionService.obtenirSession(sessionId)).thenReturn(new Session("Reunion"));
        when(compteRenduRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(List.of());

        assertThatThrownBy(() -> compteRenduService.obtenirOuGenererCompteRendu(sessionId))
                .isInstanceOf(AucuneTranscriptionDisponibleException.class);
        verify(generateurCompteRendu, never()).genererCompteRendu(any());
    }

    @Test
    void obtenirOuGenererCompteRendu_leve_une_exception_si_la_session_est_introuvable() {
        UUID idInconnu = UUID.randomUUID();
        when(sessionService.obtenirSession(idInconnu)).thenThrow(new SessionNotFoundException(idInconnu));

        assertThatThrownBy(() -> compteRenduService.obtenirOuGenererCompteRendu(idInconnu))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void obtenirCompteRendu_retourne_le_compte_rendu_existant() {
        UUID sessionId = UUID.randomUUID();
        CompteRendu compteRendu = new CompteRendu(
                sessionId, "Texte", List.of("Decision"), List.of(), List.of(0), StatutCompteRendu.REUSSI
        );
        when(compteRenduRepository.findBySessionId(sessionId)).thenReturn(Optional.of(compteRendu));

        CompteRendu resultat = compteRenduService.obtenirCompteRendu(sessionId);

        assertThat(resultat).isSameAs(compteRendu);
    }

    @Test
    void obtenirCompteRendu_leve_une_exception_si_aucun_compte_rendu_nexiste() {
        UUID sessionId = UUID.randomUUID();
        when(compteRenduRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> compteRenduService.obtenirCompteRendu(sessionId))
                .isInstanceOf(CompteRenduNotFoundException.class);
    }
}
