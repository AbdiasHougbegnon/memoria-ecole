package com.memoria.core.auth;

// Concept de moteur generique : le moteur ne connait ni "entreprise" ni
// "ecole" en soi (cf. Couloir/MembreCouloir), mais un compte Utilisateur
// doit savoir a quel produit il appartient pour router la connexion et
// restreindre l'acces aux endpoints propres a chaque produit.
public enum ModuleMemoria {
    ECOLE,
    ENTREPRISE
}
