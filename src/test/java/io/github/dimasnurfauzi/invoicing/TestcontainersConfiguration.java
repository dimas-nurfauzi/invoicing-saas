package io.github.dimasnurfauzi.invoicing;

import io.github.dimasnurfauzi.invoicing.support.ScriptedInvoiceDraftModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Real PostgreSQL for integration tests. Import this in every {@code @SpringBootTest}; Spring's
 * context cache then reuses one container for the whole test run. Also replaces the Claude API with
 * a scripted fake, so no test ever calls it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgresContainer() {
    return new PostgreSQLContainer<>("postgres:17-alpine");
  }

  @Bean
  @Primary
  ScriptedInvoiceDraftModel scriptedInvoiceDraftModel() {
    return new ScriptedInvoiceDraftModel();
  }
}
