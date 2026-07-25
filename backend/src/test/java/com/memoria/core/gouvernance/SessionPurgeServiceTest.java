package com.memoria.core.gouvernance;

import com.memoria.core.audio.AudioChunkRepository;
import com.memoria.core.audio.StockageAudioPort;
import com.memoria.core.document.DocumentRepository;
import com.memoria.core.document.StockageDocumentPort;
import com.memoria.core.filmemoire.FilMemoire;
import com.memoria.core.filmemoire.FilMemoireRepository;
import com.memoria.core.recherche.IndexRechercheRepository;
import com.memoria.core.recherche.RecherchePort;
import com.memoria.core.resume.ResumeRepository;
import com.memoria.core.session.SessionRepository;
import com.memoria.core.transcription.TranscriptionRepository;
import com.memoria.ecole.qcm.Qcm;
import com.memoria.ecole.qcm.QcmRepository;
import com.memoria.ecole.qcm.StatutQcm;
import com.memoria.ecole.qcm.TentativeQcmRepository;
import com.memoria.ecole.resumecours.ResumeCoursRepository;
import com.memoria.entreprise.compterendu.CompteRenduRepository;
import com.memoria.entreprise.engagement.EngagementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionPurgeServiceTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private TranscriptionRepository transcriptionRepository;
    @Mock private ResumeRepository resumeRepository;
    @Mock private CompteRenduRepository compteRenduRepository;
    @Mock private ResumeCoursRepository resumeCoursRepository;
    @Mock private QcmRepository qcmRepository;
    @Mock private TentativeQcmRepository tentativeQcmRepository;
    @Mock private IndexRechercheRepository indexRechercheRepository;
    @Mock private AudioChunkRepository audioChunkRepository;
    @Mock private EngagementRepository engagementRepository;
    @Mock private FilMemoireRepository filMemoireRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private StockageAudioPort stockageAudio;
    @Mock private StockageDocumentPort stockageDocument;
    @Mock private RecherchePort recherche;

    private SessionPurgeService sessionPurgeService;

    @BeforeEach
    void setUp() {
        sessionPurgeService = new SessionPurgeService(
                documentRepository, transcriptionRepository, resumeRepository, compteRenduRepository,
                resumeCoursRepository, qcmRepository, tentativeQcmRepository, indexRechercheRepository, audioChunkRepository, engagementRepository,
                filMemoireRepository, sessionRepository, stockageAudio, stockageDocument, recherche
        );
    }

    @Test
    void purgerSessionCompletement_supprime_toutes_les_donnees_liees_a_la_session() {
        UUID sessionId = UUID.randomUUID();
        when(filMemoireRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

        sessionPurgeService.purgerSessionCompletement(sessionId);

        verify(documentRepository).deleteBySessionId(sessionId);
        verify(transcriptionRepository).deleteBySessionId(sessionId);
        verify(resumeRepository).deleteBySessionId(sessionId);
        verify(compteRenduRepository).deleteBySessionId(sessionId);
        verify(resumeCoursRepository).deleteBySessionId(sessionId);
        verify(qcmRepository).deleteBySessionId(sessionId);
        verify(indexRechercheRepository).deleteBySessionId(sessionId);
        verify(audioChunkRepository).deleteBySessionId(sessionId);
        verify(engagementRepository).deleteBySessionId(sessionId);
        verify(sessionRepository).deleteById(sessionId);
    }

    @Test
    void purgerSessionCompletement_supprime_les_tentatives_du_qcm_de_la_session() {
        UUID sessionId = UUID.randomUUID();
        Qcm qcm = new Qcm(sessionId, List.of(), List.of(0), StatutQcm.REUSSI);
        when(filMemoireRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(qcmRepository.findBySessionId(sessionId)).thenReturn(Optional.of(qcm));

        sessionPurgeService.purgerSessionCompletement(sessionId);

        verify(tentativeQcmRepository).deleteByQcmId(qcm.getId());
        verify(qcmRepository).deleteBySessionId(sessionId);
    }

    @Test
    void purgerSessionCompletement_retire_la_session_dun_fil_de_memoire_sans_le_vider() {
        UUID sessionId = UUID.randomUUID();
        UUID autreSessionId = UUID.randomUUID();
        FilMemoire fil = new FilMemoire("Reseaux", "resume", new byte[]{1}, new java.util.ArrayList<>(List.of(sessionId, autreSessionId)));
        when(filMemoireRepository.findBySessionId(sessionId)).thenReturn(Optional.of(fil));

        sessionPurgeService.purgerSessionCompletement(sessionId);

        verify(filMemoireRepository).save(fil);
        verify(filMemoireRepository, never()).delete(any());
    }

    @Test
    void purgerSessionCompletement_supprime_le_fil_de_memoire_devenu_vide() {
        UUID sessionId = UUID.randomUUID();
        FilMemoire fil = new FilMemoire("Reseaux", "resume", new byte[]{1}, new java.util.ArrayList<>(List.of(sessionId)));
        when(filMemoireRepository.findBySessionId(sessionId)).thenReturn(Optional.of(fil));

        sessionPurgeService.purgerSessionCompletement(sessionId);

        verify(filMemoireRepository).delete(fil);
        verify(filMemoireRepository, never()).save(any());
    }

    @Test
    void nettoyerDependancesExternes_appelle_les_trois_ports_meme_si_lun_echoue() {
        UUID sessionId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new RuntimeException("disque indisponible")).when(stockageAudio).supprimerSession(sessionId);

        sessionPurgeService.nettoyerDependancesExternes(sessionId);

        verify(stockageAudio).supprimerSession(sessionId);
        verify(stockageDocument).supprimerSession(sessionId);
        verify(recherche).supprimerDocumentsSession(sessionId);
    }
}
