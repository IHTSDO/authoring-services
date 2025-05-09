package org.ihtsdo.authoringservices.rest;

import com.google.common.base.Strings;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;

class ControllerHelper {

	public static final String PROJECT_KEY = "projectKey";
	public static final String TASK_KEY = "taskKey";

	private ControllerHelper() {
	}

	// This request value commonly comes from a Javascript application that has event sequencing issues.
	public static final String UNDEFINED = "undefined";

	public static String requiredParam(String value, String paramName) {
		if (Strings.isNullOrEmpty(value) || UNDEFINED.equals(value)) {
			throw new IllegalArgumentException(String.format("Parameter %s is required.", paramName));
		}
		return value;
	}

	public static ResponseEntity <Void> getCreatedResponse(String id) {
		return getCreatedResponse(id, null);
	}

	static ResponseEntity<Void> getCreatedResponse(String id, String removePathPart) {
		HttpHeaders httpHeaders = getCreatedLocationHeaders(id, removePathPart);
		return new ResponseEntity<>(httpHeaders, HttpStatus.CREATED);
	}

	static HttpHeaders getCreatedLocationHeaders(String id) {
		return getCreatedLocationHeaders(id, null);
	}

	static HttpHeaders getCreatedLocationHeaders(String id, String removePathPart) {
		RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
		Assert.state(attrs instanceof ServletRequestAttributes, "No current ServletRequestAttributes");
		HttpServletRequest request = ((ServletRequestAttributes) attrs).getRequest();

		String requestUrl = request.getRequestURL().toString();
		// Decode branch path
		requestUrl = requestUrl.replace("%7C", "/");
		if (!Strings.isNullOrEmpty(removePathPart)) {
			requestUrl = requestUrl.replace(removePathPart, "");
		}

		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setLocation(ServletUriComponentsBuilder.fromHttpUrl(requestUrl).path("/{id}").buildAndExpand(id).toUri());
		return httpHeaders;
	}

	static HttpHeaders getCreatedLocationHeaders(String requestUrl,  String id, String removePathPart) {
		RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
		Assert.state(attrs instanceof ServletRequestAttributes, "No current ServletRequestAttributes");
		HttpServletRequest request = ((ServletRequestAttributes) attrs).getRequest();

		if (!Strings.isNullOrEmpty(removePathPart)) {
			requestUrl = requestUrl.replace(removePathPart, "");
		}

		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setLocation(ServletUriComponentsBuilder.fromHttpUrl(requestUrl).path("/{id}").buildAndExpand(id).toUri());
		return httpHeaders;
	}

	static Pageable setPageDefaults(Pageable page) {
		if (page == null) {
			page = PageRequest.of(0, 10);
		} else {
			page = PageRequest.of(page.getPageNumber(), Math.min(page.getPageSize(), 500), page.getSort());
		}
		if (Sort.unsorted() == page.getSort()) {
			page = PageRequest.of(page.getPageNumber(), page.getPageSize());
		}
		return page;
	}
}
