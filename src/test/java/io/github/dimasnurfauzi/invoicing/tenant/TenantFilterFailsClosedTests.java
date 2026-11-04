package io.github.dimasnurfauzi.invoicing.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dimasnurfauzi.invoicing.TestcontainersConfiguration;
import io.github.dimasnurfauzi.invoicing.support.TestTenants;
import io.github.dimasnurfauzi.invoicing.support.TestTenants.TenantSession;
import io.github.dimasnurfauzi.invoicing.tenant.context.TenantContext;
import io.github.dimasnurfauzi.invoicing.user.entity.Role;
import io.github.dimasnurfauzi.invoicing.user.entity.User;
import io.github.dimasnurfauzi.invoicing.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Repository-level behaviour of the tenant filter, below the HTTP layer. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class TenantFilterFailsClosedTests {

  @Autowired TestRestTemplate rest;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbc;

  @Test
  void withoutTenantContextNothingIsVisible() {
    TenantSession tenant = new TestTenants(rest).register("closed");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isPositive();

    assertThat(users.findAll()).isEmpty();
    assertThat(users.count()).isZero();
    assertThat(users.findById(UUID.fromString(tenant.adminId()))).isEmpty();
    assertThat(users.findByEmail(tenant.adminEmail())).isEmpty();
  }

  @Test
  void withoutTenantContextInsertFails() {
    assertThatThrownBy(() -> users.saveAndFlush(new User("orphan@example.test", "x", Role.STAFF)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void findByIdIsScopedToTheCurrentTenant() {
    TestTenants api = new TestTenants(rest);
    TenantSession a = api.register("find-a");
    TenantSession b = api.register("find-b");
    UUID adminB = UUID.fromString(b.adminId());

    assertThat(TenantContext.callAs(UUID.fromString(a.tenantId()), () -> users.findById(adminB)))
        .isEmpty();
    assertThat(TenantContext.callAs(UUID.fromString(b.tenantId()), () -> users.findById(adminB)))
        .isPresent();
  }
}
