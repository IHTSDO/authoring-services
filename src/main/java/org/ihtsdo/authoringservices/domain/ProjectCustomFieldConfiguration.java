package org.ihtsdo.authoringservices.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Configuration settings for the Authoring Project custom fields.
 */
@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "authoring.project")
public class ProjectCustomFieldConfiguration {

    private Map<String, String> customFields;

    private Set<String> customFieldsDisabledByDefault;

    public Map<String, String> getCustomFields() {
        return customFields;
    }

    public void setCustomFields(Map<String, String> customFields) {
        this.customFields = customFields;
    }

    public Set<String> getCustomFieldsDisabledByDefault() {
        return customFieldsDisabledByDefault;
    }

    public void setCustomFieldsDisabledByDefault(Set<String> customFieldsDisabledByDefault) {
        this.customFieldsDisabledByDefault = customFieldsDisabledByDefault;
    }
}
