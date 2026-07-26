package com.memoria.ecole.couloir;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

// Vocabulaire Ecole (annee academique, filiere, specialite) : pas de place
// sur l'entite moteur Couloir (core/couloir/Couloir.java), qui doit rester
// reutilisable tel quel par Entreprise -- meme raison que Matiere.couloirId,
// une reference brute plutot qu'un champ sur l'entite moteur. Relation 1-1
// avec Couloir.id (couloirId unique) : un couloir importe ou cree pour une
// promotion porte exactement un triplet.
@Entity
@Table(name = "contextes_scolaires_couloir")
public class ContexteScolaireCouloir {

    @Id
    private UUID id;

    @Column(name = "couloir_id", nullable = false, unique = true)
    private UUID couloirId;

    @Column(name = "annee_academique", nullable = false)
    private String anneeAcademique;

    @Column(nullable = false)
    private String filiere;

    @Column
    private String specialite;

    protected ContexteScolaireCouloir() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public ContexteScolaireCouloir(UUID couloirId, String anneeAcademique, String filiere, String specialite) {
        this.id = UUID.randomUUID();
        this.couloirId = couloirId;
        this.anneeAcademique = anneeAcademique;
        this.filiere = filiere;
        this.specialite = specialite;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCouloirId() {
        return couloirId;
    }

    public String getAnneeAcademique() {
        return anneeAcademique;
    }

    public String getFiliere() {
        return filiere;
    }

    public String getSpecialite() {
        return specialite;
    }
}
