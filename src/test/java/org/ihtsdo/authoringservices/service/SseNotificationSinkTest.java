package org.ihtsdo.authoringservices.service;

import org.ihtsdo.authoringservices.domain.EntityType;
import org.ihtsdo.authoringservices.domain.Notification;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SseNotificationSinkTest {

	@Test
	void subscribeTracksConnectedUser() {
		SseNotificationSink sink = new SseNotificationSink();

		SseEmitter emitter = sink.subscribe("alice");

		assertTrue(sink.hasSubscriber("alice"));
		assertEquals(Set.of("alice"), sink.getConnectedUsernames());
		assertFalse(sink.hasSubscriber("bob"));
		assertFalse(sink.hasSubscriber(null));
	}

	@Test
	void sendAndHeartbeatDoNotThrowForConnectedUser() {
		SseNotificationSink sink = new SseNotificationSink();
		sink.subscribe("alice");

		sink.send("alice", new Notification("WRPAS", "WRPAS-76", EntityType.Feedback, "new"));
		sink.send("bob", new Notification("WRPAS", EntityType.BranchState, "DIVERGED"));
		sink.heartbeat();

		assertTrue(sink.hasSubscriber("alice"));
	}
}
