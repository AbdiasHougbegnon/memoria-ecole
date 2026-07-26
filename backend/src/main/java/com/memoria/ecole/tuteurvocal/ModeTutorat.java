package com.memoria.ecole.tuteurvocal;

// EXPLICATION : le tuteur explique la notion et verifie la comprehension.
// EXERCICE : le tuteur pose directement des exercices/questions d'application,
// sans re-expliquer d'abord.
// LIBRE : conversation libre sur la matiere, sans notion ni evaluation de
// maitrise -- l'etudiant parle en premier, le tuteur ne fait que repondre
// (voir docs/phases/phase-19-mode-conversation-libre.md).
// Fixe au demarrage d'une SeanceTutorat, pas modifiable en cours de
// conversation dans ce premier increment.
public enum ModeTutorat {
    EXPLICATION,
    EXERCICE,
    LIBRE
}
