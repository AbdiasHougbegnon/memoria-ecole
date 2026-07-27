package com.memoria.core.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "utilisateurs", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class Utilisateur {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "mot_de_passe_hash", nullable = false)
    private String motDePasseHash;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    // Optionnel : aucune inscription ne le demande aujourd'hui. Utilise pour
    // l'affichage d'un nom reel (ex: reconnaissance de locuteur recurrente) ;
    // repli sur l'email tant qu'il n'est pas renseigne (voir nomAffichage()).
    @Column
    private String nom;

    // Produit auquel ce compte appartient (choisi a l'inscription, jamais
    // modifiable ensuite dans ce lot). Determine le routage frontend et les
    // autorisations backend (voir SecurityConfig) -- pas un concept du
    // moteur lui-meme (cf. Couloir), mais une propriete du compte.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModuleMemoria module;

    // Accorde uniquement via la liste memoria.admin.emails-autorises (voir
    // AuthService et AdminBootstrapRunner) -- jamais de promotion en libre-service.
    @Column(name = "est_admin", nullable = false)
    private boolean estAdmin;

    protected Utilisateur() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public Utilisateur(String email, String motDePasseHash, ModuleMemoria module) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.motDePasseHash = motDePasseHash;
        this.dateCreation = Instant.now();
        this.module = module;
        this.estAdmin = false;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getMotDePasseHash() {
        return motDePasseHash;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }

    public String getNom() {
        return nom;
    }

    public ModuleMemoria getModule() {
        return module;
    }

    public boolean estAdmin() {
        return estAdmin;
    }

    public void renseignerNom(String nom) {
        this.nom = nom;
    }

    public void promouvoirAdmin() {
        this.estAdmin = true;
    }

    public String nomAffichage() {
        return (nom != null && !nom.isBlank()) ? nom : email;
    }
}
