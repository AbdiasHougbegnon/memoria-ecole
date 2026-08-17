package com.memoria.ecole.tuteurvocal;

// EXPLICATION : le tuteur explique la notion et verifie la comprehension.
// EXERCICE : le tuteur pose directement des exercices/questions d'application,
// sans re-expliquer d'abord.
// LIBRE : conversation libre sur la matiere, sans notion ni evaluation de
// maitrise -- l'etudiant parle en premier, le tuteur ne fait que repondre
// (voir docs/phases/phase-19-mode-conversation-libre.md).
// Choisi au demarrage d'une SeanceTutorat, mais PEUT changer en cours de
// route : une session demarree en EXPLICATION bascule automatiquement en
// EXERCICE une fois toutes les notions maitrisees (voir
// SeanceTutorat.passerEnModeExercice et TuteurVocalService.soumettreReponse).
public enum ModeTutorat {
    EXPLICATION,
    EXERCICE,
    LIBRE
}
