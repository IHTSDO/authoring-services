package org.ihtsdo.authoringservices.service;

import com.google.common.cache.LoadingCache;
import net.sf.json.JSONObject;
import org.ihtsdo.authoringservices.domain.AuthoringProject;
import org.ihtsdo.authoringservices.domain.CreateProjectRequest;
import org.ihtsdo.authoringservices.domain.ProjectDetails;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;

import java.util.List;

public interface ProjectService {
    AuthoringProject createProject(CreateProjectRequest request) throws BusinessServiceException;

    AuthoringProject updateProject(String projectKey, AuthoringProject updatedProject) throws BusinessServiceException;

    void deleteProject(String projectKey) throws BusinessServiceException;

    List<JSONObject> retrieveProjectCustomFields(String projectKey) throws BusinessServiceException;

    List<AuthoringProject> listProjects(Boolean lightweight) throws BusinessServiceException;

    AuthoringProject retrieveProject(String projectKey) throws BusinessServiceException;

    AuthoringProject retrieveProject(String projectKey, boolean lightweight) throws BusinessServiceException;

    void lockProject(String projectKey) throws BusinessServiceException;

    void unlockProject(String projectKey) throws BusinessServiceException;

    String getProjectBaseUsingCache(String projectKey) throws BusinessServiceException;

    LoadingCache<String, ProjectDetails> getProjectDetailsCache();

    void addCommentLogErrors(String projectKey, String commentString) throws BusinessServiceException;
}
