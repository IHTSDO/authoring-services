package org.ihtsdo.authoringservices.rest;

import com.google.common.base.Strings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.ihtsdo.authoringservices.service.NotificationService;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Notifications")
@RestController
@ConditionalOnProperty(name = "authoring.notifications.sse.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationStreamController {

	private final NotificationService notificationService;

	public NotificationStreamController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@Operation(summary = "Subscribe to authoring notifications via Server-Sent Events",
			description = "Opens a long-lived HTTP stream of notification events for the authenticated user. "
					+ "Each payload is a JSON Notification on the `notification` event. "
					+ "The stream is one-way (server to client); use the REST API for writes. "
					+ "Native EventSource cannot set IMS auth headers — call this through the IMS proxy "
					+ "(cookies + injected X-AUTH-* headers) or use fetch() with those headers.")
	@GetMapping(value = "/notifications", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter subscribe(HttpServletResponse response) {
		String username = SecurityUtil.getUsername();
		if (Strings.isNullOrEmpty(username)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
		}
		response.setHeader("Cache-Control", "no-cache");
		response.setHeader("X-Accel-Buffering", "no");
		response.setHeader("Connection", "keep-alive");
		return notificationService.subscribe(username);
	}
}
