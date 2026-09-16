package com.tenant.management.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
    private String firstName;

    @Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
    private String lastName;

    @Email(message = "Email should be valid")
    @Size(min = 1, max = 150, message = "Email must be between 1 and 150 characters")
    private String email;

    private Boolean active;

    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    private String password;
}
