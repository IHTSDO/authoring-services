package org.ihtsdo.authoringservices.domain;

import java.util.List;

public record ProjectFieldUpdateRequest(List<AuthoringProjectField> fields) {
}
