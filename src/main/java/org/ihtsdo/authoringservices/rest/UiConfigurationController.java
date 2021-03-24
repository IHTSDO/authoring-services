package org.ihtsdo.authoringservices.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.ihtsdo.authoringservices.domain.UiConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Api("UI Configuration")
@RestController
@RequestMapping(produces={MediaType.APPLICATION_JSON_VALUE})
public class UiConfigurationController {

	@Autowired
	private UiConfiguration uiConfiguration;

	@ApiOperation(value="Retrieve configuration for the UI.")
	@ApiResponses({@ApiResponse(code = 200, message = "OK")})
	@RequestMapping(value="/ui-configuration", method= RequestMethod.GET)
	public UiConfiguration retrieveUiConfiguration() throws IOException {
		return uiConfiguration;
	}

}
