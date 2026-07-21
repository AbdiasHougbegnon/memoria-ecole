package com.memoria.core.locuteur;

import com.memoria.core.audio.AudioChunk;
import com.memoria.core.audio.AudioChunkRepository;
import com.memoria.core.audio.StockageAudioPort;
import com.memoria.core.session.SessionTermineeEvent;
import com.memoria.core.transcription.SegmentLocuteur;
import com.memoria.core.transcription.Transcription;
import com.memoria.core.transcription.TranscriptionRepository;
import com.memoria.core.transcription.TranscriptionStatut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentificationLocuteurServiceTest {

    @Mock
    private TranscriptionRepository transcriptionRepository;

    @Mock
    private AudioChunkRepository audioChunkRepository;

    @Mock
    private StockageAudioPort stockageAudio;

    @Mock
    private EmpreinteVocaleRepository empreinteVocaleRepository;

    @Mock
    private IdentificateurLocuteurPort identificateur;

    private IdentificationLocuteurService identificationLocuteurService;

    private static final double SEUIL = 0.70;
    private static final long DUREE_MINIMALE_MS = 1500;

    @BeforeEach
    void setUp() {
        identificationLocuteurService = new IdentificationLocuteurService(
                transcriptionRepository, audioChunkRepository, stockageAudio, empreinteVocaleRepository,
                identificateur, SEUIL, DUREE_MINIMALE_MS
        );
    }

    private EmpreinteVocale profilPret(UUID utilisateurId, String profilExterneId) {
        EmpreinteVocale empreinte = new EmpreinteVocale(utilisateurId);
        empreinte.marquerPrete(profilExterneId);
        return empreinte;
    }

    // En-tete WAV valide (8000 Hz, mono, 16 bits, aucune donnee) : le port
    // d'identification est mocke, seul l'en-tete doit etre coherent pour
    // qu'ExtracteurAudioLocuteur (execute reellement) ne divise pas par
    // zero sur un taux d'echantillonnage/alignement de bloc nul.
    private byte[] wavMinimal() {
        java.nio.ByteBuffer tampon = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        tampon.put("RIFF".getBytes());
        tampon.putInt(36);
        tampon.put("WAVE".getBytes());
        tampon.put("fmt ".getBytes());
        tampon.putInt(16);
        tampon.putShort((short) 1);
        tampon.putShort((short) 1);
        tampon.putInt(8000);
        tampon.putInt(16000);
        tampon.putShort((short) 2);
        tampon.putShort((short) 16);
        tampon.put("data".getBytes());
        tampon.putInt(0);
        return tampon.array();
    }

    @Test
    void surSessionTerminee_naboie_pas_azure_si_aucun_profil_pret() {
        when(empreinteVocaleRepository.findByStatut(StatutEmpreinteVocale.PRETE)).thenReturn(List.of());

        identificationLocuteurService.surSessionTerminee(new SessionTermineeEvent(UUID.randomUUID()));

        verify(identificateur, never()).identifier(any(), any());
        verify(transcriptionRepository, never()).findBySessionIdOrderByNumeroSequenceAsc(any());
    }

    @Test
    void surSessionTerminee_identifie_un_locuteur_au_dessus_du_seuil() {
        UUID sessionId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        when(empreinteVocaleRepository.findByStatut(StatutEmpreinteVocale.PRETE))
                .thenReturn(List.of(profilPret(utilisateurId, "profil-A")));

        Transcription chunk = new Transcription(sessionId, 0, "texte", TranscriptionStatut.REUSSIE,
                List.of(new SegmentLocuteur(1, "Bonjour tout le monde", 0, 2000)));
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(List.of(chunk));
        AudioChunk audioChunk = new AudioChunk(sessionId, 0, "chemin.chunk", 1000);
        when(audioChunkRepository.findBySessionIdAndNumeroSequence(sessionId, 0)).thenReturn(Optional.of(audioChunk));
        when(stockageAudio.lire("chemin.chunk")).thenReturn(wavMinimal());
        when(identificateur.identifier(any(), any())).thenReturn(new ResultatIdentification("profil-A", 0.85));

        identificationLocuteurService.surSessionTerminee(new SessionTermineeEvent(sessionId));

        verify(transcriptionRepository).save(chunk);
        assertThat(chunk.getSegmentsLocuteur().get(0).getUtilisateurIdentifieId()).isEqualTo(utilisateurId);
        assertThat(chunk.getSegmentsLocuteur().get(0).getConfianceIdentification()).isEqualTo(0.85);
    }

    @Test
    void surSessionTerminee_ne_marque_rien_si_la_confiance_est_sous_le_seuil() {
        UUID sessionId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        when(empreinteVocaleRepository.findByStatut(StatutEmpreinteVocale.PRETE))
                .thenReturn(List.of(profilPret(utilisateurId, "profil-A")));

        Transcription chunk = new Transcription(sessionId, 0, "texte", TranscriptionStatut.REUSSIE,
                List.of(new SegmentLocuteur(1, "Bonjour", 0, 2000)));
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(List.of(chunk));
        when(audioChunkRepository.findBySessionIdAndNumeroSequence(sessionId, 0))
                .thenReturn(Optional.of(new AudioChunk(sessionId, 0, "chemin.chunk", 1000)));
        when(stockageAudio.lire("chemin.chunk")).thenReturn(wavMinimal());
        when(identificateur.identifier(any(), any())).thenReturn(new ResultatIdentification("profil-A", 0.40));

        identificationLocuteurService.surSessionTerminee(new SessionTermineeEvent(sessionId));

        assertThat(chunk.getSegmentsLocuteur().get(0).getUtilisateurIdentifieId()).isNull();
        verify(transcriptionRepository, never()).save(any());
    }

    @Test
    void surSessionTerminee_ignore_un_locuteur_dont_la_duree_totale_est_trop_courte() {
        UUID sessionId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        when(empreinteVocaleRepository.findByStatut(StatutEmpreinteVocale.PRETE))
                .thenReturn(List.of(profilPret(utilisateurId, "profil-A")));

        Transcription chunk = new Transcription(sessionId, 0, "texte", TranscriptionStatut.REUSSIE,
                List.of(new SegmentLocuteur(1, "Bref", 0, 500))); // 500ms < 1500ms minimum
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(List.of(chunk));

        identificationLocuteurService.surSessionTerminee(new SessionTermineeEvent(sessionId));

        verify(identificateur, never()).identifier(any(), any());
    }

    @Test
    void surSessionTerminee_ignore_un_locuteur_deja_identifie() {
        UUID sessionId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        when(empreinteVocaleRepository.findByStatut(StatutEmpreinteVocale.PRETE))
                .thenReturn(List.of(profilPret(utilisateurId, "profil-A")));

        Transcription chunk = new Transcription(sessionId, 0, "texte", TranscriptionStatut.REUSSIE,
                List.of(new SegmentLocuteur(1, "Bonjour", 0, 2000).avecIdentification(utilisateurId, 0.9)));
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(List.of(chunk));

        identificationLocuteurService.surSessionTerminee(new SessionTermineeEvent(sessionId));

        verify(identificateur, never()).identifier(any(), any());
    }

    @Test
    void surSessionTerminee_continue_les_autres_locuteurs_si_un_echoue() {
        UUID sessionId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        when(empreinteVocaleRepository.findByStatut(StatutEmpreinteVocale.PRETE))
                .thenReturn(List.of(profilPret(utilisateurId, "profil-A")));

        Transcription chunk = new Transcription(sessionId, 0, "texte", TranscriptionStatut.REUSSIE, List.of(
                new SegmentLocuteur(1, "Bonjour tout le monde", 0, 2000),
                new SegmentLocuteur(2, "Salut a tous les gens", 2000, 2000)
        ));
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(List.of(chunk));
        when(audioChunkRepository.findBySessionIdAndNumeroSequence(sessionId, 0))
                .thenReturn(Optional.of(new AudioChunk(sessionId, 0, "chemin.chunk", 1000)));
        when(stockageAudio.lire("chemin.chunk"))
                .thenThrow(new RuntimeException("disque indisponible"))
                .thenReturn(wavMinimal());
        when(identificateur.identifier(any(), any())).thenReturn(new ResultatIdentification("profil-A", 0.9));

        identificationLocuteurService.surSessionTerminee(new SessionTermineeEvent(sessionId));

        long identifies = chunk.getSegmentsLocuteur().stream().filter(s -> s.getUtilisateurIdentifieId() != null).count();
        assertThat(identifies).isEqualTo(1);
    }

    @Test
    void surSessionTerminee_resout_independamment_deux_chunks_avec_le_meme_index_locuteur() {
        UUID sessionId = UUID.randomUUID();
        UUID utilisateurA = UUID.randomUUID();
        UUID utilisateurB = UUID.randomUUID();
        when(empreinteVocaleRepository.findByStatut(StatutEmpreinteVocale.PRETE))
                .thenReturn(List.of(profilPret(utilisateurA, "profil-A"), profilPret(utilisateurB, "profil-B")));

        Transcription chunk0 = new Transcription(sessionId, 0, "texte", TranscriptionStatut.REUSSIE,
                List.of(new SegmentLocuteur(1, "Bonjour tout le monde", 0, 2000)));
        Transcription chunk1 = new Transcription(sessionId, 1, "texte", TranscriptionStatut.REUSSIE,
                List.of(new SegmentLocuteur(1, "Salut a tous les gens", 0, 2000))); // meme index "1", locuteur different
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(List.of(chunk0, chunk1));
        when(audioChunkRepository.findBySessionIdAndNumeroSequence(sessionId, 0))
                .thenReturn(Optional.of(new AudioChunk(sessionId, 0, "chunk0", 1000)));
        when(audioChunkRepository.findBySessionIdAndNumeroSequence(sessionId, 1))
                .thenReturn(Optional.of(new AudioChunk(sessionId, 1, "chunk1", 1000)));
        when(stockageAudio.lire("chunk0")).thenReturn(wavMinimal());
        when(stockageAudio.lire("chunk1")).thenReturn(wavMinimal());
        when(identificateur.identifier(any(), any()))
                .thenReturn(new ResultatIdentification("profil-A", 0.9))
                .thenReturn(new ResultatIdentification("profil-B", 0.9));

        identificationLocuteurService.surSessionTerminee(new SessionTermineeEvent(sessionId));

        assertThat(chunk0.getSegmentsLocuteur().get(0).getUtilisateurIdentifieId()).isEqualTo(utilisateurA);
        assertThat(chunk1.getSegmentsLocuteur().get(0).getUtilisateurIdentifieId()).isEqualTo(utilisateurB);
    }
}
