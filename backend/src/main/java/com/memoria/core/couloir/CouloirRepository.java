package com.memoria.core.couloir;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CouloirRepository extends JpaRepository<Couloir, UUID> {
}
