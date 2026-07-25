package com.memoria.ecole.tuteurvocal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TourDialogueTutoratRepository extends JpaRepository<TourDialogueTutorat, UUID> {

    List<TourDialogueTutorat> findBySeanceTutoratIdOrderByDateCreationAsc(UUID seanceTutoratId);

    List<TourDialogueTutorat> findBySeanceTutoratIdAndNotionIdOrderByDateCreationAsc(UUID seanceTutoratId, UUID notionId);

    void deleteBySeanceTutoratId(UUID seanceTutoratId);
}
