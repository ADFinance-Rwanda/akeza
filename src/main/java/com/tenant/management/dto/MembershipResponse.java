package com.tenant.management.dto;

import com.tenant.management.entity.MemberRole;
import com.tenant.management.entity.Membership;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MembershipResponse {

    private Long id;
    private Long tenantId;
    private String tenantName;
    private String tenantSlug;
    private Long userId;
    private String userEmail;
    private String userFirstName;
    private String userLastName;
    private MemberRole role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static MembershipResponse fromEntity(Membership membership) {
        return MembershipResponse.builder()
                .id(membership.getId())
                .tenantId(membership.getTenant().getId())
                .tenantName(membership.getTenant().getName())
                .tenantSlug(membership.getTenant().getSlug())
                .userId(membership.getUser().getId())
                .userEmail(membership.getUser().getEmail())
                .userFirstName(membership.getUser().getFirstName())
                .userLastName(membership.getUser().getLastName())
                .role(membership.getRole())
                .createdAt(membership.getCreatedAt())
                .updatedAt(membership.getUpdatedAt())
                .build();
    }
}
