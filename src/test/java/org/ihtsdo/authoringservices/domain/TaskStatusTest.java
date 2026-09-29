package org.ihtsdo.authoringservices.domain;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TaskStatusTest {

	@Test
	public void testValueOfAndEquals() throws BusinessServiceException {
        assertSame(TaskStatus.fromLabelOrThrow("In Progress"), TaskStatus.IN_PROGRESS);
        assertSame(TaskStatus.fromLabelOrThrow("Ready For Review"), TaskStatus.READY_FOR_REVIEW);
	}

	@Test
	public void testThrow() {
		assertThrows(BusinessServiceException.class, () -> TaskStatus.fromLabelOrThrow("Something"));
	}

	@Test
	public void testInReviewWithoutReviewersMovesToReadyForReview() {
		assertSame(TaskStatus.READY_FOR_REVIEW, TaskStatus.resolveReviewStatus(TaskStatus.IN_REVIEW, false));
		assertSame(TaskStatus.IN_REVIEW, TaskStatus.resolveReviewStatus(TaskStatus.IN_REVIEW, true));
	}

	@Test
	public void testReadyForReviewWithReviewersMovesToInReview() {
		assertSame(TaskStatus.IN_REVIEW, TaskStatus.resolveReviewStatus(TaskStatus.READY_FOR_REVIEW, true));
		assertSame(TaskStatus.READY_FOR_REVIEW, TaskStatus.resolveReviewStatus(TaskStatus.READY_FOR_REVIEW, false));
	}

	@Test
	public void testOtherStatusesAreUnaffectedByReviewers() {
		assertSame(TaskStatus.IN_PROGRESS, TaskStatus.resolveReviewStatus(TaskStatus.IN_PROGRESS, false));
		assertSame(TaskStatus.REVIEW_COMPLETED, TaskStatus.resolveReviewStatus(TaskStatus.REVIEW_COMPLETED, false));
		assertSame(TaskStatus.PROMOTED, TaskStatus.resolveReviewStatus(TaskStatus.PROMOTED, true));
		assertNull(TaskStatus.resolveReviewStatus(null, false));
	}

}
