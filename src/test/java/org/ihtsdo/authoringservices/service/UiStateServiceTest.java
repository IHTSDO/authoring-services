package org.ihtsdo.authoringservices.service;

import org.junit.jupiter.api.Test;
import us.monoid.json.JSONException;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class UiStateServiceTest {

	@Test
	public void testPersistTaskPanelStateThrowsExceptionWhenJsonStateIsMalformed() {
		assertThrows(JSONException.class, () -> new UiStateService().persistTaskPanelState("", "", "", "", "test}"));
	}

    @Test
	public void testPersistPanelStateThrowsExceptionWhenJsonStateIsMalformed() {
        assertThrows(JSONException.class, () -> new UiStateService().persistTaskPanelState("", "", "", "", "{test"));
	}
}
