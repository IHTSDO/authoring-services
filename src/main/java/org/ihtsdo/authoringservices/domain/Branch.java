package org.ihtsdo.authoringservices.domain;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Branch {

	private String name;
	private String path;
	private String state;
	private boolean deleted;
	private long baseTimestamp;
	private long headTimestamp;
	private Map<String, Object> metadata;
	private Set<String> userRoles;
	private Set<String> globalUserRoles;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	public long getBaseTimestamp() {
		return baseTimestamp;
	}

	public void setBaseTimestamp(long baseTimestamp) {
		this.baseTimestamp = baseTimestamp;
	}

	public long getHeadTimestamp() {
		return headTimestamp;
	}

	public void setHeadTimestamp(long headTimestamp) {
		this.headTimestamp = headTimestamp;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}

	public Set <String> getUserRoles() {
		return userRoles;
	}

	public void setUserRoles(Set <String> userRoles) {
		this.userRoles = userRoles;
	}

	public Set <String> getGlobalUserRoles() {
		return globalUserRoles;
	}

	public void setGlobalUserRoles(Set <String> globalUserRoles) {
		this.globalUserRoles = globalUserRoles;
	}

	@Override
	public boolean equals(Object o) {
		// Reflexivity check
		if (this == o) return true;
		
		// Null check and type check
		if (o == null || getClass() != o.getClass()) return false;
		
		// Cast to the correct type
		Branch branch = (Branch) o;
		
		// Compare all fields
		return isDeleted() == branch.isDeleted() &&
			   getBaseTimestamp() == branch.getBaseTimestamp() &&
			   getHeadTimestamp() == branch.getHeadTimestamp() &&
			   Objects.equals(getName(), branch.getName()) &&
			   Objects.equals(getPath(), branch.getPath()) &&
			   Objects.equals(getState(), branch.getState()) &&
			   Objects.equals(getMetadata(), branch.getMetadata()) &&
			   Objects.equals(getUserRoles(), branch.getUserRoles()) &&
			   Objects.equals(getGlobalUserRoles(), branch.getGlobalUserRoles());
	}

	@Override
	public int hashCode() {
		// Use Objects.hash() for consistent hash code generation
		// Include all fields that are used in equals() method
		// Order should match the order in equals() for consistency
		return Objects.hash(
			getName(),
			getPath(), 
			getState(),
			isDeleted(),
			getBaseTimestamp(),
			getHeadTimestamp(),
			getMetadata(),
			getUserRoles(),
			getGlobalUserRoles()
		);
	}
}
