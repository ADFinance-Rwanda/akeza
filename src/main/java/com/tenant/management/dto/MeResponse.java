package com.tenant.management.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MeResponse {
    private UserResponse user;
    private boolean superAdmin;
    private List<MembershipResponse> organizations;
}
