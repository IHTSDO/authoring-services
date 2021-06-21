package org.ihtsdo.authoringservices.service;

import org.ihtsdo.authoringservices.entity.ReviewMessage;

/**
 * Receive event notifications by declaring a bean which implements this method.
 */
public interface ReviewMessageSentListener  {
	void messageSent(ReviewMessage message, String event);
}
