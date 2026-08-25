package org.ihtsdo.authoringservices.service;

import org.ihtsdo.authoringservices.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Component
public class SseNotificationSink {

	private static final long NO_TIMEOUT = 0L;

	private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public SseEmitter subscribe(String username) {
		SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
		emittersByUser.computeIfAbsent(username, key -> new CopyOnWriteArrayList<>()).add(emitter);

		Runnable cleanup = () -> remove(username, emitter);
		emitter.onCompletion(cleanup);
		emitter.onTimeout(cleanup);
		emitter.onError(_ -> cleanup.run());

		sendComment(username, emitter, "connected");
		return emitter;
	}

	public boolean hasSubscriber(String username) {
		if (username == null) {
			return false;
		}
		List<SseEmitter> emitters = emittersByUser.get(username);
		return emitters != null && !emitters.isEmpty();
	}

	public Set<String> getConnectedUsernames() {
		return emittersByUser.entrySet().stream()
				.filter(entry -> !entry.getValue().isEmpty())
				.map(Map.Entry::getKey)
				.collect(Collectors.toUnmodifiableSet());
	}

	public void send(String username, Notification notification) {
		List<SseEmitter> emitters = emittersByUser.get(username);
		if (emitters == null || emitters.isEmpty()) {
			return;
		}
		for (SseEmitter emitter : emitters) {
			synchronized (emitter) {
				try {
					emitter.send(SseEmitter.event()
							.name("notification")
							.data(notification, MediaType.APPLICATION_JSON));
				} catch (IOException | IllegalStateException e) {
					logger.debug("Failed to send SSE notification to {}", username, e);
					completeQuietly(emitter);
					remove(username, emitter);
				}
			}
		}
	}

	public void heartbeat() {
		for (var entry : emittersByUser.entrySet()) {
			String username = entry.getKey();
			for (SseEmitter emitter : entry.getValue()) {
				sendComment(username, emitter, "keepalive");
			}
		}
	}

	private void sendComment(String username, SseEmitter emitter, String comment) {
		synchronized (emitter) {
			try {
				emitter.send(SseEmitter.event().comment(comment));
			} catch (IOException | IllegalStateException e) {
				logger.debug("Failed to send SSE comment to {}", username, e);
				completeQuietly(emitter);
				remove(username, emitter);
			}
		}
	}

	private void remove(String username, SseEmitter emitter) {
		emittersByUser.computeIfPresent(username, (_, emitters) -> {
			emitters.remove(emitter);
			return emitters.isEmpty() ? null : emitters;
		});
	}

	private void completeQuietly(SseEmitter emitter) {
		try {
			emitter.complete();
		} catch (IllegalStateException _) {
			// Already completed.
		}
	}
}
