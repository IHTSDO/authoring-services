package org.ihtsdo.authoringservices.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ihtsdo.authoringservices.domain.User;
import org.ihtsdo.authoringservices.entity.RMPNotificationUser;
import org.ihtsdo.authoringservices.service.RMPUserNotificationService;
import org.ihtsdo.authoringservices.service.UserCacheService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Tag(name = "RMP Notification Users")
@RestController
@RequestMapping(value = "/rmp-notification-users", produces = {MediaType.APPLICATION_JSON_VALUE})
public class RMPNotificationUserController {

    private final RMPUserNotificationService rmpUserNotificationService;

    private final UserCacheService userCacheService;

    public RMPNotificationUserController(RMPUserNotificationService rmpUserNotificationService, UserCacheService userCacheService) {
        this.rmpUserNotificationService = rmpUserNotificationService;
        this.userCacheService = userCacheService;
    }

    @GetMapping
    @Operation(summary = "Get RMP notification users , optionally filtered by country")
    public List<RMPNotificationUser> getNotificationUsers(
            @RequestParam(value = "country", required = false) String country) {
        return rmpUserNotificationService.getNotificationUsers(country);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single RMP notification user by id")
    public ResponseEntity<RMPNotificationUser> getNotificationUserById(@PathVariable long id) {
        Optional<RMPNotificationUser> notification = rmpUserNotificationService.getNotificationUserById(id);
        return ResponseEntity.of(notification);
    }

    @PreAuthorize("hasPermission('ADMIN', 'global')")
    @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE})
    @Operation(summary = "Create a new RMP notification user")
    public ResponseEntity<RMPNotificationUser> createNotificationUser(@RequestBody RMPNotificationUser notification) {
        if (!StringUtils.hasText(notification.getCountry())) {
            return ResponseEntity.badRequest().build();
        }
        if (!StringUtils.hasText(notification.getUser())) {
            return ResponseEntity.badRequest().build();
        }
        User user = userCacheService.getUser(notification.getUser());
        if (user == null || user.getDisplayName() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (rmpUserNotificationService.existsByCountryAndUser(notification.getCountry(), notification.getUser())) {
            return ResponseEntity.status(409).build();
        }

        return ResponseEntity.ok(rmpUserNotificationService.createNotificationUser(notification));
    }

    @PreAuthorize("hasPermission('ADMIN', 'global')")
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an RMP user notification by id")
    public ResponseEntity<Void> deleteNotificationUser(@PathVariable long id) {
        if (rmpUserNotificationService.deleteNotificationUser(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}

