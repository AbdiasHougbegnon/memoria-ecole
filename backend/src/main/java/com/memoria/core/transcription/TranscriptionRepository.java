package com.memoria.core.transcription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TranscriptionRepository extends JpaRepository<Transcription, UUID> {

    List<Transcription> findBySessionIdOrderByNumeroSequenceAsc(UUID sessionId);

    long countBySessionId(UUID sessionId);

    void deleteBySessionId(UUID sessionId);

    // transcription_segments_locuteur est la table d'une @ElementCollection,
    // pas une entite JPA a part entiere -- une requete native est necessaire
    // pour la mettre a jour en masse (droit a l'effacement, voir
    // GouvernanceDonneesService : anonymise l'identification d'un locuteur
    // sans toucher au contenu partage de la transcription).
    @Modifying
    @Query(
            value = "update transcription_segments_locuteur set utilisateur_identifie_id = null, "
                    + "confiance_identification = null where utilisateur_identifie_id = :utilisateurId",
            nativeQuery = true
    )
    void anonymiserSegmentsLocuteur(@Param("utilisateurId") UUID utilisateurId);
}
