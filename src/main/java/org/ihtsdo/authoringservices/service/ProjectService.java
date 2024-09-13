package org.ihtsdo.authoringservices.service;

import com.google.common.cache.LoadingCache;
import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;

import java.util.List;

public interface ProjectService {
    AuthoringProject createProject(CreateProjectRequest request, String codeSystemBranchPath) throws BusinessServiceException;

    AuthoringProject updateProject(String projectKey, AuthoringProject updatedProject) throws BusinessServiceException;

    void deleteProject(String projectKey) throws BusinessServiceException;

    List<AuthoringProjectField> retrieveProjectCustomFields(String projectKey) throws BusinessServiceException;

    List<AuthoringProject> listProjects(Boolean lightweight, Boolean ignoreProductCodeFilter) throws BusinessServiceException;

    AuthoringProject retrieveProject(String projectKey) throws BusinessServiceException;

    AuthoringProject retrieveProject(String projectKey, boolean lightweight) throws BusinessServiceException;

    void lockProject(String projectKey) throws BusinessServiceException;

    void unlockProject(String projectKey) throws BusinessServiceException;

    String getProjectBaseUsingCache(String projectKey) throws BusinessServiceException;

    LoadingCache<String, ProjectDetails> getProjectDetailsCache();

    void addCommentLogErrors(String projectKey, String commentString) throws BusinessServiceException;

    void updateProjectCustomFields(String projectKey, ProjectFieldUpdateRequest request) throws BusinessServiceException;
}
