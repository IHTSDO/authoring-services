package org.ihtsdo.authoringservices.rest;

import io.swagger.annotations.*;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.authoringservices.service.JiraUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@Api("User Search")
@RestController
@RequestMapping(produces = {MediaType.APPLICATION_JSON_VALUE })
public class JiraUserSearchController {
	
	@Autowired
	private JiraUserService configurationService;
	
	@RequestMapping(value = "/users", method = RequestMethod.GET)
	@ApiOperation( value = "Returns authoring users from Jira")
	@ResponseBody
	public Object getUsers(
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "50") int limit) throws JiraException {

		return configurationService.getUsers(offset, limit);
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
											   int startAt) throws JiraException {
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
