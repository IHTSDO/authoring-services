package org.ihtsdo.authoringservices.service.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.Date;

public class TimerUtil {

	private final String timerName;
	private final long start;
	private long lastCheck;
	private final Logger logger = LoggerFactory.getLogger(getClass());
	private final Level loggingLevel;

	public TimerUtil(String timerName) {
		this(timerName, Level.INFO);
	}

	public TimerUtil(String timerName, Level loggingLevel) {
		this.loggingLevel = loggingLevel;
		this.timerName = timerName;
		this.start = new Date().getTime();
		lastCheck = start;
		log("Timer {}: started", timerName);
	}

	public void checkpoint(String name) {
		final long now = new Date().getTime();
		float millisTaken = now - lastCheck;
		lastCheck = now;
		log("Timer {}: {} took {} seconds", timerName, name, millisTaken / 1000f);
	}

	public void finish() {
		final long now = new Date().getTime();
		float millisTaken = now - start;
		log("Timer {}: total took {} seconds", timerName, millisTaken / 1000f);
	}

	private void log(String s, Object... o) {
        switch (loggingLevel.toString()) {
            case "TRACE" -> logger.trace(s, o);
            case "DEBUG" -> logger.debug(s, o);
            case "WARN" -> logger.warn(s, o);
            case "ERROR" -> logger.error(s, o);
            case "OFF" -> {
            }
            default -> logger.info(s, o);
        }
	}

	public static void main(String[] args) throws InterruptedException {
		final TimerUtil timer = new TimerUtil("A");
		Thread.sleep(100);
		timer.checkpoint("thing");
		Thread.sleep(1000);
		timer.checkpoint("other thing");
		timer.finish();
	}

}
