package com.tenant.management.dto;

import com.tenant.management.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private boolean active;
    private boolean superAdmin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .active(user.isActive())
                .superAdmin(user.isSuperAdmin())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
