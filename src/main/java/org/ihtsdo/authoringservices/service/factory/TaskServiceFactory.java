package org.ihtsdo.authoringservices.service.factory;

import org.ihtsdo.authoringservices.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TaskServiceFactory {

    @Value("${jira.enabled}")
    private boolean jiraEnabled;

    @Autowired
    private TaskService jiraTaskService;

    @Autowired
    @Qualifier(value = "authoringTaskService")
    private TaskService authoringTaskService;


    @Autowired
    @Qualifier(value = "defaultTaskService")
    private TaskService defaultTaskService;

    public TaskService getInstance(Boolean useNew) {
        if (Boolean.TRUE.equals(useNew)) {
            return authoringTaskService;
        } else if (jiraEnabled){
            return jiraTaskService;
        } else {
            return defaultTaskService;
        }
    }

    public TaskService getInstanceByKey(String taskKey) {
        if (authoringTaskService.isUseNew(taskKey)) {
            return authoringTaskService;
        } else if (jiraEnabled) {
            return jiraTaskService;
        } else {
            return defaultTaskService;
        }
    }
}
