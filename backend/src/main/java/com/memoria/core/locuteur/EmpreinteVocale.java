package com.memoria.core.locuteur;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

// Un profil par utilisateur (contrainte unique) : le re-enrolement remplace
// l'ancien profil plutot que d'en accumuler plusieurs (voir
// EmpreinteVocaleService.enregistrerConsentement). dateConsentement est la
// preuve RGPD que l'opt-in biometrique a bien eu lieu.
@Entity
@Table(name = "empreintes_vocales", uniqueConstraints = @UniqueConstraint(columnNames = "utilisateur_id"))
public class EmpreinteVocale {

    @Id
    private UUID id;

    @Column(name = "utilisateur_id", nullable = false)
    private UUID utilisateurId;

    // Identifiant du profil chez le fournisseur d'identification (Azure
    // Speaker Recognition aujourd'hui) -- nullable tant que le statut n'est
    // pas PRETE.
    @Column(name = "profil_externe_id")
    private String profilExterneId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutEmpreinteVocale statut;

    @Column(name = "date_consentement", nullable = false)
    private Instant dateConsentement;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    protected EmpreinteVocale() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public EmpreinteVocale(UUID utilisateurId) {
        this.id = UUID.randomUUID();
        this.utilisateurId = utilisateurId;
        this.statut = StatutEmpreinteVocale.EN_ATTENTE;
        this.dateConsentement = Instant.now();
        this.dateCreation = Instant.now();
    }

    public void marquerPrete(String profilExterneId) {
        this.profilExterneId = profilExterneId;
        this.statut = StatutEmpreinteVocale.PRETE;
    }

    public void marquerEchec() {
        this.statut = StatutEmpreinteVocale.ECHEC;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUtilisateurId() {
        return utilisateurId;
    }

    public String getProfilExterneId() {
        return profilExterneId;
    }

    public StatutEmpreinteVocale getStatut() {
        return statut;
    }

    public Instant getDateConsentement() {
        return dateConsentement;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }
}
