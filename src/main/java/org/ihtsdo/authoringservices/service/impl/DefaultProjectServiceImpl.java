package org.ihtsdo.authoringservices.service.impl;

import com.google.common.cache.LoadingCache;
import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.authoringservices.service.ProjectService;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;

import java.util.Collections;
import java.util.List;

public class DefaultProjectServiceImpl implements ProjectService {
    @Override
    public boolean isUseNew(String projectKey) {
        throw new UnsupportedOperationException("isUseNew is not supported");
    }

    @Override
    public AuthoringProject createProject(CreateProjectRequest request, AuthoringCodeSystem codeSystem) throws BusinessServiceException {
        throw new UnsupportedOperationException("Project creation is not supported");
    }

    @Override
    public AuthoringProject updateProject(String projectKey, AuthoringProject updatedProject) throws BusinessServiceException {
        throw new UnsupportedOperationException("Project update is not supported");
    }

    @Override
    public void deleteProject(String projectKey) throws BusinessServiceException {
        // Do nothing
    }

    @Override
    public List<AuthoringProjectField> retrieveProjectCustomFields(String projectKey) throws BusinessServiceException {
        return Collections.emptyList();
    }

    @Override
    public List<AuthoringProject> listProjects(Boolean lightweight, Boolean ignoreProductCodeFilter, Boolean excludeArchived) throws BusinessServiceException {
        return Collections.emptyList();
    }

    @Override
    public AuthoringProject retrieveProject(String projectKey) throws BusinessServiceException {
        throw new UnsupportedOperationException("Retrieving project is not supported");
    }

    @Override
    public AuthoringProject retrieveProject(String projectKey, boolean lightweight) throws BusinessServiceException {
        throw new UnsupportedOperationException("Retrieving project is not supported");
    }

    @Override
    public void lockProject(String projectKey) throws BusinessServiceException {
        // Do nothing
    }

    @Override
    public void unlockProject(String projectKey) throws BusinessServiceException {
        // Do nothing
    }

    @Override
    public String getProjectBaseUsingCache(String projectKey) throws BusinessServiceException {
        throw new UnsupportedOperationException("Getting project base from cache is not supported");
    }

    @Override
    public LoadingCache<String, ProjectDetails> getProjectDetailsCache() {
        throw new UnsupportedOperationException("Getting project details from cache is not supported");
    }

    @Override
    public void addCommentLogErrors(String projectKey, String commentString) throws BusinessServiceException {
        // Do nothing
    }

    @Override
    public void updateProjectCustomFields(String projectKey, ProjectFieldUpdateRequest request) throws BusinessServiceException {
        // Do nothing
    }

    @Override
    public List<String> retrieveProjectRoles(String projectKey) throws BusinessServiceException {
        return Collections.emptyList();
    }

    @Override
    public void updateProjectRoles(String projectKey, ProjectRoleUpdateRequest request) throws BusinessServiceException {
        // Do nothing
    }
}
