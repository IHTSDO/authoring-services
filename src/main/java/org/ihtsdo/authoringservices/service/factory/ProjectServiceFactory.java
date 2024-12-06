package org.ihtsdo.authoringservices.service.factory;

import org.ihtsdo.authoringservices.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class ProjectServiceFactory {

    @Autowired
    private ProjectService jiraProjectService;

    @Autowired
    @Qualifier(value = "authoringProjectService")
    private ProjectService authoringProjectService;


    public ProjectService getInstance(Boolean useNew) {
        if (Boolean.TRUE.equals(useNew)) {
            return authoringProjectService;
        }
        return  jiraProjectService;
    }
}
