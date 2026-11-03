package io.github.dimasnurfauzi.invoicing.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimasnurfauzi.invoicing.TestcontainersConfiguration;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Enforces the multi-tenancy rules from CLAUDE.md mechanically, so a new tenant-owned table or
 * entity can't silently skip them.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class TenantSchemaGuardTests {

  @Autowired EntityManagerFactory entityManagerFactory;
  @Autowired JdbcTemplate jdbc;

  @Test
  void everyEntityWithTenantIdColumnUsesTenantIdAnnotation() {
    List<String> unguarded = new ArrayList<>();
    for (EntityType<?> entity : entityManagerFactory.getMetamodel().getEntities()) {
      for (Field field : entity.getJavaType().getDeclaredFields()) {
        Column column = field.getAnnotation(Column.class);
        boolean mapsTenantId =
            field.getName().equals("tenantId")
                || (column != null && column.name().equals("tenant_id"));
        if (mapsTenantId && !field.isAnnotationPresent(TenantId.class)) {
          unguarded.add(entity.getName() + "." + field.getName());
        }
      }
    }
    assertThat(unguarded).as("tenant_id fields without @TenantId").isEmpty();
  }

  @Test
  void everyTenantIdColumnIsNotNullAndIndexed() {
    List<String> tables =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.columns"
                + " WHERE table_schema = 'public' AND column_name = 'tenant_id'",
            String.class);
    assertThat(tables).contains("users");

    for (String table : tables) {
      assertThat(
              jdbc.queryForObject(
                  "SELECT is_nullable FROM information_schema.columns"
                      + " WHERE table_schema = 'public' AND table_name = ?"
                      + " AND column_name = 'tenant_id'",
                  String.class,
                  table))
          .as("%s.tenant_id nullable", table)
          .isEqualTo("NO");
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public'"
                      + " AND tablename = ? AND indexdef LIKE '%(tenant_id%'",
                  Integer.class, table))
          .as("index leading with %s.tenant_id", table)
          .isPositive();
    }
  }
}
