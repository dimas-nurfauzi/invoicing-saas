package io.github.dimasnurfauzi.invoicing.supportaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dimasnurfauzi.invoicing.TestcontainersConfiguration;
import io.github.dimasnurfauzi.invoicing.support.TestTenants;
import io.github.dimasnurfauzi.invoicing.support.TestTenants.TenantSession;
import io.github.dimasnurfauzi.invoicing.supportaccess.service.PlatformSupportService;
import io.github.dimasnurfauzi.invoicing.user.entity.Role;
import io.github.dimasnurfauzi.invoicing.user.entity.User;
import io.github.dimasnurfauzi.invoicing.user.repository.UserRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** The support read path cannot write, even if code inside it tries: PostgreSQL refuses. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SupportReadOnlyTests {

  @Autowired TestRestTemplate rest;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformSupportService support;
  @Autowired UserRepository users;

  private TenantSession tenant;
  private UUID platformAdminId;

  @BeforeEach
  void tenantWithGrant() {
    TestTenants api = new TestTenants(rest);
    tenant = api.register("sup-ro");
    api.call(
        HttpMethod.POST,
        "/api/support-access",
        tenant.adminToken(),
        Map.of("durationHours", 1),
        Map.class);
    platformAdminId =
        UUID.fromString(
            jdbc.queryForObject(
                "SELECT id::text FROM platform_admins WHERE email = 'platform-admin@test.local'",
                String.class));
  }

  @Test
  void writeInsideSupportReadIsRejectedByTheDatabase() {
    UUID tenantId = UUID.fromString(tenant.tenantId());

    assertThatThrownBy(() ->
            support.readAs(
                tenantId,
                platformAdminId,
                "TEST-WRITE",
                "test write",
                () -> users.saveAndFlush(new User("sneaky@example.test", "x", Role.ADMIN))))
        .hasStackTraceContaining("read-only transaction");

    assertThat(userCount()).isOne();
  }

  @Test
  void writeWithoutFlushIsNotPersistedEither() {
    UUID tenantId = UUID.fromString(tenant.tenantId());

    support.readAs(
        tenantId,
        platformAdminId,
        "TEST-WRITE",
        "test write",
        () -> users.save(new User("sneaky@example.test", "x", Role.ADMIN)));

    assertThat(userCount()).isOne();
  }

  private int userCount() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM users WHERE tenant_id = ?::uuid", Integer.class, tenant.tenantId());
  }
}
