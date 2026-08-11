package org.ihtsdo.authoringservices.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Request body for task-scoped concept inactivation.
 * The service fetches related concepts and applies updates from this payload.
 */
public class ConceptInactivationRequest {

	private String reasonId;
	private List<Association> associations = new ArrayList<>();
	private List<AcceptedAffectedConcept> acceptedAffectedConcepts = new ArrayList<>();
	/**
	 * Historical associations on other concepts/descriptions that currently target the
	 * concept being inactivated.
	 */
	private List<AcceptedAffectedHistoricalAssociation> acceptedAffectedHistoricalAssociations = new ArrayList<>();
	private Boolean dryRun;

	public String getReasonId() {
		return reasonId;
	}

	public void setReasonId(String reasonId) {
		this.reasonId = reasonId;
	}

	public List<Association> getAssociations() {
		return associations;
	}

	public void setAssociations(List<Association> associations) {
		this.associations = associations != null ? associations : new ArrayList<>();
	}

	public List<AcceptedAffectedConcept> getAcceptedAffectedConcepts() {
		return acceptedAffectedConcepts;
	}

	public void setAcceptedAffectedConcepts(List<AcceptedAffectedConcept> acceptedAffectedConcepts) {
		this.acceptedAffectedConcepts = acceptedAffectedConcepts != null ? acceptedAffectedConcepts : new ArrayList<>();
	}

	public List<AcceptedAffectedHistoricalAssociation> getAcceptedAffectedHistoricalAssociations() {
		return acceptedAffectedHistoricalAssociations;
	}

	public void setAcceptedAffectedHistoricalAssociations(
			List<AcceptedAffectedHistoricalAssociation> acceptedAffectedHistoricalAssociations) {
		this.acceptedAffectedHistoricalAssociations = acceptedAffectedHistoricalAssociations != null
				? acceptedAffectedHistoricalAssociations
				: new ArrayList<>();
	}

	public Boolean getDryRun() {
		return dryRun;
	}

	public void setDryRun(Boolean dryRun) {
		this.dryRun = dryRun;
	}

	public boolean isDryRun() {
		return Boolean.TRUE.equals(dryRun);
	}

	public static class Association {
		private String type;
		private String targetConceptId;

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public String getTargetConceptId() {
			return targetConceptId;
		}

		public void setTargetConceptId(String targetConceptId) {
			this.targetConceptId = targetConceptId;
		}
	}

	public static class AcceptedAffectedConcept {
		private String conceptId;
		private List<AcceptedReplacement> acceptedReplacements = new ArrayList<>();

		public String getConceptId() {
			return conceptId;
		}

		public void setConceptId(String conceptId) {
			this.conceptId = conceptId;
		}

		public List<AcceptedReplacement> getAcceptedReplacements() {
			return acceptedReplacements;
		}

		public void setAcceptedReplacements(List<AcceptedReplacement> acceptedReplacements) {
			this.acceptedReplacements = acceptedReplacements != null ? acceptedReplacements : new ArrayList<>();
		}
	}

	public static class AcceptedReplacement {
		private String targetConceptId;
		private String typeConceptId;

		public String getTargetConceptId() {
			return targetConceptId;
		}

		public void setTargetConceptId(String targetConceptId) {
			this.targetConceptId = targetConceptId;
		}

		public String getTypeConceptId() {
			return typeConceptId;
		}

		public void setTypeConceptId(String typeConceptId) {
			this.typeConceptId = typeConceptId;
		}
	}

	/**
	 * Remap a historical association on a concept or description that pointed at the
	 * inactivated concept. When {@code descriptionId} is set, the update applies to that
	 * description on {@code conceptId}; otherwise it applies to the concept itself.
	 */
	public static class AcceptedAffectedHistoricalAssociation {
		private String conceptId;
		private String descriptionId;
		/** Historical association type name or SCTID (e.g. POSSIBLY_EQUIVALENT_TO / REFERS_TO). */
		private String associationType;
		/** Replacement target; null/blank clears association targets for this row. */
		private String newTargetConceptId;
		/** New inactivation indicator for the affected concept/description. */
		private String inactivationIndicator;

		public String getConceptId() {
			return conceptId;
		}

		public void setConceptId(String conceptId) {
			this.conceptId = conceptId;
		}

		public String getDescriptionId() {
			return descriptionId;
		}

		public void setDescriptionId(String descriptionId) {
			this.descriptionId = descriptionId;
		}

		public String getAssociationType() {
			return associationType;
		}

		public void setAssociationType(String associationType) {
			this.associationType = associationType;
		}

		public String getNewTargetConceptId() {
			return newTargetConceptId;
		}

		public void setNewTargetConceptId(String newTargetConceptId) {
			this.newTargetConceptId = newTargetConceptId;
		}

		public String getInactivationIndicator() {
			return inactivationIndicator;
		}

		public void setInactivationIndicator(String inactivationIndicator) {
			this.inactivationIndicator = inactivationIndicator;
		}
	}
}
