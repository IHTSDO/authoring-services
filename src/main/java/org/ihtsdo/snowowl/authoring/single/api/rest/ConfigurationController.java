package org.ihtsdo.snowowl.authoring.single.api.rest;

import java.net.URISyntaxException;

import org.ihtsdo.snowowl.authoring.single.api.service.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import net.rcarz.jiraclient.JiraException;

@Api("Configuration")
@RestController
@RequestMapping(produces = {MediaType.APPLICATION_JSON_VALUE })
public class ConfigurationController {
	
	@Autowired
	private ConfigurationService configurationService;
	
	@RequestMapping(value = "/users", method = RequestMethod.GET)
	@ApiOperation( value = "Returns authoring users from Jira by group name")
	@ResponseBody
	public Object findtUsersByGroupName(@ApiParam(value="Contains the properties that can be expanded. Example: users[0:50]") 
										@RequestParam("expand") String expand, 
										@ApiParam(value="Group name. Example: ihtsdo-sca-author")
										@RequestParam("groupName")String groupName) throws JiraException, URISyntaxException {
		return configurationService.findtUsersByGroupName(expand, groupName);
	}
	
	@RequestMapping(value = "users/search", method = RequestMethod.GET)
	@ApiOperation( value = "Returns authoring users from Jira by search conditions")
	@ResponseBody
	public Object findtUsersByNameAndGroupName(@ApiParam(value="A part of user name that to be searched") 
											   @RequestParam("username") String username, 
											   @ApiParam(value="Group name. Example: ihtsdo-sca-author") 
											   @RequestParam("groupName")	String groupName, 
											   @ApiParam(value="Project key. Example: TESTINT2,...") 
											   @RequestParam("projectKeys") String projectKeys, 
											   @ApiParam(value="Task key. Exameple: TESTINT2-XXX") 
											   @RequestParam("issueKey") String issueKey,
											   int maxResults, 
											   int startAt) throws JiraException, URISyntaxException {
		return configurationService.searchUsers(username, groupName, projectKeys, issueKey, maxResults, startAt);
	}
}
