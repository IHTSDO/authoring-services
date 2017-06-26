package org.ihtsdo.snowowl.authoring.batchimport.api.pojo.browser;

import java.util.Set;

public class SnomedBrowserConcept {

	private String id;
	private boolean active;
	private String fsn;
	private Set<SnomedBrowserDescription> descriptions;
	private Set<SnomedBrowserRelationship> relationships;
	
	public String getId() {
		return id;
	}
	public void setId(String conceptId) {
		this.id = conceptId;
	}
	public boolean isActive() {
		return active;
	}
	public void setActive(boolean active) {
		this.active = active;
	}
	public String getFsn() {
		return fsn;
	}
	public void setFsn(String fsn) {
		this.fsn = fsn;
	}
	public Set<SnomedBrowserDescription> getDescriptions() {
		return descriptions;
	}
	public void setDescriptions(Set<SnomedBrowserDescription> descriptions) {
		this.descriptions = descriptions;
	}
	public Set<SnomedBrowserRelationship> getRelationships() {
		return relationships;
	}
	public void setRelationships(Set<SnomedBrowserRelationship> relationships) {
		this.relationships = relationships;
	}
	
}
