package com.memoria.core.gouvernance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JournalRgpdRepository extends JpaRepository<JournalRgpd, UUID> {

    List<JournalRgpd> findAllByOrderByDateActionDesc();
}
