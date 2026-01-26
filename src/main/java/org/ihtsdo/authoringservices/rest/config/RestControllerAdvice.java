package org.ihtsdo.authoringservices.rest.config;

import jakarta.servlet.ServletException;
import org.ihtsdo.authoringservices.service.exceptions.PathNotProvidedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class RestControllerAdvice {

	private static final Logger logger = LoggerFactory.getLogger(RestControllerAdvice.class);

	@ExceptionHandler({
			PathNotProvidedException.class
	})
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ResponseBody
	public Map<String,Object> handleIllegalArgumentException(Exception exception) {
		HashMap<String, Object> result = new HashMap<>();
		result.put("error", HttpStatus.BAD_REQUEST);
		result.put("message", exception.getMessage());
		logger.info("bad request {}", exception.getMessage());
		logger.debug("bad request {}", exception.getMessage(), exception);
		return result;
	}

	@ExceptionHandler({ServletException.class})
	public ResponseEntity<String> handleServletException(ServletException ex) {
		Throwable rootCause = ex.getRootCause();
		if (rootCause != null && rootCause.getMessage().contains("The encoded slash character is not allowed")) {
			return ResponseEntity
					.status(HttpStatus.BAD_REQUEST)
					.body("Invalid branch path: encoded slashes are not allowed");
		}
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected server error");
	}
}
