package org.ihtsdo.snowowl.authoring.single.api.pojo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "uiConfiguration")
public class UiConfiguration {

	private Map<String, String> endpoints;
	private Map<String, String> features;

	public Map<String, String> getEndpoints() {
		return endpoints;
	}

	public void setEndpoints(Map<String, String> endpoints) {
		this.endpoints = endpoints;
	}

	public Map<String, String> getFeatures() {
		return features;
	}

	public void setFeatures(Map<String, String> features) {
		this.features = features;
	}
}
