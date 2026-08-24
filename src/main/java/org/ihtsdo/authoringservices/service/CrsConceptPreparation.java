package org.ihtsdo.authoringservices.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prepares a Snowstorm concept from a CRS request payload.
 * Mirrors authoring-ui crsService: prepareCrsConcept, updateConceptFromCrsRequest,
 * and getConceptIdsFromPromotionSummary.
 */
@Component
public class CrsConceptPreparation {

	static final String EMPTY_REQUEST_FSN = "Request without proposed concept";
	static final String CHANGE_TYPE_NEW_CONCEPT = "NEW_CONCEPT";
	static final String STATED_RELATIONSHIP = "STATED_RELATIONSHIP";
	static final String PRIMITIVE = "PRIMITIVE";
	static final String FULLY_DEFINED = "FULLY_DEFINED";

	private static final Pattern PROMOTION_CONCEPT_ID_PATTERN = Pattern.compile("(\\d+)\\s+\\|([^|]*)\\|");
	public static final String DEFINITION_STATUS = "definitionStatus";
	public static final String CONCRETE_VALUE = "concreteValue";
	public static final String TARGET = "target";
	public static final String CONCEPT_ID = "conceptId";
	public static final String RELATIONSHIPS = "relationships";
	public static final String DEFINITION_OF_CHANGES = "definitionOfChanges";

	private final ObjectMapper objectMapper;
	private final Supplier<String> uuidSupplier;

	@Autowired
	public CrsConceptPreparation(ObjectMapper objectMapper) {
		this(objectMapper, () -> UUID.randomUUID().toString());
	}

	CrsConceptPreparation(ObjectMapper objectMapper, Supplier<String> uuidSupplier) {
		this.objectMapper = objectMapper;
		this.uuidSupplier = uuidSupplier;
	}

	/**
	 * Builds the concept the editor should open for a CRS request.
	 *
	 * @param crsRequest CRS concept JSON, or {@code null} when the request has no proposed concept
	 * @param existingConcept concept currently on the task branch, or {@code null} when this is a new concept
	 * @param defaultModuleId branch default module, used when constructing a new axiom
	 */
	public ObjectNode prepareCrsConcept(JsonNode crsRequest, JsonNode existingConcept, String defaultModuleId) {
		if (!(crsRequest instanceof ObjectNode requestNode) || requestNode.isEmpty()) {
			ObjectNode empty = objectMapper.createObjectNode();
			empty.put("fsn", EMPTY_REQUEST_FSN);
			return empty;
		}

		ObjectNode request = requestNode.deepCopy();
		String conceptId = textOrNull(request, CONCEPT_ID);
		if (conceptId != null) {
			conceptId = conceptId.trim();
			if (conceptId.isEmpty()) {
				conceptId = null;
			} else {
				request.put(CONCEPT_ID, conceptId);
			}
		}

		if (conceptId == null) {
			request.put(CONCEPT_ID, uuidSupplier.get());
			return toNewConcept(request, defaultModuleId);
		}
		if (isNewConcept(request)) {
			return toNewConcept(request, defaultModuleId);
		}
		if (!(existingConcept instanceof ObjectNode existingNode)) {
			throw new IllegalArgumentException("Existing concept is required to apply CRS request for concept " + conceptId);
		}
		ObjectNode concept = existingNode.deepCopy();
		updateConceptFromCrsRequest(concept, request);
		return concept;
	}

	/**
	 * Parses a content promotion summary and returns SCTIDs in order of appearance.
	 * Example: {@code "Content promotion of 11 |term 1| and dependency: 22 |term 2|, 33 |term 3|"}
	 */
	public static List<String> getConceptIdsFromPromotionSummary(String summary) {
		List<String> result = new ArrayList<>();
		if (!StringUtils.hasLength(summary)) {
			return result;
		}
		Matcher matcher = PROMOTION_CONCEPT_ID_PATTERN.matcher(summary);
		while (matcher.find()) {
			result.add(matcher.group(1));
		}
		return result;
	}

	static boolean isNewConcept(JsonNode crsRequest) {
		return CHANGE_TYPE_NEW_CONCEPT.equals(crsRequest.path(DEFINITION_OF_CHANGES).path("changeType").asText(null));
	}

	private ObjectNode toNewConcept(ObjectNode request, String defaultModuleId) {
		ArrayNode relationships = copyArray(request.get(RELATIONSHIPS));
		ObjectNode axiom = newAxiom(defaultModuleId);
		axiom.set(RELATIONSHIPS, relationships);
		if (FULLY_DEFINED.equals(request.path(DEFINITION_STATUS).asText(null))) {
			axiom.put(DEFINITION_STATUS, FULLY_DEFINED);
		}
		for (JsonNode relationship : relationships) {
			if (relationship instanceof ObjectNode relationshipNode) {
				setPreferredTermFromFsn(relationshipNode, false);
			}
		}
		ArrayNode classAxioms = objectMapper.createArrayNode();
		classAxioms.add(axiom);
		request.set("classAxioms", classAxioms);
		request.remove(RELATIONSHIPS);
		return request;
	}

	void updateConceptFromCrsRequest(ObjectNode concept, ObjectNode crsConcept) {
		copyDefinitionOfChanges(concept, crsConcept);

		ArrayNode descriptions = ensureArray(concept, "descriptions");
		for (JsonNode crsDescription : iterable(crsConcept.get("descriptions"))) {
			if (!(crsDescription instanceof ObjectNode crsDescriptionNode)) {
				continue;
			}
			String descriptionId = textOrNull(crsDescriptionNode, "descriptionId");
			ObjectNode existing = findDescriptionById(descriptions, descriptionId);
			if (existing == null) {
				descriptions.add(crsDescriptionNode.deepCopy());
			} else {
				copyDefinitionOfChanges(existing, crsDescriptionNode);
			}
		}

		ArrayNode classAxioms = ensureArray(concept, "classAxioms");
		for (JsonNode crsRelationship : iterable(crsConcept.get(RELATIONSHIPS))) {
			if (!(crsRelationship instanceof ObjectNode crsRelationshipNode)
					|| !STATED_RELATIONSHIP.equals(crsRelationshipNode.path("characteristicType").asText(null))) {
				continue;
			}
			if (!applyDefinitionOfChangesToMatchingRelationship(classAxioms, crsRelationshipNode)
					&& crsRelationshipNode.path("active").asBoolean(false)) {
				ObjectNode copied = crsRelationshipNode.deepCopy();
				setPreferredTermFromFsn(copied, true);
				firstAxiomRelationships(classAxioms).add(copied);
			}
		}

		String definitionStatus = textOrNull(crsConcept, DEFINITION_STATUS);
		if (definitionStatus != null) {
			concept.put(DEFINITION_STATUS, definitionStatus);
		}
		applyDefinitionStatusToAxioms(classAxioms, definitionStatus);
	}

	private boolean applyDefinitionOfChangesToMatchingRelationship(ArrayNode classAxioms, ObjectNode crsRelationship) {
		for (JsonNode axiom : classAxioms) {
			if (!(axiom instanceof ObjectNode axiomNode)) {
				continue;
			}
			for (JsonNode relationship : iterable(axiomNode.get(RELATIONSHIPS))) {
				if (relationship instanceof ObjectNode relationshipNode && relationshipsMatch(relationshipNode, crsRelationship)) {
					copyDefinitionOfChanges(relationshipNode, crsRelationship);
					return true;
				}
			}
		}
		return false;
	}

	private ArrayNode firstAxiomRelationships(ArrayNode classAxioms) {
		ObjectNode axiom;
		if (classAxioms.isEmpty()) {
			axiom = newAxiom(null);
			classAxioms.add(axiom);
		} else if (classAxioms.get(0) instanceof ObjectNode first) {
			axiom = first;
		} else {
			axiom = newAxiom(null);
			classAxioms.set(0, axiom);
		}
		return ensureArray(axiom, RELATIONSHIPS);
	}

	private void applyDefinitionStatusToAxioms(ArrayNode classAxioms, String definitionStatus) {
		if (!StringUtils.hasLength(definitionStatus) || classAxioms.isEmpty()) {
			return;
		}
		if (PRIMITIVE.equals(definitionStatus)) {
			for (JsonNode axiom : classAxioms) {
				if (axiom instanceof ObjectNode axiomNode) {
					axiomNode.put(DEFINITION_STATUS, PRIMITIVE);
				}
			}
			return;
		}
		if (!FULLY_DEFINED.equals(definitionStatus)) {
			return;
		}
		for (JsonNode axiom : classAxioms) {
			if (FULLY_DEFINED.equals(axiom.path(DEFINITION_STATUS).asText(null))) {
				return;
			}
		}
		if (classAxioms.get(0) instanceof ObjectNode first) {
			first.put(DEFINITION_STATUS, FULLY_DEFINED);
		}
	}

	private ObjectNode newAxiom(String defaultModuleId) {
		ObjectNode axiom = objectMapper.createObjectNode();
		axiom.put("axiomId", uuidSupplier.get());
		axiom.put(DEFINITION_STATUS, PRIMITIVE);
		axiom.putNull("effectiveTime");
		axiom.put("active", true);
		axiom.put("released", false);
		if (StringUtils.hasLength(defaultModuleId)) {
			axiom.put("moduleId", defaultModuleId);
		}
		axiom.set(RELATIONSHIPS, objectMapper.createArrayNode());
		return axiom;
	}

	private void setPreferredTermFromFsn(ObjectNode relationship, boolean subtractOneBeforeParen) {
		JsonNode type = relationship.get("type");
		if (!(type instanceof ObjectNode typeNode)) {
			return;
		}
		String fsn = fsnTerm(typeNode.get("fsn"));
		if (!StringUtils.hasLength(fsn)) {
			return;
		}
		typeNode.put("pt", preferredTermFromFsn(fsn, subtractOneBeforeParen));
	}

	static String preferredTermFromFsn(String fsn, boolean subtractOneBeforeParen) {
		int paren = fsn.lastIndexOf('(');
		if (paren <= 0) {
			return fsn.trim();
		}
		int end = subtractOneBeforeParen ? paren - 1 : paren;
		if (end <= 0) {
			return fsn.trim();
		}
		return fsn.substring(0, end).trim();
	}

	private static String fsnTerm(JsonNode fsn) {
		if (fsn == null || fsn.isNull() || fsn.isMissingNode()) {
			return null;
		}
		if (fsn.isTextual()) {
			return fsn.asText();
		}
		return textOrNull(fsn, "term");
	}

	private static boolean relationshipsMatch(ObjectNode axiomRelationship, ObjectNode crsRelationship) {
		if (!conceptIdsEqual(axiomRelationship.path("type"), crsRelationship.path("type"))) {
			return false;
		}
		if (axiomRelationship.path("groupId").asInt(Integer.MIN_VALUE)
				!= crsRelationship.path("groupId").asInt(Integer.MIN_VALUE)) {
			return false;
		}
		boolean targetsMatch = axiomRelationship.hasNonNull(TARGET) && crsRelationship.hasNonNull(TARGET)
				&& conceptIdsEqual(axiomRelationship.path(TARGET), crsRelationship.path(TARGET));
		boolean concreteMatch = axiomRelationship.hasNonNull(CONCRETE_VALUE) && crsRelationship.hasNonNull(CONCRETE_VALUE)
				&& textsEqual(axiomRelationship.path(CONCRETE_VALUE).path("valueWithPrefix"),
				crsRelationship.path(CONCRETE_VALUE).path("valueWithPrefix"));
		return targetsMatch || concreteMatch;
	}

	private static boolean conceptIdsEqual(JsonNode left, JsonNode right) {
		return textsEqual(left.path(CONCEPT_ID), right.path(CONCEPT_ID));
	}

	private static boolean textsEqual(JsonNode left, JsonNode right) {
		String leftText = left.isMissingNode() || left.isNull() ? null : left.asText();
		String rightText = right.isMissingNode() || right.isNull() ? null : right.asText();
		return leftText != null && leftText.equals(rightText);
	}

	private static ObjectNode findDescriptionById(ArrayNode descriptions, String descriptionId) {
		if (!StringUtils.hasLength(descriptionId)) {
			return null;
		}
		for (JsonNode description : descriptions) {
			if (description instanceof ObjectNode descriptionNode
					&& descriptionId.equals(textOrNull(descriptionNode, "descriptionId"))) {
				return descriptionNode;
			}
		}
		return null;
	}

	private static void copyDefinitionOfChanges(ObjectNode target, JsonNode source) {
		JsonNode definitionOfChanges = source.get(DEFINITION_OF_CHANGES);
		if (definitionOfChanges != null && !definitionOfChanges.isNull()) {
			target.set(DEFINITION_OF_CHANGES, definitionOfChanges.deepCopy());
		}
	}

	private ArrayNode copyArray(JsonNode node) {
		ArrayNode copy = objectMapper.createArrayNode();
		for (JsonNode item : iterable(node)) {
			copy.add(item.deepCopy());
		}
		return copy;
	}

	private ArrayNode ensureArray(ObjectNode node, String field) {
		JsonNode existing = node.get(field);
		if (existing instanceof ArrayNode arrayNode) {
			return arrayNode;
		}
		ArrayNode array = objectMapper.createArrayNode();
		node.set(field, array);
		return array;
	}

	private static Iterable<JsonNode> iterable(JsonNode node) {
		if (node instanceof ArrayNode arrayNode) {
			return arrayNode;
		}
		return List.of();
	}

	private static String textOrNull(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull() || value.isMissingNode()) {
			return null;
		}
		if (!value.isTextual() && !value.isNumber()) {
			return null;
		}
		String text = value.asText();
		return StringUtils.hasLength(text) ? text : null;
	}
}
