package com.memoria.ecole.couloir;

public record OptionInscriptionResponse(String anneeAcademique, String filiere, String specialite) {

    static OptionInscriptionResponse depuis(ContexteScolaireCouloir contexte) {
        return new OptionInscriptionResponse(contexte.getAnneeAcademique(), contexte.getFiliere(), contexte.getSpecialite());
    }
}
