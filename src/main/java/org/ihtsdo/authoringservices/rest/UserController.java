package org.ihtsdo.authoringservices.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ihtsdo.authoringservices.domain.JiraUser;
import org.ihtsdo.authoringservices.domain.JiraUserGroup;
import org.ihtsdo.authoringservices.domain.User;
import org.ihtsdo.authoringservices.domain.UserGroupItem;
import org.ihtsdo.authoringservices.service.UserCacheService;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "User Search")
@RestController
@RequestMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
public class UserController {

    @Value("${jira.groupName:ihtsdo-sca-author}")
    private String defaultGroupName;

    private final UserCacheService userCacheService;

    public UserController(UserCacheService userCacheService) {
        this.userCacheService = userCacheService;
    }

    @GetMapping(value = "/users")
    @Operation(summary = "Returns authoring users from Jira")
    public JiraUserGroup getUsers(
            @RequestParam(required = false) String groupName,
            @RequestParam(required = false, defaultValue = "0") int offset,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        String groupNameToSearch = StringUtils.hasLength(groupName) ? groupName : defaultGroupName;
        List<User> allUsers = userCacheService.getAllUsersForGroup(groupNameToSearch.trim());

        JiraUserGroup result = new JiraUserGroup();
        result.setName(groupName);

        UserGroupItem userGroupItem = new UserGroupItem();
        userGroupItem.setSize(allUsers.size());
        int to = Math.min((offset + limit), allUsers.size());
        List<JiraUser> items = new ArrayList<>();
        for (User user : allUsers.subList(offset, to)) {
            items.add(toJiraUser(user));
        }
        userGroupItem.setItems(items);
        result.setUsers(userGroupItem);
        return result;
    }

    @GetMapping(value = "users/search")
    @Operation(summary = "Returns authoring users from Jira by search conditions")
    public List<JiraUser> findUsersByUsername(
            @Parameter(description = "A part of user name that to be searched") @RequestParam("username") String username,
            int maxResults,
            int startAt) {
        List<User> allUsers = userCacheService.getAllUsersForGroup(defaultGroupName);
        List<User> filteredUsers = allUsers.stream().filter(item -> item.getUsername().contains(username.toLowerCase()) || item.getDisplayName().toLowerCase().contains(username.toLowerCase())).toList();
        int to = Math.min((startAt + maxResults), filteredUsers.size());
        List<JiraUser> result = new ArrayList<>();
        for (User user : filteredUsers.subList(startAt, to)) {
            result.add(toJiraUser(user));
        }
        return result;
    }

    @NotNull
    private JiraUser toJiraUser(User user) {
        JiraUser jiraUser = new JiraUser();
        jiraUser.setName(user.getUsername());
        jiraUser.setKey(user.getUsername());
        jiraUser.setDisplayName(user.getDisplayName());
        jiraUser.setActive(user.isActive());
        return jiraUser;
    }
}
