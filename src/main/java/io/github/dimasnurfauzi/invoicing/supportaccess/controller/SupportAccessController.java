package io.github.dimasnurfauzi.invoicing.supportaccess.controller;

import io.github.dimasnurfauzi.invoicing.supportaccess.dto.AccessLogResponse;
import io.github.dimasnurfauzi.invoicing.supportaccess.dto.GrantRequest;
import io.github.dimasnurfauzi.invoicing.supportaccess.dto.GrantResponse;
import io.github.dimasnurfauzi.invoicing.supportaccess.service.SupportAccessService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** A tenant ADMIN controls and audits support access to their own tenant's data. */
@RestController
@RequestMapping("/api/support-access")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Support access (tenant)")
public class SupportAccessController {

  private final SupportAccessService service;

  public SupportAccessController(SupportAccessService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public GrantResponse grant(
      @Valid @RequestBody GrantRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return service.grant(request.durationHours(), UUID.fromString(jwt.getSubject()));
  }

  @GetMapping
  public List<GrantResponse> list() {
    return service.list();
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(@PathVariable UUID id) {
    service.revoke(id);
  }

  @GetMapping("/logs")
  public List<AccessLogResponse> logs() {
    return service.logs();
  }
}
