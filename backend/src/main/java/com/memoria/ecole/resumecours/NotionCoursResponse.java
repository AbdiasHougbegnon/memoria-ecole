package com.memoria.ecole.resumecours;

public record NotionCoursResponse(String terme, String definition) {

    public static NotionCoursResponse depuis(NotionCours notion) {
        return new NotionCoursResponse(notion.getTerme(), notion.getDefinition());
    }
}
