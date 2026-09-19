package com.tenant.management.dto;

import com.tenant.management.entity.MemberRole;
import com.tenant.management.entity.Membership;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MembershipResponse {
    private Long id;
    private Long organizationId;
    private String organizationName;
    private Long userId;
    private String userEmail;
    private MemberRole role;

    public static MembershipResponse from(Membership membership) {
        return MembershipResponse.builder()
                .id(membership.getId())
                .organizationId(membership.getOrganization().getId())
                .organizationName(membership.getOrganization().getName())
                .userId(membership.getUser().getId())
                .userEmail(membership.getUser().getEmail())
                .role(membership.getRole())
                .build();
    }
}
