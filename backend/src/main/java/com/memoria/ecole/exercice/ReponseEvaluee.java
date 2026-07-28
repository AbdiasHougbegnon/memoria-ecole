package com.memoria.ecole.exercice;

import com.memoria.ecole.notion.NiveauMaitrise;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

// Reutilise NiveauMaitrise (ecole.notion) : la doctrine du projet rejette deja
// un score numerique pour une evaluation IA qualitative (voir NiveauMaitrise),
// exactement le meme raisonnement s'applique a une reponse en saisie libre.
@Embeddable
public class ReponseEvaluee {

    @Column(columnDefinition = "text")
    private String reponse;

    @Enumerated(EnumType.STRING)
    private NiveauMaitrise niveau;

    @Column(columnDefinition = "text")
    private String retour;

    protected ReponseEvaluee() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public ReponseEvaluee(String reponse, NiveauMaitrise niveau, String retour) {
        this.reponse = reponse;
        this.niveau = niveau;
        this.retour = retour;
    }

    public String getReponse() {
        return reponse;
    }

    public NiveauMaitrise getNiveau() {
        return niveau;
    }

    public String getRetour() {
        return retour;
    }
}
