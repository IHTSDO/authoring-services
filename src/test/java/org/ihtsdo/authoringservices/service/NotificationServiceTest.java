package org.ihtsdo.authoringservices.service;

import org.ihtsdo.authoringservices.domain.EntityType;
import org.ihtsdo.authoringservices.domain.Notification;
import org.ihtsdo.authoringservices.domain.NotificationSeverity;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Classification;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ClassificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	@Mock
	private BranchService branchService;

	@Mock
	private SnowstormClassificationClient classificationClient;

	@InjectMocks
	private NotificationService notificationService;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(notificationService, "branchService", branchService);
		ReflectionTestUtils.setField(notificationService, "classificationClient", classificationClient);
	}

	@Test
	void enrichPromotionTaskLevel() {
		Notification notification = new Notification("WRPAS", "WRPAS-76", EntityType.Promotion, "Task successfully promoted");

		ReflectionTestUtils.invokeMethod(notificationService, "enrichNotification", notification);

		assertEquals("Task successfully promoted for WRPAS-76", notification.getNotificationMessage());
		assertEquals("/tasks/WRPAS/WRPAS-76/edit", notification.getDeepLinkPath());
		assertEquals(NotificationSeverity.INFO, notification.getSeverity());
		assertTrue(notification.getRequiresRefetch().contains("promotion"));
		assertTrue(notification.getRequiresRefetch().contains("task"));
	}

	@Test
	void enrichFeedbackCapitalizesEvent() {
		Notification notification = new Notification("WRPAS", "WRPAS-76", EntityType.Feedback, "new");

		ReflectionTestUtils.invokeMethod(notificationService, "enrichNotification", notification);

		assertEquals("New feedback for task WRPAS-76", notification.getNotificationMessage());
		assertEquals("/tasks/WRPAS/WRPAS-76/feedback", notification.getDeepLinkPath());
		assertEquals(NotificationSeverity.INFO, notification.getSeverity());
	}

	@Test
	void enrichClassificationRunningDoesNotSetMessage() {
		Notification notification = new Notification("WRPAS", "WRPAS-98", EntityType.Classification, "Classification is running");
		notification.setBranchPath("MAIN/WRPAS/WRPAS-98");

		ReflectionTestUtils.invokeMethod(notificationService, "enrichNotification", notification);

		assertNull(notification.getNotificationMessage());
		assertTrue(notification.getRequiresRefetch().contains("classification"));
		assertTrue(notification.getRequiresRefetch().contains("task"));
	}

	@Test
	void enrichClassificationCompletedWithChanges() throws RestClientException {
		Notification notification = new Notification("WRPAS", "WRPAS-98", EntityType.Classification, "Classification completed successfully");
		notification.setBranchPath("MAIN/WRPAS/WRPAS-98");

		Classification classification = mock(Classification.class);
		when(classification.getStatus()).thenReturn(ClassificationStatus.COMPLETED);
		when(classification.hasEquivalentConceptsFound()).thenReturn(true);
		when(classificationClient.getLatestClassification("MAIN/WRPAS/WRPAS-98")).thenReturn(classification);

		ReflectionTestUtils.invokeMethod(notificationService, "enrichNotification", notification);

		assertEquals("Classification completed successfully for task WRPAS-98 - Changes found", notification.getNotificationMessage());
		assertEquals("/tasks/WRPAS/WRPAS-98/classify", notification.getDeepLinkPath());
		assertEquals(NotificationSeverity.INFO, notification.getSeverity());
	}

	@Test
	void enrichValidationFailedHasNoDeepLink() {
		Notification notification = new Notification("WRPAS", "WRPAS-98", EntityType.Validation, "FAILED");
		notification.setBranchPath("MAIN/WRPAS/WRPAS-98");

		ReflectionTestUtils.invokeMethod(notificationService, "enrichNotification", notification);

		assertEquals("Validation Failed for task WRPAS-98", notification.getNotificationMessage());
		assertEquals(NotificationSeverity.ERROR, notification.getSeverity());

		assertEquals("/tasks/WRPAS/WRPAS-98/validate", notification.getDeepLinkPath());
		assertTrue(notification.getRequiresRefetch().contains("validation"));
	}

	@Test
	void enrichValidationCompletedSetsDeepLink() {
		Notification notification = new Notification("WRPAS", "WRPAS-98", EntityType.Validation, "COMPLETED");
		notification.setBranchPath("MAIN/WRPAS/WRPAS-98");

		ReflectionTestUtils.invokeMethod(notificationService, "enrichNotification", notification);

		assertEquals("Validation Completed for task WRPAS-98", notification.getNotificationMessage());
		assertEquals("/tasks/WRPAS/WRPAS-98/validate", notification.getDeepLinkPath());
		assertEquals(NotificationSeverity.INFO, notification.getSeverity());
	}

	@Test
	void enrichBranchStateDivergedSetsRefetchHint() {
		Notification notification = new Notification("WRPAS", "WRPAS-98", EntityType.BranchState, "DIVERGED");

		ReflectionTestUtils.invokeMethod(notificationService, "enrichNotification", notification);

		assertEquals(List.of("branchState"), notification.getRequiresRefetch());
	}
}
