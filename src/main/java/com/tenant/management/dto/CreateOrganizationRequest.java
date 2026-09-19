package com.tenant.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateOrganizationRequest {
    @NotBlank
    @Size(max = 150)
    private String name;

    @NotBlank
    @Size(max = 80)
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$")
    private String slug;

    @Size(max = 500)
    private String description;
}
