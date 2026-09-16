package com.tenant.management.service;

import com.tenant.management.dto.CreateMembershipRequest;
import com.tenant.management.dto.MembershipResponse;
import com.tenant.management.entity.MemberRole;
import com.tenant.management.entity.Membership;
import com.tenant.management.entity.Tenant;
import com.tenant.management.entity.User;
import com.tenant.management.exception.ConflictException;
import com.tenant.management.exception.ResourceNotFoundException;
import com.tenant.management.repository.MembershipRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {

    @Mock
    private MembershipRepository membershipRepository;
    @Mock
    private TenantService tenantService;
    @Mock
    private UserService userService;
    @Mock
    private AccessService accessService;

    @InjectMocks
    private MembershipService membershipService;

    @Test
    void addMemberCreatesMembership() {
        Tenant tenant = Tenant.builder().id(1L).name("Acme").slug("acme").build();
        User user = User.builder().id(2L).email("member@example.com").firstName("Mem").lastName("Ber").build();
        when(tenantService.getTenant(1L)).thenReturn(tenant);
        when(userService.getUser(2L)).thenReturn(user);
        when(membershipRepository.existsByTenantIdAndUserId(1L, 2L)).thenReturn(false);
        when(membershipRepository.save(any(Membership.class))).thenAnswer(invocation -> {
            Membership membership = invocation.getArgument(0);
            membership.setId(10L);
            membership.setCreatedAt(LocalDateTime.now());
            membership.setUpdatedAt(LocalDateTime.now());
            return membership;
        });

        MembershipResponse response = membershipService.addMember(1L, CreateMembershipRequest.builder()
                .userId(2L)
                .role(MemberRole.MEMBER)
                .build());

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getRole()).isEqualTo(MemberRole.MEMBER);
        assertThat(response.getUserEmail()).isEqualTo("member@example.com");
    }

    @Test
    void addMemberRejectsDuplicate() {
        when(tenantService.getTenant(1L)).thenReturn(Tenant.builder().id(1L).build());
        when(userService.getUser(2L)).thenReturn(User.builder().id(2L).build());
        when(membershipRepository.existsByTenantIdAndUserId(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> membershipService.addMember(1L, CreateMembershipRequest.builder()
                .userId(2L)
                .role(MemberRole.MEMBER)
                .build()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already a member");
    }

    @Test
    void removeMemberRejectsLastOwner() {
        Tenant tenant = Tenant.builder().id(1L).name("Acme").build();
        User user = User.builder().id(9L).build();
        Membership membership = Membership.builder()
                .id(3L)
                .tenant(tenant)
                .user(user)
                .role(MemberRole.OWNER)
                .build();
        when(tenantService.getTenant(1L)).thenReturn(tenant);
        when(userService.getUser(9L)).thenReturn(user);
        when(membershipRepository.findByTenantIdAndUserId(1L, 9L)).thenReturn(Optional.of(membership));
        when(membershipRepository.countByTenantIdAndRole(1L, MemberRole.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> membershipService.removeMember(1L, 9L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("last owner");
    }

    @Test
    void getMembershipThrowsWhenMissing() {
        when(tenantService.getTenant(1L)).thenReturn(Tenant.builder().id(1L).build());
        when(userService.getUser(2L)).thenReturn(User.builder().id(2L).build());
        when(membershipRepository.findByTenantIdAndUserId(1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> membershipService.removeMember(1L, 2L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
