package io.github.dimasnurfauzi.invoicing.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  /** Login lookup only; see {@link AuthService#login}. */
  Optional<User> findByTenantIdAndEmail(UUID tenantId, String email);
}
