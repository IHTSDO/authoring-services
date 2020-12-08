package org.ihtsdo.snowowl.authoring.single.api.service;

import org.json.JSONException;
import org.junit.Test;

import java.io.IOException;

public class UiStateServiceTest {

	@Test(expected = JSONException.class)
	public void testPersistTaskPanelStateThrowsExceptionWhenJsonStateIsMalformed() throws IOException {
		new UiStateService().persistTaskPanelState("", "", "", "", "test}");
	}

	@Test(expected = JSONException.class)
	public void testPersistPanelStateThrowsExceptionWhenJsonStateIsMalformed() throws IOException {
		new UiStateService().persistTaskPanelState("", "", "", "", "{test");
	}
}
