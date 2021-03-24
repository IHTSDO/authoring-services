package org.ihtsdo.authoringservices;

import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.authoringservices.configuration.Configuration;
import org.ihtsdo.authoringservices.service.TaskService;
import org.ihtsdo.authoringservices.service.jira.ImpersonatingJiraClientFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
@SpringBootApplication
public class TestConfig extends Configuration {

    @Bean
    public TaskService taskService(@Autowired ImpersonatingJiraClientFactory jiraClientFactory, @Value("${jira.username}") String jiraUsername) throws JiraException {
        return new TaskService(null, "UNIT_TEST");
    }

}
