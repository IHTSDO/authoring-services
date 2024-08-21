package org.ihtsdo.authoringservices.rest;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.catalina.connector.ClientAbortException;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.ihtsdo.authoringservices.service.exceptions.UnauthorizedServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class ControllerConfig {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@ExceptionHandler(ClientAbortException.class)
	void clientAbortException(ClientAbortException e) {
		logger.info("Client disconnected.");
	}

	@ExceptionHandler(UnauthorizedServiceException.class)
	ResponseEntity<Error> unauthorizedServiceException(UnauthorizedServiceException e) {
		logger.info("Returning FORBIDDEN status code.", e);
		return response(e.getMessage(), HttpStatus.FORBIDDEN);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<Error> accessDeniedException(AccessDeniedException e) {
		logger.info("Returning FORBIDDEN status code.", e);
		return response(e.getMessage(), HttpStatus.FORBIDDEN);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Error> exceptionCatchAll(Exception e) {
		logger.error(e.getMessage(), e);
		return response(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	ResponseEntity<Error> catchNotFoundException(ResourceNotFoundException e) {
		logger.debug("{}", e.getMessage());
		return response(e.getMessage(), HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<Error> catchIllegalArgumentException(IllegalArgumentException e) {
		logger.debug("{}", e, e);
		return response(e.getMessage(), HttpStatus.BAD_REQUEST);
	}

	private ResponseEntity<Error> response(String message, HttpStatus status) {
		return new ResponseEntity<>(new Error(status, message), status);
	}

	@JsonPropertyOrder({"status", "statusMessage", "message"})
	private static final class Error {

		private HttpStatus status;
		private String message;

		Error(HttpStatus status, String message) {
			this.status = status;
			this.message = message;
		}

		public int getStatus() {
			return status.value();
		}

		public String getStatusMessage() {
			return status.getReasonPhrase();
		}

		public String getMessage() {
			return message;
		}
	}

}
