package com.tenant.management.service;

import com.tenant.management.dto.CreateTenantRequest;
import com.tenant.management.dto.TenantResponse;
import com.tenant.management.entity.Tenant;
import com.tenant.management.entity.User;
import com.tenant.management.exception.ConflictException;
import com.tenant.management.exception.ResourceNotFoundException;
import com.tenant.management.repository.MembershipRepository;
import com.tenant.management.repository.TenantRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private MembershipRepository membershipRepository;
    @Mock
    private UserService userService;
    @Mock
    private AccessService accessService;

    @InjectMocks
    private TenantService tenantService;

    @Test
    void createTenantAssignsCurrentUserAsOwner() {
        when(tenantRepository.existsBySlugIgnoreCase("acme")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(1L);
            tenant.setCreatedAt(LocalDateTime.now());
            tenant.setUpdatedAt(LocalDateTime.now());
            return tenant;
        });
        when(accessService.currentUserId()).thenReturn(9L);
        when(userService.getUser(9L)).thenReturn(User.builder().id(9L).email("owner@example.com").build());

        TenantResponse response = tenantService.createTenant(CreateTenantRequest.builder()
                .name("Acme")
                .slug("Acme")
                .build());

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getSlug()).isEqualTo("acme");
        verify(membershipRepository).save(any());
    }

    @Test
    void createTenantRejectsDuplicateSlug() {
        when(tenantRepository.existsBySlugIgnoreCase("acme")).thenReturn(true);

        assertThatThrownBy(() -> tenantService.createTenant(CreateTenantRequest.builder()
                .name("Acme")
                .slug("acme")
                .build()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("acme");
    }

    @Test
    void getTenantByIdThrowsWhenMissing() {
        when(tenantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.getTenantById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
