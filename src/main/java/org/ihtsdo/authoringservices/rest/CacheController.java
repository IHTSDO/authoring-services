package org.ihtsdo.authoringservices.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ihtsdo.authoringservices.service.CacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cache")
@Tag(name = "Cache", description = "-")
public class CacheController {

    private final CacheService cacheService;

    @Autowired
    public CacheController(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Operation(summary = "Clear branch cache", description = "-")
    @PostMapping(value = "/clear-branch-cache")
    public ResponseEntity<Void> clearCacheControllerBranchCache(
            @RequestParam(required = false) String branchPath,
            @RequestParam(required = false) String branchPathStartWith
    ) {
        if (branchPath != null) {
            cacheService.clearBranchCache(branchPath);
        }
        if (branchPathStartWith != null) {
            cacheService.clearBranchCacheStartWith(branchPathStartWith);
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
