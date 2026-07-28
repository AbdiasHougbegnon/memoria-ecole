package com.memoria.ecole.exercice;

import com.memoria.ecole.notion.NiveauMaitrise;

import java.util.List;

// Detail d'infrastructure remplacable (Azure OpenAI aujourd'hui). Deux
// operations distinctes de la meme capacite IA (phase 30, brique C) : generer
// une question de verification de comprehension a partir de la correction
// deja donnee, et evaluer qualitativement une reponse redigee librement
// (alternative au choix a cocher -- l'etudiant peut cocher OU repondre avec
// ses propres mots, voir le texte tape ou transcrit depuis sa voix).
public interface VerificateurComprehensionPort {

    QuestionVerificationGeneree genererQuestion(String enonce, String correctionSynthese, List<PointCorrection> points);

    NiveauMaitrise evaluerReponseLibre(String questionVerification, String reponseEtudiant);
}
