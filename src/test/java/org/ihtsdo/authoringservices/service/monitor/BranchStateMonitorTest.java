package org.ihtsdo.authoringservices.service.monitor;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BranchStateMonitorTest {

	@Test
	public void testEquals() {
        assertEquals(newMon(), newMon());
	}

	@Test
	public void testMapContains() {
		Map<Class<?>, Monitor> map = new HashMap<>();
		final BranchStateMonitor monitor = newMon();
		map.put(monitor.getClass(), monitor);
        assertTrue(map.containsValue(newMon()));
	}

	private BranchStateMonitor newMon() {
		return new BranchStateMonitor("A", "B", "MAIN/A/B", null);
	}
}
