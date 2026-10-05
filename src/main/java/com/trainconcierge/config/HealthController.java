package com.trainconcierge.config;

import com.trainconcierge.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Global health-check endpoint independent of Spring Actuator.
 * Useful for load balancer / Kubernetes liveness probes.
 *
 * GET /api/health  →  200 OK
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @Value("${spring.application.name}")
    private String appName;

    @Value("${info.app.version}")
    private String appVersion;

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> info = Map.of(
                "application", appName,
                "version", appVersion,
                "status", "UP",
                "timestamp", Instant.now().toString()
        );
        return ResponseEntity.ok(ApiResponse.ok("Service is healthy", info));
    }
}
