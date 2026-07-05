package com.memoria.entreprise.compterendu;

public record ActionCompteRenduResponse(String description, String responsable, String echeance) {

    public static ActionCompteRenduResponse depuis(ActionCompteRendu action) {
        return new ActionCompteRenduResponse(action.getDescription(), action.getResponsable(), action.getEcheance());
    }
}
