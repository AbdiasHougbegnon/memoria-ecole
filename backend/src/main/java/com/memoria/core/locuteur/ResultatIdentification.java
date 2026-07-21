package com.memoria.core.locuteur;

public record ResultatIdentification(String profilExterneIdReconnu, double confiance) {

    public static ResultatIdentification aucunMatch() {
        return new ResultatIdentification(null, 0.0);
    }
}
