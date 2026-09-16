package com.tenant.management.service;

import com.tenant.management.dto.CreateUserRequest;
import com.tenant.management.dto.UserResponse;
import com.tenant.management.entity.User;
import com.tenant.management.exception.ConflictException;
import com.tenant.management.exception.ResourceNotFoundException;
import com.tenant.management.repository.MembershipRepository;
import com.tenant.management.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private MembershipRepository membershipRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AccessService accessService;

    @InjectMocks
    private UserService userService;

    @Test
    void createUserHashesPasswordAndNormalizesEmail() {
        when(userRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(5L);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            return user;
        });

        UserResponse response = userService.createUser(CreateUserRequest.builder()
                .firstName("Ada")
                .lastName("Lovelace")
                .email("Ada@Example.com")
                .password("Password123")
                .build());

        assertThat(response.getEmail()).isEqualTo("ada@example.com");
        assertThat(response.getId()).isEqualTo(5L);
    }

    @Test
    void createUserRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(CreateUserRequest.builder()
                .firstName("Ada")
                .lastName("Lovelace")
                .email("ada@example.com")
                .password("Password123")
                .build()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void getUserByIdThrowsWhenMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
