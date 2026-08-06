package org.ihtsdo.authoringservices.domain;

import java.util.ArrayList;
import java.util.List;

public class CrsBlockingState {

	public record BlockingConcept(String conceptId, String crsRequestId, String status, String requestSummary) {
	}

	private List<BlockingConcept> blockingConcepts = new ArrayList<>();
	private boolean blocked;

	public List<BlockingConcept> getBlockingConcepts() {
		return blockingConcepts;
	}

	public void setBlockingConcepts(List<BlockingConcept> blockingConcepts) {
		this.blockingConcepts = blockingConcepts != null ? blockingConcepts : new ArrayList<>();
		this.blocked = !this.blockingConcepts.isEmpty();
	}

	public boolean isBlocked() {
		return blocked;
	}

	public void setBlocked(boolean blocked) {
		this.blocked = blocked;
	}
}
