package org.ihtsdo.authoringservices.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrsConceptPreparationTest {

	private static final String MODULE_ID = "900000000000207008";
	private static final String ISA = "116680003";
	private static final String FINDING_SITE = "363698007";
	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private CrsConceptPreparation preparation;
	private AtomicInteger uuidCounter;

	@BeforeEach
	void setUp() {
		uuidCounter = new AtomicInteger();
		preparation = new CrsConceptPreparation(MAPPER, () -> "uuid-" + uuidCounter.incrementAndGet());
	}

	@Test
	void prepareCrsConcept_returnsPlaceholderWhenRequestHasNoConcept() {
		ObjectNode result = preparation.prepareCrsConcept(null, null, MODULE_ID);

		assertEquals("Request without proposed concept", result.path("fsn").asString());
		assertFalse(result.has("conceptId"));
	}

	@Test
	void prepareCrsConcept_returnsPlaceholderWhenRequestIsEmptyObject() throws Exception {
		ObjectNode result = preparation.prepareCrsConcept(MAPPER.readTree("{}"), null, MODULE_ID);

		assertEquals("Request without proposed concept", result.path("fsn").asString());
	}

	@Test
	void prepareCrsConcept_generatesGuidAndClassAxiomWhenConceptIdMissing() throws Exception {
		JsonNode request = MAPPER.readTree("""
				{
				  "fsn": "New pneumonia (disorder)",
				  "definitionStatus": "FULLY_DEFINED",
				  "relationships": [
				    {
				      "characteristicType": "STATED_RELATIONSHIP",
				      "groupId": 0,
				      "active": true,
				      "type": { "conceptId": "%s", "fsn": "Is a (attribute)" },
				      "target": { "conceptId": "404684003" }
				    }
				  ]
				}
				""".formatted(ISA));

		ObjectNode result = preparation.prepareCrsConcept(request, null, MODULE_ID);

		assertEquals("uuid-1", result.path("conceptId").asString());
		assertFalse(result.has("relationships"));
		assertEquals(1, result.path("classAxioms").size());
		JsonNode axiom = result.path("classAxioms").get(0);
		assertEquals("uuid-2", axiom.path("axiomId").asString());
		assertEquals("FULLY_DEFINED", axiom.path("definitionStatus").asString());
		assertEquals(MODULE_ID, axiom.path("moduleId").asString());
		assertTrue(axiom.path("active").asBoolean());
		assertFalse(axiom.path("released").asBoolean());
		assertEquals(1, axiom.path("relationships").size());
		assertEquals("Is a", axiom.path("relationships").get(0).path("type").path("pt").asString());
	}

	@Test
	void prepareCrsConcept_buildsAxiomForNewConceptChangeTypeAndTrimsConceptId() throws Exception {
		JsonNode request = MAPPER.readTree("""
				{
				  "conceptId": " 12345678901 ",
				  "definitionStatus": "PRIMITIVE",
				  "definitionOfChanges": { "changeType": "NEW_CONCEPT" },
				  "relationships": [
				    {
				      "characteristicType": "STATED_RELATIONSHIP",
				      "groupId": 0,
				      "active": true,
				      "type": { "conceptId": "%s", "fsn": "Is a (attribute)" },
				      "target": { "conceptId": "404684003" }
				    }
				  ]
				}
				""".formatted(ISA));

		ObjectNode result = preparation.prepareCrsConcept(request, MAPPER.readTree("{\"conceptId\":\"should-not-be-used\"}"), MODULE_ID);

		assertEquals("12345678901", result.path("conceptId").asString());
		assertEquals("NEW_CONCEPT", result.path("definitionOfChanges").path("changeType").asString());
		assertEquals("PRIMITIVE", result.path("classAxioms").get(0).path("definitionStatus").asString());
		assertEquals("Is a", result.path("classAxioms").get(0).path("relationships").get(0).path("type").path("pt").asString());
	}

	@Test
	void prepareCrsConcept_mergesDefinitionOfChangesOntoExistingConcept() throws Exception {
		JsonNode existing = MAPPER.readTree("""
				{
				  "conceptId": "12345678901",
				  "definitionStatus": "PRIMITIVE",
				  "descriptions": [
				    { "descriptionId": "d1", "term": "Old" }
				  ],
				  "classAxioms": [
				    {
				      "axiomId": "ax-1",
				      "definitionStatus": "PRIMITIVE",
				      "relationships": [
				        {
				          "groupId": 0,
				          "type": { "conceptId": "%s" },
				          "target": { "conceptId": "404684003" }
				        }
				      ]
				    }
				  ]
				}
				""".formatted(ISA));
		JsonNode request = MAPPER.readTree("""
				{
				  "conceptId": "12345678901",
				  "definitionStatus": "FULLY_DEFINED",
				  "definitionOfChanges": { "changeType": "CHANGE", "notes": "Update" },
				  "descriptions": [
				    { "descriptionId": "d1", "term": "Old", "definitionOfChanges": { "changed": true } },
				    { "term": "New synonym", "type": "SYNONYM", "active": true }
				  ],
				  "relationships": [
				    {
				      "characteristicType": "STATED_RELATIONSHIP",
				      "groupId": 0,
				      "active": true,
				      "type": { "conceptId": "%s" },
				      "target": { "conceptId": "404684003" },
				      "definitionOfChanges": { "changed": true }
				    },
				    {
				      "characteristicType": "STATED_RELATIONSHIP",
				      "groupId": 1,
				      "active": true,
				      "type": { "conceptId": "%s", "fsn": "Finding site (attribute)" },
				      "target": { "conceptId": "39607008" }
				    }
				  ]
				}
				""".formatted(ISA, FINDING_SITE));

		ObjectNode result = preparation.prepareCrsConcept(request, existing, MODULE_ID);

		assertEquals("Update", result.path("definitionOfChanges").path("notes").asString());
		assertEquals("FULLY_DEFINED", result.path("definitionStatus").asString());
		assertEquals("FULLY_DEFINED", result.path("classAxioms").get(0).path("definitionStatus").asString());
		assertEquals(2, result.path("descriptions").size());
		assertTrue(result.path("descriptions").get(0).path("definitionOfChanges").path("changed").asBoolean());
		assertEquals("New synonym", result.path("descriptions").get(1).path("term").asString());
		assertTrue(result.path("classAxioms").get(0).path("relationships").get(0).path("definitionOfChanges").path("changed").asBoolean());
		JsonNode added = result.path("classAxioms").get(0).path("relationships").get(1);
		assertEquals(FINDING_SITE, added.path("type").path("conceptId").asString());
		assertEquals("Finding site", added.path("type").path("pt").asString());
	}

	@Test
	void prepareCrsConcept_setsAllAxiomsPrimitiveWhenCrsDefinitionStatusIsPrimitive() throws Exception {
		JsonNode existing = MAPPER.readTree("""
				{
				  "conceptId": "1",
				  "classAxioms": [
				    { "definitionStatus": "FULLY_DEFINED", "relationships": [] },
				    { "definitionStatus": "FULLY_DEFINED", "relationships": [] }
				  ]
				}
				""");
		JsonNode request = MAPPER.readTree("""
				{
				  "conceptId": "1",
				  "definitionStatus": "PRIMITIVE",
				  "relationships": []
				}
				""");

		ObjectNode result = preparation.prepareCrsConcept(request, existing, MODULE_ID);

		assertEquals("PRIMITIVE", result.path("classAxioms").get(0).path("definitionStatus").asString());
		assertEquals("PRIMITIVE", result.path("classAxioms").get(1).path("definitionStatus").asString());
	}

	@Test
	void prepareCrsConcept_matchesConcreteValueRelationships() throws Exception {
		JsonNode existing = MAPPER.readTree("""
				{
				  "conceptId": "1",
				  "classAxioms": [
				    {
				      "relationships": [
				        {
				          "groupId": 1,
				          "type": { "conceptId": "1142135004" },
				          "concreteValue": { "valueWithPrefix": "#5" }
				        }
				      ]
				    }
				  ]
				}
				""");
		JsonNode request = MAPPER.readTree("""
				{
				  "conceptId": "1",
				  "relationships": [
				    {
				      "characteristicType": "STATED_RELATIONSHIP",
				      "groupId": 1,
				      "active": true,
				      "type": { "conceptId": "1142135004" },
				      "concreteValue": { "valueWithPrefix": "#5" },
				      "definitionOfChanges": { "changed": true }
				    }
				  ]
				}
				""");

		ObjectNode result = preparation.prepareCrsConcept(request, existing, MODULE_ID);

		assertEquals(1, result.path("classAxioms").get(0).path("relationships").size());
		assertTrue(result.path("classAxioms").get(0).path("relationships").get(0)
				.path("definitionOfChanges").path("changed").asBoolean());
	}

	@Test
	void prepareCrsConcept_skipsInferredRelationshipsAndInactiveAdds() throws Exception {
		JsonNode existing = MAPPER.readTree("""
				{
				  "conceptId": "1",
				  "classAxioms": [ { "relationships": [] } ]
				}
				""");
		JsonNode request = MAPPER.readTree("""
				{
				  "conceptId": "1",
				  "relationships": [
				    {
				      "characteristicType": "INFERRED_RELATIONSHIP",
				      "groupId": 0,
				      "active": true,
				      "type": { "conceptId": "%s", "fsn": "Is a (attribute)" },
				      "target": { "conceptId": "404684003" }
				    },
				    {
				      "characteristicType": "STATED_RELATIONSHIP",
				      "groupId": 0,
				      "active": false,
				      "type": { "conceptId": "%s", "fsn": "Finding site (attribute)" },
				      "target": { "conceptId": "39607008" }
				    }
				  ]
				}
				""".formatted(ISA, FINDING_SITE));

		ObjectNode result = preparation.prepareCrsConcept(request, existing, MODULE_ID);

		assertEquals(0, result.path("classAxioms").get(0).path("relationships").size());
	}

	@Test
	void prepareCrsConcept_requiresExistingConceptWhenNotNew() throws Exception {
		JsonNode request = MAPPER.readTree("{\"conceptId\":\"123\"}");

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> preparation.prepareCrsConcept(request, null, MODULE_ID));

		assertTrue(ex.getMessage().contains("123"));
	}

	@Test
	void getConceptIdsFromPromotionSummary_parsesIdsInOrder() {
		List<String> ids = CrsConceptPreparation.getConceptIdsFromPromotionSummary(
				"Content promotion of 11 |term 1| and dependency: 22 |term 2|, 33 |term 3|");

		assertEquals(List.of("11", "22", "33"), ids);
	}

	@Test
	void getConceptIdsFromPromotionSummary_returnsEmptyForBlank() {
		assertTrue(CrsConceptPreparation.getConceptIdsFromPromotionSummary(null).isEmpty());
		assertTrue(CrsConceptPreparation.getConceptIdsFromPromotionSummary("").isEmpty());
		assertTrue(CrsConceptPreparation.getConceptIdsFromPromotionSummary("No identifiers here").isEmpty());
	}

	@Test
	void preferredTermFromFsn_trimsSemanticTag() {
		assertEquals("Is a", CrsConceptPreparation.preferredTermFromFsn("Is a (attribute)", false));
		assertEquals("Finding site", CrsConceptPreparation.preferredTermFromFsn("Finding site (attribute)", true));
		assertEquals("No tag", CrsConceptPreparation.preferredTermFromFsn("No tag", false));
	}

	@Test
	void isNewConcept_readsChangeType() throws Exception {
		assertTrue(CrsConceptPreparation.isNewConcept(MAPPER.readTree(
				"{\"definitionOfChanges\":{\"changeType\":\"NEW_CONCEPT\"}}")));
		assertFalse(CrsConceptPreparation.isNewConcept(MAPPER.readTree("{\"conceptId\":\"1\"}")));
	}
}
