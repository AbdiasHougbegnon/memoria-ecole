package com.memoria.ecole.exercice;

// Statut de la verification de comprehension en mode progressif (phase 30,
// brique C) -- distinct du niveau de correction initial (NiveauMaitrise) :
// un exercice "EN_COURS" peut devenir VALIDE si l'etudiant confirme avoir
// compris via la question de verification, ou rester PAS_CLAIR sinon. Ne
// bloque jamais la navigation entre exercices, quel que soit son statut.
public enum StatutVerification {
    NON_VERIFIE,
    VALIDE,
    PAS_CLAIR
}
