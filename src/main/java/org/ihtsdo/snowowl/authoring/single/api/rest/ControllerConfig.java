package org.ihtsdo.snowowl.authoring.single.api.rest;

import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class ControllerConfig {

	private Logger logger = LoggerFactory.getLogger(getClass());

	@ExceptionHandler(Exception.class)
	void exceptionCatchAll(Exception e) {
		logger.error("{}", e);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	void catchNotFoundException(ResourceNotFoundException e) {
		logger.debug("{}", e);
	}

}
