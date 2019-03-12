package org.ihtsdo.snowowl.authoring.single.api.service;

public class UnauthorizedServiceException extends RuntimeException {
	public UnauthorizedServiceException(String message) {
		super(message);
	}
}
