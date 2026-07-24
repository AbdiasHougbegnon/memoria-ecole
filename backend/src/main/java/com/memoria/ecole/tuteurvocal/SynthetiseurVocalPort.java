package com.memoria.ecole.tuteurvocal;

// Detail d'infrastructure remplacable (Azure Speech Text-to-Speech
// aujourd'hui, un autre fournisseur demain).
public interface SynthetiseurVocalPort {

    byte[] synthetiser(String texte);
}
