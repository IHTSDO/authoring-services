package org.ihtsdo.authoringservices.service.exceptions;

public class UnauthorizedServiceException extends RuntimeException {

	public UnauthorizedServiceException(String message) {
		super(message);
	}
}
