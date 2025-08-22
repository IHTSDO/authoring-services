package org.ihtsdo.authoringservices.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ihtsdo.authoringservices.service.UserCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for managing user cache operations.
 * Provides endpoints for cache statistics and management.
 */
@Tag(name = "User Cache Management")
@RestController
@RequestMapping(value = "/admin/user-cache", produces = {MediaType.APPLICATION_JSON_VALUE})
public class UserCacheController {

    @Autowired
    private UserCacheService userCacheService;

    @Operation(summary = "Get user cache statistics")
    @PreAuthorize("hasPermission('ADMIN', 'global')")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        Map<String, Object> stats = userCacheService.getCacheStats();
        return ResponseEntity.ok(stats);
    }

    @Operation(summary = "Clear user cache")
    @PreAuthorize("hasPermission('ADMIN', 'global')")
    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, String>> clearCache() {
        userCacheService.clearCache();
        return ResponseEntity.ok(Map.of("message", "User cache cleared successfully"));
    }
}
