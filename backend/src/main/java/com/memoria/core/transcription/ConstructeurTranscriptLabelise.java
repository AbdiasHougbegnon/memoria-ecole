package com.memoria.core.transcription;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

// Construit, a partir des transcriptions d'une session, un texte etiquete
// par locuteur a soumettre a une IA generative (compte rendu, resume) --
// corrige un ecart reel : les generateurs (GenerateurCompteRenduAzureOpenAI)
// demandaient a l'IA de reutiliser des reperes "Intervenant N", mais
// recevaient jusqu'ici le texte brut concatene (Transcription.getTexte,
// sans aucune etiquette de locuteur) : l'IA n'avait donc jamais l'info
// necessaire pour suivre cette consigne, et semblait inventer des noms a
// partir du contenu de la conversation.
//
// Quand un segment a ete identifie (reconnaissance de locuteur recurrente,
// voir IdentificationLocuteurService), son vrai nom est utilise comme
// etiquette -- ce qui permet ensuite a EngagementService de resoudre
// precisement le responsable d'une action, au lieu de notifier tous les
// participants de la session. Un segment non identifie recoit une etiquette
// "Intervenant X" stable au sein d'un seul appel (A, B, C...) : locale a ce
// texte, jamais une identite globale (voir la limite documentee dans
// IdentificationLocuteurService -- aucune continuite n'est supposee au-dela
// d'un seul chunk).
public final class ConstructeurTranscriptLabelise {

    private ConstructeurTranscriptLabelise() {
    }

    public record Resultat(String texte, Map<String, UUID> utilisateurIdParLabel) {
    }

    public static Resultat construire(List<Transcription> transcriptions, Function<UUID, String> resolveurNom) {
        StringBuilder texte = new StringBuilder();
        Map<String, UUID> utilisateurIdParLabel = new LinkedHashMap<>();
        Map<String, String> labelParCleLocale = new HashMap<>();
        int[] compteurAnonyme = {0};

        for (Transcription transcription : transcriptions) {
            if (transcription.getSegmentsLocuteur().isEmpty()) {
                if (transcription.getTexte() != null && !transcription.getTexte().isBlank()) {
                    texte.append(transcription.getTexte()).append('\n');
                }
                continue;
            }

            for (SegmentLocuteur segment : transcription.getSegmentsLocuteur()) {
                String label = etiquette(transcription, segment, resolveurNom, labelParCleLocale, compteurAnonyme, utilisateurIdParLabel);
                texte.append(label).append(" : ").append(segment.getTexte()).append('\n');
            }
        }

        return new Resultat(texte.toString(), utilisateurIdParLabel);
    }

    private static String etiquette(
            Transcription transcription,
            SegmentLocuteur segment,
            Function<UUID, String> resolveurNom,
            Map<String, String> labelParCleLocale,
            int[] compteurAnonyme,
            Map<String, UUID> utilisateurIdParLabel
    ) {
        UUID utilisateurIdentifie = segment.getUtilisateurIdentifieId();
        if (utilisateurIdentifie != null) {
            String nom = resolveurNom.apply(utilisateurIdentifie);
            if (nom != null) {
                utilisateurIdParLabel.put(nom, utilisateurIdentifie);
                return nom;
            }
        }

        String cleLocale = transcription.getNumeroSequence() + "-" + segment.getLocuteur();
        return labelParCleLocale.computeIfAbsent(
                cleLocale, k -> "Intervenant " + (char) ('A' + (compteurAnonyme[0]++ % 26)));
    }
}
