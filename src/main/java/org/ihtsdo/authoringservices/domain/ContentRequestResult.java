package org.ihtsdo.authoringservices.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContentRequestResult(JsonNode concepts, String status, String message) {

	public static ContentRequestResult of(JsonNode concepts) {
		return new ContentRequestResult(concepts, null, null);
	}

	public static ContentRequestResult error(JsonNode concepts, String message) {
		return new ContentRequestResult(concepts, "ERROR", message);
	}

	public static ContentRequestResult warning(JsonNode concepts, String message) {
		return new ContentRequestResult(concepts, "WARNING", message);
	}
}
