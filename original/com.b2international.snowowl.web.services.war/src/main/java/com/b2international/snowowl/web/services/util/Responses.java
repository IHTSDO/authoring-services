package com.b2international.snowowl.web.services.util;

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Contains handy utility methods to work with responses in Spring MVC.
 * 
 * @author mczotter
 * @since 1.0
 */
public class Responses {

	private Responses() {
	}

	/**
	 * Creates a {@link ResponseBuilder} with the HTTP status code CREATED.
	 * 
	 * @param location
	 *            - the Location header value to find the created resource.
	 * @return
	 */
	public static final ResponseBuilder created(URI location) {
		return status(HttpStatus.CREATED).location(location);
	}

	/**
	 * Creates a {@link ResponseBuilder} with the HTTP status code OK.
	 * 
	 * @return
	 */
	public static final ResponseBuilder ok() {
		return status(HttpStatus.OK);
	}

	/**
	 * Creates a {@link ResponseBuilder} with the HTTP status code NOT_MODIFIED.
	 * 
	 * @param the
	 *            - HTTP ETag value associated with the not modified response.
	 * @return
	 */
	public static final ResponseBuilder notModified(String tag) {
		return status(HttpStatus.NOT_MODIFIED);
	}

	/**
	 * Creates a {@link ResponseBuilder} with the given HTTP status code.
	 * 
	 * @param status
	 * @return
	 */
	public static final ResponseBuilder status(HttpStatus status) {
		return builder().status(status);
	}

	/**
	 * Creates an empty {@link ResponseBuilder} instance.
	 * 
	 * @return
	 */
	public static ResponseBuilder builder() {
		return new ResponseBuilder();
	}

	/**
	 * {@link ResponseBuilder}
	 * 
	 * @author mczotter
	 * @since 1.0
	 */
	public static class ResponseBuilder {

		private HttpStatus status;
		private HttpHeaders headers = new HttpHeaders();

		/**
		 * Sets the HTTP status to the given status.
		 * 
		 * @param status
		 *            - the HTTP status to set, may not be <code>null</code>.
		 * @return
		 */
		public ResponseBuilder status(HttpStatus status) {
			this.status = checkNotNull(status, "Status must be defined");
			return this;
		}

		/**
		 * Sets the location header to the given URI value.
		 * 
		 * @param location
		 * @return
		 */
		public ResponseBuilder location(URI location) {
			headers.setLocation(location);
			return this;
		}

		/**
		 * Adds a custom header name - value.
		 * 
		 * @param name
		 * @param value
		 * @return
		 */
		public ResponseBuilder header(String name, String value) {
			headers.add(name, value);
			return this;
		}

		/**
		 * Builds a {@link ResponseEntity} from the accumulated response properties with the given entity as body.
		 * 
		 * @param body
		 * @return
		 */
		public <T> ResponseEntity<T> build(T body) {
			if (!headers.isEmpty()) {
				return new ResponseEntity<T>(body, headers, status);
			}
			return new ResponseEntity<T>(body, status);
		}

		/**
		 * Builds a {@link ResponseEntity} from the accumulated response properties with a {@link Void} <code>null</code> body.
		 * 
		 * @return
		 */
		public ResponseEntity<Void> build() {
			return build((Void)null);
		}

	}

}
