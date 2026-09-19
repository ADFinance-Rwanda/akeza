package com.tenant.management.service;

import com.tenant.management.entity.User;
import com.tenant.management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserSyncService {

    private final UserRepository userRepository;

    @Transactional
    public User sync(Jwt jwt) {
        String sub = jwt.getSubject();
        String email = claim(jwt, "email", sub + "@users.local");
        String firstName = claim(jwt, "given_name", "User");
        String lastName = claim(jwt, "family_name", "Account");
        boolean superAdmin = hasRealmRole(jwt, "SUPER_ADMIN");

        User user = userRepository.findByKeycloakSub(sub)
                .or(() -> userRepository.findByEmailIgnoreCase(email))
                .orElse(null);
        if (user == null) {
            try {
                return userRepository.save(User.builder()
                        .keycloakSub(sub)
                        .email(email)
                        .firstName(firstName)
                        .lastName(lastName)
                        .active(true)
                        .superAdmin(superAdmin)
                        .build());
            } catch (DataIntegrityViolationException ex) {
                return userRepository.findByKeycloakSub(sub)
                        .or(() -> userRepository.findByEmailIgnoreCase(email))
                        .orElseThrow(() -> ex);
            }
        }
        user.setKeycloakSub(sub);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setSuperAdmin(superAdmin);
        return userRepository.save(user);
    }

    private boolean hasRealmRole(Jwt jwt, String role) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> map) {
            Object roles = map.get("roles");
            if (roles instanceof Collection<?> collection) {
                return collection.stream().map(String::valueOf).anyMatch(role::equals);
            }
        }
        return false;
    }

    private String claim(Jwt jwt, String name, String fallback) {
        String value = jwt.getClaimAsString(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
