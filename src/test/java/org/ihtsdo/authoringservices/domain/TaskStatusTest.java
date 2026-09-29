package org.ihtsdo.authoringservices.domain;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskStatusTest {

	@Test
	void testValueOfAndEquals() throws BusinessServiceException {
        assertSame(TaskStatus.IN_PROGRESS, TaskStatus.fromLabelOrThrow("In Progress"));
        assertSame(TaskStatus.READY_FOR_REVIEW, TaskStatus.fromLabelOrThrow("Ready For Review"));
	}

	@Test
	void testThrow() {
		assertThrows(BusinessServiceException.class, () -> TaskStatus.fromLabelOrThrow("Something"));
	}

	@Test
	void testInReviewWithoutReviewersMovesToReadyForReview() {
		assertSame(TaskStatus.READY_FOR_REVIEW, TaskStatus.resolveReviewStatus(TaskStatus.IN_REVIEW, false));
		assertSame(TaskStatus.IN_REVIEW, TaskStatus.resolveReviewStatus(TaskStatus.IN_REVIEW, true));
	}

	@Test
	void testReadyForReviewWithReviewersMovesToInReview() {
		assertSame(TaskStatus.IN_REVIEW, TaskStatus.resolveReviewStatus(TaskStatus.READY_FOR_REVIEW, true));
		assertSame(TaskStatus.READY_FOR_REVIEW, TaskStatus.resolveReviewStatus(TaskStatus.READY_FOR_REVIEW, false));
	}

	@Test
	void testOtherStatusesAreUnaffectedByReviewers() {
		assertSame(TaskStatus.IN_PROGRESS, TaskStatus.resolveReviewStatus(TaskStatus.IN_PROGRESS, false));
		assertSame(TaskStatus.REVIEW_COMPLETED, TaskStatus.resolveReviewStatus(TaskStatus.REVIEW_COMPLETED, false));
		assertSame(TaskStatus.PROMOTED, TaskStatus.resolveReviewStatus(TaskStatus.PROMOTED, true));
		assertNull(TaskStatus.resolveReviewStatus(null, false));
	}

}
