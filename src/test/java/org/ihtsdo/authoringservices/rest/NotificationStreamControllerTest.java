package org.ihtsdo.authoringservices.rest;

import org.ihtsdo.authoringservices.service.NotificationService;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationStreamControllerTest {

	@Mock
	private NotificationService notificationService;

	@Test
	void subscribeRequiresAuthenticatedUser() {
		NotificationStreamController controller = new NotificationStreamController(notificationService);
		MockHttpServletResponse response = new MockHttpServletResponse();

		try (MockedStatic<SecurityUtil> security = mockStatic(SecurityUtil.class)) {
			security.when(SecurityUtil::getUsername).thenReturn(null);

			ResponseStatusException exception = assertThrows(ResponseStatusException.class,
					() -> controller.subscribe(response));

			assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
		}
	}

	@Test
	void subscribeOpensStreamForAuthenticatedUser() {
		NotificationStreamController controller = new NotificationStreamController(notificationService);
		MockHttpServletResponse response = new MockHttpServletResponse();
		SseEmitter emitter = new SseEmitter(0L);
		when(notificationService.subscribe("alice")).thenReturn(emitter);

		try (MockedStatic<SecurityUtil> security = mockStatic(SecurityUtil.class)) {
			security.when(SecurityUtil::getUsername).thenReturn("alice");

			SseEmitter result = controller.subscribe(response);

			assertSame(emitter, result);
			assertEquals("no-cache", response.getHeader("Cache-Control"));
			assertEquals("no", response.getHeader("X-Accel-Buffering"));
			verify(notificationService).subscribe("alice");
		}
	}
}
