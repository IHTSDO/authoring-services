package org.ihtsdo.snowowl.authoring.single.api.configuration;

import org.ihtsdo.otf.resourcemanager.ResourceConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration settings for the UI state changes.
 */
@Configuration
@ConfigurationProperties("ui-state.storage")
public class UiStateStorageConfiguration extends ResourceConfiguration {
}
