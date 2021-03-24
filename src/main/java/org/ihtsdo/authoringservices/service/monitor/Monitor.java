package org.ihtsdo.authoringservices.service.monitor;

import org.ihtsdo.authoringservices.domain.Notification;

public abstract class Monitor { // Using abstract class to enforce overriding java.lang.Object methods
	public abstract Notification runOnce() throws MonitorException;

	public abstract boolean equals(Object other);

	public abstract int hashCode();

	public abstract String toString();
}
