package com.tenant.management.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ProbeController {

    private static final long READY_TIMEOUT_SECONDS = 3;

    private final HealthEndpoint healthEndpoint;

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            HealthComponent component = CompletableFuture
                    .supplyAsync(() -> healthEndpoint.healthForPath("readiness"))
                    .orTimeout(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .join();
            Status status = component == null ? Status.UNKNOWN : component.getStatus();
            boolean up = Status.UP.equals(status);
            body.put("status", status.getCode());
            return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
        } catch (Exception ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (cause instanceof TimeoutException) {
                log.warn("Readiness check timed out after {}s", READY_TIMEOUT_SECONDS);
            } else {
                log.warn("Readiness check failed: {}", cause.getMessage());
            }
            body.put("status", Status.DOWN.getCode());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
    }
}
