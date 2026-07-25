package com.memoria.ecole.qcm;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

// 4 choix fixes plutot qu'une collection imbriquee (pas de nested
// @ElementCollection dans un @Embeddable) : un QCM classique a toujours 4
// options, meme style plat que NotionCours (ecole.resumecours).
@Embeddable
public class QuestionQcm {

    @Column(columnDefinition = "text")
    private String enonce;

    @Column(columnDefinition = "text")
    private String choixA;

    @Column(columnDefinition = "text")
    private String choixB;

    @Column(columnDefinition = "text")
    private String choixC;

    @Column(columnDefinition = "text")
    private String choixD;

    @Column(name = "index_reponse_correcte")
    private int indexReponseCorrecte;

    @Column(columnDefinition = "text")
    private String explication;

    protected QuestionQcm() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public QuestionQcm(
            String enonce, String choixA, String choixB, String choixC, String choixD,
            int indexReponseCorrecte, String explication
    ) {
        this.enonce = enonce;
        this.choixA = choixA;
        this.choixB = choixB;
        this.choixC = choixC;
        this.choixD = choixD;
        this.indexReponseCorrecte = indexReponseCorrecte;
        this.explication = explication;
    }

    public String getEnonce() {
        return enonce;
    }

    public String getChoixA() {
        return choixA;
    }

    public String getChoixB() {
        return choixB;
    }

    public String getChoixC() {
        return choixC;
    }

    public String getChoixD() {
        return choixD;
    }

    public int getIndexReponseCorrecte() {
        return indexReponseCorrecte;
    }

    public String getExplication() {
        return explication;
    }
}
