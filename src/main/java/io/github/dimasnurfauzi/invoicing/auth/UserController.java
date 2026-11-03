package io.github.dimasnurfauzi.invoicing.auth;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Users of the caller's tenant. Any role may read; only ADMIN may change. */
@RestController
@RequestMapping("/api/users")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping
  public List<UserResponse> list() {
    return userService.list();
  }

  @GetMapping("/{id}")
  public UserResponse get(@PathVariable UUID id) {
    return userService.get(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
    return userService.create(request);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public UserResponse changeRole(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateUserRoleRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return userService.changeRole(id, request.role(), UUID.fromString(jwt.getSubject()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  public void delete(
      @PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    userService.delete(id, UUID.fromString(jwt.getSubject()));
  }
}
