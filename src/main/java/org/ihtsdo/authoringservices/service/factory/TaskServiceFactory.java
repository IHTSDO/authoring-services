package org.ihtsdo.authoringservices.service.factory;

import org.ihtsdo.authoringservices.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class TaskServiceFactory {

    @Autowired
    private TaskService jiraTaskService;

    @Autowired
    @Qualifier(value = "authoringTaskService")
    private TaskService authoringTaskService;

    public TaskService getInstance(Boolean useNew) {
        if (Boolean.TRUE.equals(useNew)) {
            return authoringTaskService;
        }
        return jiraTaskService;
    }

    public TaskService getInstanceByKey(String taskKey) {
        if (authoringTaskService.isUseNew(taskKey)) {
            return authoringTaskService;
        }
        return jiraTaskService;
    }
}
