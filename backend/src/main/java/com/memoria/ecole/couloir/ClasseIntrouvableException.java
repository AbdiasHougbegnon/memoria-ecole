package com.memoria.ecole.couloir;

public class ClasseIntrouvableException extends RuntimeException {

    public ClasseIntrouvableException(String anneeAcademique, String filiere, String specialite) {
        super("Aucune classe ne correspond a " + anneeAcademique + " / " + filiere + " / " + specialite);
    }
}
