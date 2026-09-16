package com.tenant.management.service;

import com.tenant.management.dto.AuthResponse;
import com.tenant.management.dto.CreateUserRequest;
import com.tenant.management.dto.LoginRequest;
import com.tenant.management.dto.UserResponse;
import com.tenant.management.entity.User;
import com.tenant.management.exception.InvalidCredentialsException;
import com.tenant.management.repository.UserRepository;
import com.tenant.management.security.CustomUserDetailsService;
import com.tenant.management.security.JwtService;
import com.tenant.management.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AccessService accessService;

    @Transactional
    public AuthResponse register(CreateUserRequest request) {
        UserResponse created = userService.createUser(request);
        User user = userRepository.findById(created.getId()).orElseThrow();
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.getEmail().trim())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!user.isActive() || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        return userService.getUserById(accessService.currentUserId());
    }

    private AuthResponse toAuthResponse(User user) {
        UserPrincipal principal = CustomUserDetailsService.toPrincipal(user);
        return AuthResponse.builder()
                .accessToken(jwtService.generateToken(principal))
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationMs() / 1000)
                .user(UserResponse.fromEntity(user))
                .build();
    }
}
