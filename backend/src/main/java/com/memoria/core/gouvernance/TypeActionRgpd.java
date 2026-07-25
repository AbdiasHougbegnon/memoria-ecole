package com.memoria.core.gouvernance;

// Audit reduit et proportionne (voir docs/phases/phase-13-gouvernance-donnees.md) :
// journalise les operations RGPD elles-memes, pas chaque acces a chaque
// donnee sensible (un audit general serait un chantier d'observabilite a
// part entiere, hors de proportion pour cette brique).
public enum TypeActionRgpd {
    EFFACEMENT_COMPTE,
    EXPORT_DONNEES,
    PURGE_RETENTION
}
