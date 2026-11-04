package io.github.dimasnurfauzi.invoicing.platform.repository;

import io.github.dimasnurfauzi.invoicing.platform.entity.PlatformAdmin;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, UUID> {

  Optional<PlatformAdmin> findByEmail(String email);

  boolean existsByEmail(String email);
}
