package org.ihtsdo.authoringservices.rest;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import org.ihtsdo.authoringservices.domain.JiraUser;
import org.ihtsdo.authoringservices.domain.JiraUserGroup;
import org.ihtsdo.authoringservices.domain.User;
import org.ihtsdo.authoringservices.domain.UserGroupItem;
import org.ihtsdo.authoringservices.service.client.IMSClientFactory;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Tag(name = "User Search")
@RestController
@RequestMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
public class UserController {

    @Autowired
    private IMSClientFactory imsClientFactory;

    @Value("${jira.groupName:ihtsdo-sca-author}")
    private String defaultGroupName;

    private Cache<String, List<User>> userCache;

    @PostConstruct
    public void init() {
        this.userCache = CacheBuilder.newBuilder().expireAfterAccess(5L, TimeUnit.MINUTES).build();
    }

    @GetMapping(value = "/users")
    @Operation(summary = "Returns authoring users from Jira")
    public JiraUserGroup getUsers(
            @RequestParam(required = false) String groupName,
            @RequestParam(required = false, defaultValue = "0") int offset,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        String groupNameToSearch = StringUtils.hasLength(groupName) ? groupName : defaultGroupName;
        List<User> allUsers = getAllUsers(groupNameToSearch);

        JiraUserGroup result = new JiraUserGroup();
        result.setName(groupName);

        UserGroupItem userGroupItem = new UserGroupItem();
        userGroupItem.setSize(allUsers.size());
        int to = Math.min((offset + limit), allUsers.size());
        List<JiraUser> items = new ArrayList<>();
        for (User user : allUsers.subList(offset, to)) {
            items.add(getNewJiraUser(user));
        }
        userGroupItem.setItems(items);
        result.setUsers(userGroupItem);
        return result;
    }

    @GetMapping(value = "users/search")
    @Operation(summary = "Returns authoring users from Jira by search conditions")
    public List<JiraUser> findUsersByUsername(
            @Parameter(description = "A part of user name that to be searched") @RequestParam("username") String username,
            @Parameter(description = "Project key. Example: TESTINT2,...") @RequestParam("projectKeys") String projectKeys,
            @Parameter(description = "Task key. Example: TESTINT2-XXX") @RequestParam("issueKey") String issueKey,
            int maxResults,
            int startAt) {
        List<User> allUsers = getAllUsers(defaultGroupName);
        List<User> filteredUsers = allUsers.stream().filter(item -> item.getUsername().contains(username.toLowerCase()) || item.getDisplayName().toLowerCase().contains(username.toLowerCase())).toList();
        int to = Math.min((startAt + maxResults), filteredUsers.size());
        List<JiraUser> result = new ArrayList<>();
        for (User user : filteredUsers.subList(startAt, to)) {
            result.add(getNewJiraUser(user));
        }
        return result;
    }

    @NotNull
    private List<User> getAllUsers(String groupName) {
        List<User> allUsers = userCache.getIfPresent(groupName);
        if (allUsers == null) {
            allUsers = new ArrayList<>();
            fetchAllUsersForGroup(groupName, 0, allUsers);
            userCache.put(groupName, allUsers);
        }
        return allUsers;
    }

    private void fetchAllUsersForGroup(String groupName, int offset, List<User> allUsers) {
        List<User> users = imsClientFactory.getClient().searchUserByGroupname(StringUtils.hasLength(groupName) ? groupName : defaultGroupName, offset, 1000);
        allUsers.addAll(users.stream().filter(User::isActive).toList());
        if (users.size() == 1000) {
            fetchAllUsersForGroup(groupName, offset + 1000, allUsers);
        }
    }

    @NotNull
    private JiraUser getNewJiraUser(User user) {
        JiraUser jiraUser = new JiraUser();
        jiraUser.setName(user.getUsername());
        jiraUser.setKey(user.getUsername());
        jiraUser.setDisplayName(user.getDisplayName());
        jiraUser.setActive(user.isActive());
        return jiraUser;
    }
}
