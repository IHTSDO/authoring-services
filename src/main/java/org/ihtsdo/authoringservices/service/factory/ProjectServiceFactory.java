package org.ihtsdo.authoringservices.service.factory;

import org.ihtsdo.authoringservices.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProjectServiceFactory {

    @Value("${jira.enabled}")
    private boolean jiraEnabled;

    @Autowired
    private ProjectService jiraProjectService;

    @Autowired
    @Qualifier(value = "authoringProjectService")
    private ProjectService authoringProjectService;


    @Autowired
    @Qualifier(value = "defaultProjectService")
    private ProjectService defaultProjectService;

    public ProjectService getInstance(Boolean useNew) {
        if (Boolean.TRUE.equals(useNew)) {
            return authoringProjectService;
        } else if (jiraEnabled) {
            return  jiraProjectService;
        } else {
            return defaultProjectService;
        }
    }

    public ProjectService getInstanceByKey(String projectKey) {
        if (authoringProjectService.isUseNew(projectKey)) {
            return authoringProjectService;
        } else if (jiraEnabled) {
            return  jiraProjectService;
        } else {
            return defaultProjectService;
        }
    }
}
