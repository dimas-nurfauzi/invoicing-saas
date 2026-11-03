package io.github.dimasnurfauzi.invoicing.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every method is scoped to the current tenant by {@code @TenantId} on {@link User}; none takes a
 * tenant id. Don't add native queries here: they bypass the tenant filter.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  List<User> findAllByOrderByCreatedAtAscEmailAsc();
}
