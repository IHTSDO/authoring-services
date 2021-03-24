package org.ihtsdo.authoringservices.rest;

import java.net.URISyntaxException;

import org.ihtsdo.authoringservices.service.JiraUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import net.rcarz.jiraclient.JiraException;

@Api("User Search")
@RestController
@RequestMapping(produces = {MediaType.APPLICATION_JSON_VALUE })
public class JiraUserSearchController {
	
	@Autowired
	private JiraUserService configurationService;
	
	@RequestMapping(value = "/users", method = RequestMethod.GET)
	@ApiOperation( value = "Returns authoring users from Jira")
	@ResponseBody
	public Object getUsers(@ApiParam(value="Contains the properties that can be expanded. Example: users[0:50]")
										@RequestParam(value = "expand", defaultValue = "users[0:50]") String expand) throws JiraException, URISyntaxException {
		return configurationService.getUsers(expand);
	}
	
	@RequestMapping(value = "users/search", method = RequestMethod.GET)
	@ApiOperation( value = "Returns authoring users from Jira by search conditions")
	@ResponseBody
	public Object findUsersByNameAndGroupName(@ApiParam(value="A part of user name that to be searched")
											   @RequestParam("username") String username,
											   @ApiParam(value="Project key. Example: TESTINT2,...")
											   @RequestParam("projectKeys") String projectKeys,
											   @ApiParam(value="Task key. Exameple: TESTINT2-XXX")
											   @RequestParam("issueKey") String issueKey,
											   int maxResults, 
											   int startAt) throws JiraException, URISyntaxException {
		return configurationService.searchUsers(username, projectKeys, issueKey, maxResults, startAt);
	}
	
	@ApiOperation(value="Delete a related link", notes="This endpoint may be used to delete a related link which came from CRS.")
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value = "/issue-key/{issueKey}/issue-link/{linkId}", method = RequestMethod.DELETE)
	public void deleteIssueLink(@ApiParam(value="Task key. Ex: TESTINT2-XXX")
								 @PathVariable final String issueKey,
								 @ApiParam(value="Issue ID. Ex: CRT-XXX") 
								 @PathVariable final String linkId) throws JiraException {
		configurationService.deleteIssueLink(issueKey, linkId);
		
	}
}
