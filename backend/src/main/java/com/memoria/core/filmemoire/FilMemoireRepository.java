package com.memoria.core.filmemoire;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FilMemoireRepository extends JpaRepository<FilMemoire, UUID> {

    @Query("SELECT COUNT(f) > 0 FROM FilMemoire f WHERE :sessionId MEMBER OF f.sessionIds")
    boolean existsBySessionId(@Param("sessionId") UUID sessionId);

    List<FilMemoire> findAllByOrderByDateMiseAJourDesc();
}
