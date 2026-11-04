package io.github.dimasnurfauzi.invoicing.support;

import io.github.dimasnurfauzi.invoicing.ai.client.InvoiceDraftModel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Test double for the Claude API: replies are scripted per test, prompts are recorded. Registered
 * as the primary {@link InvoiceDraftModel} in {@code TestcontainersConfiguration}, so the shared
 * test context never calls the real API. Call {@link #reset()} in {@code @BeforeEach}.
 */
public class ScriptedInvoiceDraftModel implements InvoiceDraftModel {

  private final Deque<Reply> replies = new ArrayDeque<>();
  private final List<String> userPrompts = new ArrayList<>();
  private boolean configured = true;

  public synchronized void reset() {
    replies.clear();
    userPrompts.clear();
    configured = true;
  }

  public synchronized void reply(Reply... scripted) {
    replies.addAll(List.of(scripted));
  }

  public synchronized void setConfigured(boolean configured) {
    this.configured = configured;
  }

  public synchronized List<String> userPrompts() {
    return List.copyOf(userPrompts);
  }

  @Override
  public synchronized boolean isConfigured() {
    return configured;
  }

  @Override
  public synchronized Reply complete(String system, String user) {
    userPrompts.add(user);
    if (replies.isEmpty()) {
      throw new IllegalStateException("No scripted reply left");
    }
    return replies.poll();
  }
}
