package org.ihtsdo.authoringservices.review.service;

import org.ihtsdo.authoringservices.review.domain.ReviewMessage;

/**
 * Receive event notifications by declaring a bean which implements this method.
 */
public interface ReviewMessageSentListener  {
	void messageSent(ReviewMessage message, String event);
}
