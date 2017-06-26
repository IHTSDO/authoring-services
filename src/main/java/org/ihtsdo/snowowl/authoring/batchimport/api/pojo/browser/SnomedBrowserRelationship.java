package org.ihtsdo.snowowl.authoring.batchimport.api.pojo.browser;

public class SnomedBrowserRelationship implements SnomedBrowserConstants {
	
	private int groupId;
	private CharacteristicType characteristicType;
	private Long sourceId;
	private Long typeId;
	private Long targetId;
	private boolean active;
	private RelationshipModifier relationshipModifier;
	
	public int getGroupId() {
		return groupId;
	}
	public void setGroupId(int groupId) {
		this.groupId = groupId;
	}
	public CharacteristicType getCharacteristicType() {
		return characteristicType;
	}
	public void setCharacteristicType(CharacteristicType characteristicType) {
		this.characteristicType = characteristicType;
	}
	public Long getSourceId() {
		return sourceId;
	}
	public void setSourceId(Long sourceId) {
		this.sourceId = sourceId;
	}
	public Long getTypeId() {
		return typeId;
	}
	public void setTypeId(Long typeId) {
		this.typeId = typeId;
	}
	public Long getTargetId() {
		return targetId;
	}
	public void setTargetId(Long targetId) {
		this.targetId = targetId;
	}
	public boolean isActive() {
		return active;
	}
	public void setActive(boolean active) {
		this.active = active;
	}
	public RelationshipModifier getRelationshipModifier() {
		return relationshipModifier;
	}
	public void setRelationshipModifier(RelationshipModifier relationshipModifier) {
		this.relationshipModifier = relationshipModifier;
	}

}
