package com.memoria.entreprise.engagement;

// EN_ATTENTE et TERMINE sont volontairement les deux seuls etats "stables"
// atteignables depuis, respectivement, la creation et la confirmation :
// voir EngagementService pour les transitions autorisees.
public enum StatutEngagement {
    EN_ATTENTE,
    CONFIRME,
    REJETE,
    TERMINE
}
