package com.memoria.core.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessions")
public class Session {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String titre;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus statut;

    @Column(name = "chemin_fichier_audio")
    private String cheminFichierAudio;

    // Rattachement optionnel a un couloir (com.memoria.core.couloir) --
    // reference brute, pas de relation JPA, comme partout ailleurs dans le
    // projet. Null = session personnelle, non rattachee a un couloir.
    @Column(name = "couloir_id")
    private UUID couloirId;

    // Createur de la session -- null pour les sessions creees avant
    // l'introduction de ce champ (elles restent visibles a tout le monde,
    // voir SessionService.listerSessionsVisibles) et pour les sessions
    // creees via l'ancien constructeur a 1 argument, conserve pour les tests.
    @Column(name = "createur_id")
    private UUID createurId;

    // Nullable de fait pour les sessions creees avant l'introduction de ce
    // champ (phase 21) -- pas de backfill retroactif, le consentement ne
    // peut pas etre reconstitue apres coup. Renseigne uniquement via
    // confirmerConsentementEnregistrement(), jamais au constructeur : c'est
    // SessionService qui verifie le consentement AVANT de construire la
    // session (meme doctrine que EmpreinteVocaleService.enregistrerConsentement).
    @Column(name = "date_consentement_enregistrement")
    private Instant dateConsentementEnregistrement;

    protected Session() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public Session(String titre) {
        this.id = UUID.randomUUID();
        this.titre = titre;
        this.dateCreation = Instant.now();
        this.statut = SessionStatus.EN_COURS;
    }

    public Session(String titre, UUID createurId, UUID couloirId) {
        this(titre);
        this.createurId = createurId;
        this.couloirId = couloirId;
    }

    public UUID getId() {
        return id;
    }

    public String getTitre() {
        return titre;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }

    public SessionStatus getStatut() {
        return statut;
    }

    public String getCheminFichierAudio() {
        return cheminFichierAudio;
    }

    public UUID getCouloirId() {
        return couloirId;
    }

    public UUID getCreateurId() {
        return createurId;
    }

    public Instant getDateConsentementEnregistrement() {
        return dateConsentementEnregistrement;
    }

    public void confirmerConsentementEnregistrement() {
        this.dateConsentementEnregistrement = Instant.now();
    }

    public void terminer() {
        this.statut = SessionStatus.TERMINEE;
    }

    // Droit a l'effacement (voir GouvernanceDonneesService) pour une session
    // rattachee a un couloir : le contenu est partage avec d'autres membres,
    // donc jamais supprime pour l'effacement d'un seul participant. On
    // reutilise l'etat "createurId null" deja supporte pour les sessions
    // anterieures a l'introduction de ce champ (voir commentaire ci-dessus) --
    // pas une nouvelle semantique.
    public void anonymiserCreateur() {
        this.createurId = null;
    }
}
