package com.memoria.ecole.tuteurvocal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// Pas de stockage audio brut : seul le texte est persiste, l'audio est
// resynthetise a la demande (endpoint dedie) -- coherent avec l'absence de
// stockage de blobs ailleurs dans le projet en dehors du cas deja gere
// (fichiers audio de session), voir docs/phases/phase-9-tuteur-vocal.md.
@Entity
@Table(name = "tours_dialogue_tutorat")
public class TourDialogueTutorat {

    @Id
    private UUID id;

    @Column(name = "seance_tutorat_id", nullable = false)
    private UUID seanceTutoratId;

    // Nullable en mode LIBRE : un tour de conversation libre n'est rattache a
    // aucune notion precise (voir docs/phases/phase-19-mode-conversation-libre.md).
    @Column(name = "notion_id")
    private UUID notionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Locuteur locuteur;

    @Column(columnDefinition = "text", nullable = false)
    private String texte;

    // Mode actif au moment ou CE tour precis a ete genere -- indispensable
    // depuis la bascule automatique EXPLICATION -> EXERCICE (voir
    // SeanceTutorat.passerEnModeExercice) : une meme notion peut etre
    // revisitee dans les deux modes au sein d'une seule SeanceTutorat, donc
    // le mode du SeanceTutorat seul ne suffit plus a savoir sous quel mode un
    // tour passe a ete produit. Sert notamment a compter les exercices deja
    // poses sur la notion courante (TuteurVocalService.NOMBRE_EXERCICES_PAR_NOTION),
    // sans compter par erreur d'anciens tours d'explication sur cette meme
    // notion.
    //
    // Colonne nommee "mode_tutorat", PAS "mode" : "mode" declenche une
    // erreur PostgreSQL ("WITHIN GROUP is required for ordered-set aggregate
    // mode") des qu'il apparait comme nom de colonne dans une liste SELECT,
    // meme qualifie par l'alias de table -- collision avec la fonction
    // d'agregat ordered-set integree mode() WITHIN GROUP (ORDER BY ...).
    // Verifie en conditions reelles : ddl-auto=update echouait silencieusement
    // a creer la colonne, et toute requete generee par Hibernate listant
    // cette colonne remontait cette erreur au runtime.
    @Enumerated(EnumType.STRING)
    @Column(name = "mode_tutorat", nullable = false)
    private ModeTutorat mode;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    protected TourDialogueTutorat() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public TourDialogueTutorat(UUID seanceTutoratId, UUID notionId, Locuteur locuteur, String texte, ModeTutorat mode) {
        this.id = UUID.randomUUID();
        this.seanceTutoratId = seanceTutoratId;
        this.notionId = notionId;
        this.locuteur = locuteur;
        this.texte = texte;
        this.mode = mode;
        this.dateCreation = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getSeanceTutoratId() {
        return seanceTutoratId;
    }

    public UUID getNotionId() {
        return notionId;
    }

    public Locuteur getLocuteur() {
        return locuteur;
    }

    public String getTexte() {
        return texte;
    }

    public ModeTutorat getMode() {
        return mode;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }
}
