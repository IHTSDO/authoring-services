package org.ihtsdo.authoringservices.service.util;

import org.ihtsdo.authoringservices.domain.AuthoringProject;

import java.util.ArrayList;
import java.util.List;

public class ProjectFilterUtil {
    private ProjectFilterUtil() {}

    public static List<AuthoringProject> joinJiraProjectsIfNotExists(List<AuthoringProject> jiraProjects, List<AuthoringProject> authoringProjects) {
        if (jiraProjects.isEmpty()) return authoringProjects;
        List<String> authoringProjectKeys = new ArrayList<>(authoringProjects.stream().map(AuthoringProject::getKey).toList());
        for (AuthoringProject project : jiraProjects) {
            if (!authoringProjectKeys.contains(project.getKey())) {
                authoringProjects.add(project);
                authoringProjectKeys.add(project.getKey());
            }
        }
        return authoringProjects;
    }
}
