package com.tenant.management.service;

import com.tenant.management.dto.CreateUserRequest;
import com.tenant.management.dto.UpdateUserRequest;
import com.tenant.management.dto.UserResponse;
import com.tenant.management.entity.MemberRole;
import com.tenant.management.entity.Membership;
import com.tenant.management.entity.User;
import com.tenant.management.exception.ConflictException;
import com.tenant.management.exception.ResourceNotFoundException;
import com.tenant.management.repository.MembershipRepository;
import com.tenant.management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessService accessService;

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        Long currentUserId = accessService.currentUserId();
        Set<User> users = new LinkedHashSet<>(userRepository.findUsersSharingTenantsWith(currentUserId));
        users.add(getUser(currentUserId));
        return users.stream().map(UserResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = getUser(id);
        accessService.requireSelfOrSharedTenant(id);
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("User with email '" + email + "' already exists");
        }

        User user = User.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .active(true)
                .build();

        return UserResponse.fromEntity(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        accessService.requireSelf(id);
        User user = getUser(id);

        if (request.getFirstName() != null) {
            user.setFirstName(requireText(request.getFirstName(), "First name cannot be blank"));
        }
        if (request.getLastName() != null) {
            user.setLastName(requireText(request.getLastName(), "Last name cannot be blank"));
        }
        if (request.getEmail() != null) {
            String email = normalizeEmail(request.getEmail());
            if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmailIgnoreCase(email)) {
                throw new ConflictException("User with email '" + email + "' already exists");
            }
            user.setEmail(email);
        }
        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }
        if (request.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        return UserResponse.fromEntity(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long id) {
        accessService.requireSelf(id);
        User user = getUser(id);
        List<Membership> memberships = membershipRepository.findAllByUserId(id);
        for (Membership membership : memberships) {
            if (membership.getRole() == MemberRole.OWNER
                    && membershipRepository.countByTenantIdAndRole(membership.getTenant().getId(), MemberRole.OWNER) <= 1) {
                throw new ConflictException(
                        "Cannot delete user who is the last owner of tenant '" + membership.getTenant().getName() + "'");
            }
        }
        userRepository.delete(user);
    }

    public User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    private String normalizeEmail(String email) {
        return requireText(email, "Email cannot be blank").toLowerCase();
    }

    private String requireText(String value, String message) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }
}
