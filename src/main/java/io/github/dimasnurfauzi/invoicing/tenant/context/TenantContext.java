package io.github.dimasnurfauzi.invoicing.tenant.context;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The current request's tenant. Set only from the validated JWT ({@link TenantContextFilter}) or,
 * for the pre-authentication flows (registration, login), from a tenant the server itself resolved
 * ({@link #callAs}). Never from request input.
 *
 * <p>Thread-local: it does not propagate to other threads ({@code @Async}, executors, scheduled
 * jobs). Code running outside a request must wrap tenant work in {@link #callAs}.
 */
public final class TenantContext {

  private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

  private TenantContext() {}

  public static Optional<UUID> current() {
    return Optional.ofNullable(CURRENT.get());
  }

  /** Runs {@code action} as {@code tenantId}, then restores whatever was set before. */
  public static <T> T callAs(UUID tenantId, Supplier<T> action) {
    if (tenantId == null) {
      throw new IllegalArgumentException("tenantId must not be null");
    }
    UUID previous = CURRENT.get();
    CURRENT.set(tenantId);
    try {
      return action.get();
    } finally {
      restore(previous);
    }
  }

  static void set(UUID tenantId) {
    CURRENT.set(tenantId);
  }

  static void clear() {
    CURRENT.remove();
  }

  private static void restore(UUID previous) {
    if (previous == null) {
      CURRENT.remove();
    } else {
      CURRENT.set(previous);
    }
  }
}
