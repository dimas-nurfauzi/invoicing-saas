package io.github.dimasnurfauzi.invoicing.common;

/**
 * Maps to 404. Thrown both when a row doesn't exist and when it belongs to another tenant (the
 * tenant filter makes the two indistinguishable), so the response never reveals which.
 */
public class NotFoundException extends RuntimeException {

  public NotFoundException(String resource) {
    super(resource + " not found");
  }
}
