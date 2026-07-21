package com.memoria.core.locuteur;

public class AudioEnrollementInsuffisantException extends RuntimeException {

    public AudioEnrollementInsuffisantException() {
        super("L'echantillon audio est trop court pour enroler une empreinte vocale");
    }
}
