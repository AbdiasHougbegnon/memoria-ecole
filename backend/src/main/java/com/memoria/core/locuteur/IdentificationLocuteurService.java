package com.memoria.core.locuteur;

import com.memoria.core.audio.AudioChunk;
import com.memoria.core.audio.AudioChunkRepository;
import com.memoria.core.audio.StockageAudioPort;
import com.memoria.core.session.SessionTermineeEvent;
import com.memoria.core.transcription.SegmentLocuteur;
import com.memoria.core.transcription.Transcription;
import com.memoria.core.transcription.TranscriptionRepository;
import com.memoria.core.transcription.TranscriptionStatut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

// Ecoute SessionTermineeEvent (meme pattern que ResumeService/FilMemoireService)
// et identifie, une seule fois par session, les locuteurs des transcriptions
// deja reussies. Groupe par (chunk, locuteur) et jamais par locuteur seul :
// TranscripteurAzureSpeech fait un appel independant par chunk de 30s, Azure
// renumerote les locuteurs a chaque appel -- aucune continuite n'est
// supposee au-dela d'un seul chunk (pas de recollement inter-chunks dans
// cette brique).
@Service
public class IdentificationLocuteurService {

    private static final Logger LOG = LoggerFactory.getLogger(IdentificationLocuteurService.class);

    private final TranscriptionRepository transcriptionRepository;
    private final AudioChunkRepository audioChunkRepository;
    private final StockageAudioPort stockageAudio;
    private final EmpreinteVocaleRepository empreinteVocaleRepository;
    private final IdentificateurLocuteurPort identificateur;
    private final double seuilConfiance;
    private final long dureeMinimaleMs;

    public IdentificationLocuteurService(
            TranscriptionRepository transcriptionRepository,
            AudioChunkRepository audioChunkRepository,
            StockageAudioPort stockageAudio,
            EmpreinteVocaleRepository empreinteVocaleRepository,
            IdentificateurLocuteurPort identificateur,
            @Value("${azure.speaker.seuil-confiance:0.70}") double seuilConfiance,
            @Value("${memoria.locuteur.duree-minimale-identification-ms:1500}") long dureeMinimaleMs
    ) {
        this.transcriptionRepository = transcriptionRepository;
        this.audioChunkRepository = audioChunkRepository;
        this.stockageAudio = stockageAudio;
        this.empreinteVocaleRepository = empreinteVocaleRepository;
        this.identificateur = identificateur;
        this.seuilConfiance = seuilConfiance;
        this.dureeMinimaleMs = dureeMinimaleMs;
    }

    @Async
    @EventListener
    public void surSessionTerminee(SessionTermineeEvent evenement) {
        identifierLocuteursSession(evenement.sessionId());
    }

    private void identifierLocuteursSession(UUID sessionId) {
        List<EmpreinteVocale> profilsDisponibles = empreinteVocaleRepository.findByStatut(StatutEmpreinteVocale.PRETE);
        if (profilsDisponibles.isEmpty()) {
            return; // aucun appel Azure si personne n'est enrole -- discipline de couts
        }
        Map<String, UUID> utilisateurParProfilExterne = profilsDisponibles.stream()
                .collect(Collectors.toMap(EmpreinteVocale::getProfilExterneId, EmpreinteVocale::getUtilisateurId));
        List<String> profilsExternes = List.copyOf(utilisateurParProfilExterne.keySet());

        List<Transcription> chunks = transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId).stream()
                .filter(chunk -> chunk.getStatut() == TranscriptionStatut.REUSSIE)
                .toList();

        for (Transcription chunk : chunks) {
            try {
                if (identifierChunk(sessionId, chunk, profilsExternes, utilisateurParProfilExterne)) {
                    transcriptionRepository.save(chunk);
                }
            } catch (Exception e) {
                LOG.warn("Echec de l'identification des locuteurs du chunk {} (session {})",
                        chunk.getNumeroSequence(), sessionId, e);
            }
        }
    }

    private boolean identifierChunk(
            UUID sessionId, Transcription chunk, List<String> profilsExternes, Map<String, UUID> utilisateurParProfilExterne) {
        Map<Integer, List<SegmentLocuteur>> segmentsParLocuteur = chunk.getSegmentsLocuteur().stream()
                .collect(Collectors.groupingBy(SegmentLocuteur::getLocuteur));

        boolean modifie = false;
        for (Map.Entry<Integer, List<SegmentLocuteur>> entree : segmentsParLocuteur.entrySet()) {
            int locuteur = entree.getKey();
            List<SegmentLocuteur> segments = entree.getValue();

            if (segments.stream().anyMatch(s -> s.getUtilisateurIdentifieId() != null)) {
                continue; // deja identifie -- idempotence
            }
            long dureeTotale = segments.stream().mapToLong(SegmentLocuteur::getDureeMillisecondes).sum();
            if (dureeTotale < dureeMinimaleMs) {
                continue; // trop court pour une identification fiable
            }

            try {
                Optional<AudioChunk> audioChunk = audioChunkRepository.findBySessionIdAndNumeroSequence(sessionId, chunk.getNumeroSequence());
                if (audioChunk.isEmpty()) {
                    continue;
                }
                byte[] wavChunk = stockageAudio.lire(audioChunk.get().getCheminStockage());
                byte[] audioLocuteur = ExtracteurAudioLocuteur.extraire(wavChunk, segments);

                ResultatIdentification resultat = identificateur.identifier(audioLocuteur, profilsExternes);
                if (resultat.profilExterneIdReconnu() != null && resultat.confiance() >= seuilConfiance) {
                    UUID utilisateurId = utilisateurParProfilExterne.get(resultat.profilExterneIdReconnu());
                    if (utilisateurId != null) {
                        chunk.identifierLocuteur(locuteur, utilisateurId, resultat.confiance());
                        modifie = true;
                    }
                }
            } catch (Exception e) {
                LOG.warn("Echec de l'identification du locuteur {} du chunk {} (session {})",
                        locuteur, chunk.getNumeroSequence(), sessionId, e);
                // un echec sur un locuteur ne doit jamais bloquer les autres
            }
        }
        return modifie;
    }
}
