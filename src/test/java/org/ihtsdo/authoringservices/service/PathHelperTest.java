package org.ihtsdo.authoringservices.service;

import org.ihtsdo.otf.rest.client.terminologyserver.PathHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PathHelperTest {

	@Test
	public void testGetMainPath() {
		assertEquals("MAIN", PathHelper.getMainPath());
	}

	@Test
	public void testGetProjectPath() {
		assertEquals("MAIN/PROJECTA", PathHelper.getProjectPath(null, "PROJECTA"));
		assertEquals("MAIN/2016-07-31/PROJECTA", PathHelper.getProjectPath("MAIN/2016-07-31", "PROJECTA"));
	}

	@Test
	public void testGetTaskPath() {
		assertEquals("MAIN/PROJECTA/PROJECTA-5", PathHelper.getTaskPath(null, "PROJECTA", "PROJECTA-5"));
		assertEquals("MAIN/2016-07-31/PROJECTA/PROJECTA-5", PathHelper.getTaskPath("MAIN/2016-07-31", "PROJECTA", "PROJECTA-5"));

	}

	@Test
	public void testGetParent() {
		assertEquals("MAIN/2016-07-31/PROJECTA", PathHelper.getParentPath("MAIN/2016-07-31/PROJECTA/PROJECTA-5"));
	}

	@Test
	public void testGetName() {
		assertEquals("PROJECTA-5", PathHelper.getName("MAIN/2016-07-31/PROJECTA/PROJECTA-5"));
	}

	@Test
	public void testGetParentName() {
		assertEquals("PROJECTA", PathHelper.getParentName("MAIN/2016-07-31/PROJECTA/PROJECTA-5"));
		assertEquals("2016-07-31", PathHelper.getParentName("MAIN/2016-07-31/PROJECTA"));
		assertEquals("MAIN", PathHelper.getParentName("MAIN/PROJECTA"));
	}

}