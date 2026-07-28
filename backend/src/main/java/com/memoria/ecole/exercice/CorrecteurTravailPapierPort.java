package com.memoria.ecole.exercice;

import java.util.List;

// Detail d'infrastructure remplacable (Azure OpenAI aujourd'hui). Corrige le
// travail qu'un etudiant a fait sur papier, a partir du texte de l'enonce
// (photo separee, phase 28) et du texte de sa reponse -- decoupe en exercices
// individuels plutot que de deviner l'enonce a partir de la seule reponse
// (limite assumee des phases 24/26/27, levee ici a la demande explicite de
// l'utilisateur).
public interface CorrecteurTravailPapierPort {

    List<ExerciceCorrige> corriger(String texteEnonce, String texteReponse);
}
