package org.ihtsdo.authoringservices.rest;

import com.google.gson.Gson;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.authoringservices.domain.JiraUser;
import org.ihtsdo.authoringservices.domain.JiraUserGroup;
import org.ihtsdo.authoringservices.service.JiraUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@Tag(name = "User Search")
@RestController
@RequestMapping(produces = {MediaType.APPLICATION_JSON_VALUE })
public class JiraUserSearchController {
	
	@Autowired
	private JiraUserService configurationService;
	
	@RequestMapping(value = "/users", method = RequestMethod.GET)
	@Operation(summary = "Returns authoring users from Jira")
	@ResponseBody
	public JiraUserGroup getUsers(
			@RequestParam(required = false) String groupName,
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "50") int limit) throws JiraException {
		Gson gson = new Gson();
		if (StringUtils.hasLength(groupName)) {
			return gson.fromJson(configurationService.findUsersByGroupName(groupName, offset, limit).toString(), JiraUserGroup.class);
		} else {
			return gson.fromJson(configurationService.getUsers(offset, limit).toString(), JiraUserGroup.class);
		}
	}


	
	@RequestMapping(value = "users/search", method = RequestMethod.GET)
	@Operation(summary = "Returns authoring users from Jira by search conditions")
	@ResponseBody
	public List<JiraUser> findUsersByNameAndGroupName(
			@Parameter(description = "A part of user name that to be searched") @RequestParam("username") String username,
			@Parameter(description = "Project key. Example: TESTINT2,...") @RequestParam("projectKeys") String projectKeys,
			@Parameter(description = "Task key. Example: TESTINT2-XXX") @RequestParam("issueKey") String issueKey,
			int maxResults,
			int startAt) throws JiraException {
		Gson gson = new Gson();
		JiraUser[] userArray = gson.fromJson(configurationService.searchUsers(username, projectKeys, issueKey, maxResults, startAt).toString(), JiraUser[].class);
		return Arrays.asList(userArray);
	}
	
	@Operation(summary = "Delete a related link", description = "This endpoint may be used to delete a related link which came from CRS.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value = "/issue-key/{issueKey}/issue-link/{linkId}", method = RequestMethod.DELETE)
	public void deleteIssueLink(
			@Parameter(description = "Task key. Example: TESTINT2-XXX") @PathVariable final String issueKey,
			@Parameter(description = "Issue ID. Example: CRT-XXX") @PathVariable final String linkId) throws JiraException {
		configurationService.deleteIssueLink(issueKey, linkId);
	}
}
