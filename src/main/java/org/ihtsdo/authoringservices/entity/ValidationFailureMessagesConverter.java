package org.ihtsdo.authoringservices.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Converts between List&lt;String&gt; and a single string for DB storage.
 * Uses a delimiter (UNIT SEPARATOR) so messages that contain newlines remain single strings.
 */
@Converter
public class ValidationFailureMessagesConverter implements AttributeConverter<List<String>, String> {

    /** Delimiter between messages; chosen so messages can contain newlines without being split. */
    public static final String DELIMITER = "\u0001";

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        return String.join(DELIMITER, attribute);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(Arrays.asList(dbData.split(DELIMITER, -1)));
    }
}

