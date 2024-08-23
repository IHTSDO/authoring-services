package org.ihtsdo.authoringservices.service.monitor;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BranchMonitorTest {

	@Test
	public void testEquals() {
        assertEquals(newMon(), newMon());
	}

	@Test
	public void testMapContains() {
		Map<Class<?>, Monitor> map = new HashMap<>();
		final BranchMonitor monitor = newMon();
		map.put(monitor.getClass(), monitor);
        assertTrue(map.containsValue(newMon()));
	}

	private BranchMonitor newMon() {
		return new BranchMonitor("A", "B", "MAIN/A/B", null,  null);
	}
}
