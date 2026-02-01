package com.leadboard.admin;

import com.leadboard.auth.AppRole;
import com.leadboard.auth.UserEntity;
import com.leadboard.auth.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin endpoints for user management.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;

    public AdminController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Get all users with their roles.
     */
    @GetMapping("/users")
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Update user role.
     */
    @PatchMapping("/users/{id}/role")
    public ResponseEntity<UserDto> updateUserRole(
            @PathVariable Long id,
            @RequestBody UpdateRoleRequest request) {

        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));

        try {
            AppRole newRole = AppRole.valueOf(request.role());
            user.setAppRole(newRole);
            userRepository.save(user);
            return ResponseEntity.ok(toDto(user));
        } catch (IllegalArgumentException e) {
            throw new InvalidRoleException("Invalid role: " + request.role());
        }
    }

    private UserDto toDto(UserEntity user) {
        return new UserDto(
                user.getId(),
                user.getAtlassianAccountId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getAppRole().name()
        );
    }

    public record UserDto(
            Long id,
            String accountId,
            String displayName,
            String email,
            String avatarUrl,
            String role
    ) {}

    public record UpdateRoleRequest(String role) {}

    @ResponseStatus(org.springframework.http.HttpStatus.NOT_FOUND)
    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String message) {
            super(message);
        }
    }

    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public static class InvalidRoleException extends RuntimeException {
        public InvalidRoleException(String message) {
            super(message);
        }
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFound(UserNotFoundException e) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(InvalidRoleException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRole(InvalidRoleException e) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
    }
}
