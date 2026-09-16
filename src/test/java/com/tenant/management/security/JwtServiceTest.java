package com.tenant.management.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "test-jwt-secret-key-that-is-at-least-32-characters",
            3600000
    );

    @Test
    void generatesAndParsesToken() {
        UserPrincipal principal = UserPrincipal.builder()
                .id(42L)
                .email("ada@example.com")
                .password("hashed")
                .active(true)
                .build();

        String token = jwtService.generateToken(principal);

        assertThat(jwtService.isValid(token)).isTrue();
        assertThat(jwtService.parseUserId(token)).isEqualTo(42L);
        assertThat(jwtService.isValid("not-a-token")).isFalse();
    }
}
