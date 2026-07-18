package com.memoria.ecole.resumecours;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class NotionCours {

    @Column(columnDefinition = "text")
    private String terme;

    @Column(columnDefinition = "text")
    private String definition;

    protected NotionCours() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public NotionCours(String terme, String definition) {
        this.terme = terme;
        this.definition = definition;
    }

    public String getTerme() {
        return terme;
    }

    public String getDefinition() {
        return definition;
    }
}
