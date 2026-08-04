package org.ihtsdo.authoringservices.service;

import org.ihtsdo.authoringservices.service.client.TraceabilityClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewConceptAggregationServiceTest {

	@Test
	void splitConceptChanges_placesStatedEditsInContentAndInferredInClassification() {
		TraceabilityClient.ActivitiesPage page = page(
				contentChange("111", "DESCRIPTION"),
				contentChange("222", "INFERRED_RELATIONSHIP"),
				classificationSave("333")
		);

		ReviewConceptAggregationService.ChangedConceptSets result =
				ReviewConceptAggregationService.splitConceptChanges(page);

		assertEquals(List.of("111"), result.contentConceptIds());
		assertEquals(List.of("222", "333"), result.classificationConceptIds());
	}

	@Test
	void splitConceptChanges_removesContentConceptsFromClassificationList() {
		TraceabilityClient.ActivitiesPage page = page(
				contentChange("111", "DESCRIPTION"),
				classificationSave("111"),
				classificationSave("222")
		);

		ReviewConceptAggregationService.ChangedConceptSets result =
				ReviewConceptAggregationService.splitConceptChanges(page);

		assertEquals(List.of("111"), result.contentConceptIds());
		assertEquals(List.of("222"), result.classificationConceptIds());
		assertFalse(result.classificationConceptIds().contains("111"));
	}

	@Test
	void splitConceptChanges_deduplicatesContentConceptsAcrossActivities() {
		TraceabilityClient.ActivitiesPage page = page(
				contentChange("111", "DESCRIPTION"),
				contentChange("111", "RELATIONSHIP")
		);

		ReviewConceptAggregationService.ChangedConceptSets result =
				ReviewConceptAggregationService.splitConceptChanges(page);

		assertEquals(1, result.contentConceptIds().size());
		assertEquals("111", result.contentConceptIds().get(0));
		assertTrue(result.classificationConceptIds().isEmpty());
	}

	@Test
	void splitConceptChanges_handlesNumericConceptIds() {
		TraceabilityClient.ConceptChange concept = new TraceabilityClient.ConceptChange();
		concept.setConceptId(123456789L);
		concept.setComponentChanges(List.of(component("AXIOM")));

		TraceabilityClient.Activity activity = new TraceabilityClient.Activity();
		activity.setActivityType("CONTENT_CHANGE");
		activity.setCommitDate(new Date());
		activity.setConceptChanges(List.of(concept));

		TraceabilityClient.ActivitiesPage page = new TraceabilityClient.ActivitiesPage();
		page.setContent(List.of(activity));

		ReviewConceptAggregationService.ChangedConceptSets result =
				ReviewConceptAggregationService.splitConceptChanges(page);

		assertEquals(List.of("123456789"), result.contentConceptIds());
	}

	@Test
	void splitConceptChanges_returnsEmptyForNullActivities() {
		ReviewConceptAggregationService.ChangedConceptSets result =
				ReviewConceptAggregationService.splitConceptChanges(null);

		assertTrue(result.contentConceptIds().isEmpty());
		assertTrue(result.classificationConceptIds().isEmpty());
	}

	private static TraceabilityClient.ActivitiesPage page(TraceabilityClient.Activity... activities) {
		TraceabilityClient.ActivitiesPage page = new TraceabilityClient.ActivitiesPage();
		List<TraceabilityClient.Activity> content = new ArrayList<>();
		for (TraceabilityClient.Activity activity : activities) {
			content.add(activity);
		}
		page.setContent(content);
		page.setNumberOfElements(content.size());
		return page;
	}

	private static TraceabilityClient.Activity contentChange(String conceptId, String componentSubType) {
		TraceabilityClient.Activity activity = new TraceabilityClient.Activity();
		activity.setActivityType("CONTENT_CHANGE");
		activity.setCommitDate(new Date());
		activity.setConceptChanges(List.of(conceptChange(conceptId, componentSubType)));
		return activity;
	}

	private static TraceabilityClient.Activity classificationSave(String conceptId) {
		TraceabilityClient.Activity activity = new TraceabilityClient.Activity();
		activity.setActivityType("CLASSIFICATION_SAVE");
		activity.setCommitDate(new Date());
		activity.setConceptChanges(List.of(conceptChange(conceptId, "INFERRED_RELATIONSHIP")));
		return activity;
	}

	private static TraceabilityClient.ConceptChange conceptChange(String conceptId, String componentSubType) {
		TraceabilityClient.ConceptChange concept = new TraceabilityClient.ConceptChange();
		concept.setConceptId(conceptId);
		concept.setComponentChanges(List.of(component(componentSubType)));
		return concept;
	}

	private static TraceabilityClient.ComponentChange component(String componentSubType) {
		TraceabilityClient.ComponentChange change = new TraceabilityClient.ComponentChange();
		change.setComponentSubType(componentSubType);
		return change;
	}
}
