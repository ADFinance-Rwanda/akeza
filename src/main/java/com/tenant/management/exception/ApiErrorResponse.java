package com.tenant.management.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorResponse {

    private int status;
    private String message;
    private LocalDateTime timestamp;
    private Map<String, String> details;
}
