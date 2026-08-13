package org.ihtsdo.authoringservices.service;

import org.ihtsdo.authoringservices.service.ClassificationPrerequisiteService.ClassificationPrerequisiteResult;
import org.ihtsdo.authoringservices.service.ClassificationPrerequisiteService.Context;
import org.ihtsdo.authoringservices.service.client.TraceabilityClient;
import org.ihtsdo.authoringservices.service.client.TraceabilityClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Branch;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Classification;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ClassificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassificationPrerequisiteServiceTest {

	@Mock
	private SnowstormClassificationClient classificationClient;
	@Mock
	private TraceabilityClientFactory traceabilityClientFactory;
	@Mock
	private Branch branch;
	@Mock
	private Classification classification;

	private ClassificationPrerequisiteService service;

	@BeforeEach
	void setUp() {
		service = new ClassificationPrerequisiteService(classificationClient, traceabilityClientFactory);
	}

	@Test
	void evaluate_review_marksCurrentCompletedClassification() {
		when(branch.getHeadTimestamp()).thenReturn(1_000L);
		when(classification.getStatus()).thenReturn(ClassificationStatus.COMPLETED);
		when(classification.getCreationDate()).thenReturn(new Date(2_000L));
		when(classification.getEquivalentConceptsFound()).thenReturn(false);
		when(classification.getInferredRelationshipChangesFound()).thenReturn(false);
		when(classification.getRedundantStatedRelationshipsFound()).thenReturn(false);

		ClassificationPrerequisiteResult result = service.evaluate(branch, classification, emptyActivities(), Context.REVIEW);

		assertTrue(result.classificationCurrent());
		assertEquals("COMPLETED", result.classificationStatus());
		assertFalse(result.equivalenciesFound());
		assertTrue(result.blockers().isEmpty());
	}

	@Test
	void evaluate_promotion_marksStaleWhenNotCurrent() {
		when(branch.getHeadTimestamp()).thenReturn(5_000L);
		when(classification.getStatus()).thenReturn(ClassificationStatus.COMPLETED);
		when(classification.getCreationDate()).thenReturn(new Date(1_000L));
		when(classification.getEquivalentConceptsFound()).thenReturn(false);
		when(classification.getInferredRelationshipChangesFound()).thenReturn(false);
		when(classification.getRedundantStatedRelationshipsFound()).thenReturn(false);

		ClassificationPrerequisiteResult result = service.evaluate(branch, classification, emptyActivities(), Context.PROMOTION);

		assertFalse(result.classificationCurrent());
		assertEquals("STALE", result.classificationStatus());
		assertTrue(result.blockers().stream().anyMatch(b -> b.startsWith("Classification Not Current")));
	}

	@Test
	void evaluate_promotion_blocksRunningClassification() {
		when(classification.getStatus()).thenReturn(ClassificationStatus.RUNNING);
		when(classification.getEquivalentConceptsFound()).thenReturn(false);
		when(classification.getInferredRelationshipChangesFound()).thenReturn(false);
		when(classification.getRedundantStatedRelationshipsFound()).thenReturn(false);

		ClassificationPrerequisiteResult result = service.evaluate(branch, classification, emptyActivities(), Context.PROMOTION);

		assertEquals("RUNNING", result.classificationStatus());
		assertTrue(result.blockers().contains("Classification is currently running"));
	}

	@Test
	void evaluate_review_blocksEquivalencies() {
		when(branch.getHeadTimestamp()).thenReturn(1_000L);
		when(classification.getStatus()).thenReturn(ClassificationStatus.COMPLETED);
		when(classification.getCreationDate()).thenReturn(new Date(2_000L));
		when(classification.getEquivalentConceptsFound()).thenReturn(true);

		ClassificationPrerequisiteResult result = service.evaluate(branch, classification, emptyActivities(), Context.REVIEW);

		assertTrue(result.equivalenciesFound());
		assertTrue(result.blockers().stream().anyMatch(b -> b.contains("submit for review")));
	}

	private static TraceabilityClient.ActivitiesPage emptyActivities() {
		TraceabilityClient.ActivitiesPage page = new TraceabilityClient.ActivitiesPage();
		page.setContent(List.of());
		page.setNumberOfElements(0);
		return page;
	}
}
