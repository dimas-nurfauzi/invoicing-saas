package io.github.dimasnurfauzi.invoicing.ai.exception;

/** 422: the model declined the request (even after the server-side fallback). */
public class AiRefusedException extends RuntimeException {

  public AiRefusedException() {
    super("The AI declined to draft an invoice from this text; enter the invoice manually");
  }
}
