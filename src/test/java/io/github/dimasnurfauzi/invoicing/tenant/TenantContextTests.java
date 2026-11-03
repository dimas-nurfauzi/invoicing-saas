package io.github.dimasnurfauzi.invoicing.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextTests {

  private final UUID tenantA = UUID.randomUUID();
  private final UUID tenantB = UUID.randomUUID();

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void callAsSetsTenantOnlyForTheAction() {
    UUID seen = TenantContext.callAs(tenantA, () -> TenantContext.current().orElseThrow());

    assertThat(seen).isEqualTo(tenantA);
    assertThat(TenantContext.current()).isEmpty();
  }

  @Test
  void nestedCallAsRestoresOuterTenant() {
    TenantContext.callAs(
        tenantA,
        () -> {
          assertThat(TenantContext.callAs(tenantB, () -> TenantContext.current().orElseThrow()))
              .isEqualTo(tenantB);
          assertThat(TenantContext.current()).contains(tenantA);
          return null;
        });
  }

  @Test
  void callAsRestoresEvenWhenActionThrows() {
    assertThatThrownBy(() ->
            TenantContext.callAs(
                tenantA,
                () -> {
                  throw new IllegalStateException("boom");
                }))
        .hasMessage("boom");

    assertThat(TenantContext.current()).isEmpty();
  }

  @Test
  void callAsRejectsNullTenant() {
    assertThatThrownBy(() -> TenantContext.callAs(null, () -> null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
