package org.ihtsdo.authoringservices.service.exceptions;

/**
 * This exception should be thrown when a specific path
 * is not provided to a resource.
 */
public class PathNotProvidedException extends RuntimeException {

	/**
	 * Builds the {@code PathNotProvidedException} which
	 * contains the {@code message} to why it was thrown.
	 *
	 * @param message The reason behind the {@code PathNotProvidedException}
	 *                being thrown.
	 */
	public PathNotProvidedException(final String message) {
		super(message);
	}
}
