package io.github.dimasnurfauzi.invoicing.user.service;

import io.github.dimasnurfauzi.invoicing.common.exception.ConflictException;
import io.github.dimasnurfauzi.invoicing.common.exception.NotFoundException;
import io.github.dimasnurfauzi.invoicing.user.dto.CreateUserRequest;
import io.github.dimasnurfauzi.invoicing.user.dto.UserResponse;
import io.github.dimasnurfauzi.invoicing.user.entity.Role;
import io.github.dimasnurfauzi.invoicing.user.entity.User;
import io.github.dimasnurfauzi.invoicing.user.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User management within the caller's tenant. No method takes a tenant id: every repository call is
 * scoped by the tenant filter, so another tenant's user is simply "not found".
 */
@Service
public class UserService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;

  public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional(readOnly = true)
  public List<UserResponse> list() {
    return users.findAllByOrderByCreatedAtAscEmailAsc().stream().map(UserResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public UserResponse get(UUID id) {
    return UserResponse.from(find(id));
  }

  @Transactional
  public UserResponse create(CreateUserRequest request) {
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    if (users.existsByEmail(email)) {
      throw emailTaken();
    }
    try {
      User user =
          users.saveAndFlush(
              new User(email, passwordEncoder.encode(request.password()), request.role()));
      return UserResponse.from(user);
    } catch (DataIntegrityViolationException e) {
      throw emailTaken();
    }
  }

  @Transactional
  public UserResponse changeRole(UUID id, Role role, UUID currentUserId) {
    User user = find(id);
    rejectSelf(user, currentUserId);
    user.changeRole(role);
    return UserResponse.from(user);
  }

  @Transactional
  public void delete(UUID id, UUID currentUserId) {
    User user = find(id);
    rejectSelf(user, currentUserId);
    users.delete(user);
  }

  private User find(UUID id) {
    return users.findById(id).orElseThrow(() -> new NotFoundException("User"));
  }

  /** Keeps a tenant from locking itself out by removing or demoting its own (last) admin. */
  private static void rejectSelf(User user, UUID currentUserId) {
    if (user.getId().equals(currentUserId)) {
      throw new ConflictException("You cannot change or delete your own account");
    }
  }

  private static ConflictException emailTaken() {
    return new ConflictException("A user with this email already exists");
  }
}
