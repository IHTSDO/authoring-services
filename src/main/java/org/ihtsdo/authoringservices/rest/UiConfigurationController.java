package org.ihtsdo.authoringservices.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ihtsdo.authoringservices.domain.UiConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Tag(name = "UI Configuration")
@RestController
@RequestMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
public class UiConfigurationController {

	@Autowired
	private UiConfiguration uiConfiguration;

	@Operation(summary = "Retrieve configuration for the UI")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value="/ui-configuration", method= RequestMethod.GET)
	public UiConfiguration retrieveUiConfiguration() throws IOException {
		return uiConfiguration;
	}

}
