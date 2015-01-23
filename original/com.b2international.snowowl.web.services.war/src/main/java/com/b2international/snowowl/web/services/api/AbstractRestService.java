/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.b2international.snowowl.rest.exception.BadRequestException;
import com.b2international.snowowl.rest.exception.ConflictException;
import com.b2international.snowowl.rest.exception.IllegalQueryParameterException;
import com.b2international.snowowl.rest.exception.NotFoundException;
import com.fasterxml.jackson.databind.JsonMappingException;

/**
 * @author apeteri
 * @author mczotter
 * @since 1.0
 */
public abstract class AbstractRestService {

	private static final Logger LOG = LoggerFactory.getLogger(AbstractRestService.class);
	private static final String GENERIC_USER_MESSAGE = "Something went wrong during the processing of your request.";

	/**
	 * The currently supported versioned media type of the snowowl RESTful API.
	 */
	public static final String V1_MEDIA_TYPE = "application/vnd.com.b2international.snowowl-v1+json";

	/**
	 * Generic <b>Internal Server Error</b> exception handler, serving as a fallback for RESTful client calls.
	 * 
	 * @param ex
	 * @return {@link RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public @ResponseBody RestApiError handle(final Exception ex) {
		LOG.error("Exception during processing of a request", ex);
		return RestApiError.of(HttpStatus.INTERNAL_SERVER_ERROR.value()).message(GENERIC_USER_MESSAGE).developerMessage(getDeveloperMessage(ex)).build();
	}
	
	/**
	 * Exception handler converting any {@link JsonMappingException} to an <em>HTTP 400</em>.
	 * 
	 * @param ex
	 * @return {@link RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public @ResponseBody RestApiError handle(HttpMessageNotReadableException ex) {
		LOG.error("Exception during processing of a JSON document", ex);
		return RestApiError.of(HttpStatus.BAD_REQUEST.value()).message("Bad Request").developerMessage(getDeveloperMessage(ex)).build();
	}

	/**
	 * <b>Not Found</b> exception handler. All {@link NotFoundException not found exception}s are mapped to {@link HttpStatus#NOT_FOUND
	 * <em>404 Not Found</em>} in case of the absence of an instance resource.
	 * 
	 * @param ex
	 * @return {@link RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public @ResponseBody RestApiError handle(final NotFoundException ex) {
		return RestApiError.of(HttpStatus.NOT_FOUND.value()).message(ex.getMessage()).developerMessage(getDeveloperMessage(ex)).build();
	}

	/**
	 * Exception handler to return <b>Not Implemented</b> when an {@link UnsupportedOperationException} is thrown from the underlying system.
	 * 
	 * @param ex
	 * @return {@link RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	@ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
	public @ResponseBody RestApiError handle(UnsupportedOperationException ex) {
		return RestApiError.of(HttpStatus.NOT_IMPLEMENTED.value()).developerMessage(getDeveloperMessage(ex))
				.build();
	}

	/**
	 * Exception handler to return <b>Bad Request</b> when an {@link BadRequestException} is thrown from the underlying system.
	 * 
	 * @param ex
	 * @return {@link RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public @ResponseBody RestApiError handle(final BadRequestException ex) {
		return RestApiError.of(HttpStatus.BAD_REQUEST.value()).message(ex.getMessage())
				.developerMessage(getDeveloperMessage(ex)).build();
	}
	
	/**
	 * Exception handler to return <b>Bad Request</b> when an {@link BadRequestException} is thrown from the underlying system.
	 * 
	 * @param ex
	 * @return {@link RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	@ResponseStatus(HttpStatus.CONFLICT)
	public @ResponseBody RestApiError handle(final ConflictException ex) {
		return RestApiError.of(HttpStatus.CONFLICT.value()).message(ex.getMessage())
				.developerMessage(getDeveloperMessage(ex)).build();
	}

	private String getDeveloperMessage(Exception ex) {
		if (ex instanceof NotFoundException) {
			return String.format("The requested instance resource (id = %s, type = %s) is not exists and/or not yet created.",
					((NotFoundException) ex).getKey(), ((NotFoundException) ex).getType());
		} else if (ex instanceof IllegalQueryParameterException) {
			return "One or more supplied query parameters were invalid. Check input values.";
		} else if (ex instanceof BadRequestException) {
			return "Input representation syntax or validation errors. Check input values.";
		} else if (ex instanceof UnsupportedOperationException) {
			return ex.getMessage() == null ? "Not implemented" : ex.getMessage();
		}
		return ex.getMessage();
	}

}
