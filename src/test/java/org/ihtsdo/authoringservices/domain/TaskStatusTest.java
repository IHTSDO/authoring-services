package org.ihtsdo.authoringservices.domain;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TaskStatusTest {

	@Test
	public void testValueOfAndEquals() throws BusinessServiceException {
        assertSame(TaskStatus.fromLabelOrThrow("In Progress"), TaskStatus.IN_PROGRESS);
	}

	@Test
	public void testThrow() {
		assertThrows(BusinessServiceException.class, () -> TaskStatus.fromLabelOrThrow("Something"));
	}

}
