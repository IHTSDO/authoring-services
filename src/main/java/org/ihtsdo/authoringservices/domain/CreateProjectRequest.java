package org.ihtsdo.authoringservices.domain;

public record CreateProjectRequest(String codeSystemShortName, String key, String name, String lead, String description) {
}
