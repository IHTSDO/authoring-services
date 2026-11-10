package org.ihtsdo.authoringservices.service;

import com.google.common.cache.LoadingCache;
import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;

import java.util.List;

public interface ProjectService {

    boolean exists(String projectKey);

    AuthoringProject createProject(CreateProjectRequest request, AuthoringCodeSystem codeSystem) throws BusinessServiceException;

    AuthoringProject updateProject(String projectKey, AuthoringProject updatedProject) throws BusinessServiceException;

    void deleteProject(String projectKey) throws BusinessServiceException;

    List<AuthoringProjectField> retrieveProjectCustomFields(String projectKey) throws BusinessServiceException;

    List<AuthoringProject> listProjects(Boolean lightweight, Boolean ignoreProductCodeFilter, Boolean excludeArchived) throws BusinessServiceException;

    /**
     * @param includeReviewerOnlyProjects when false, omit projects where the user only has a reviewer role.
     *                                    The Authoring Platform uses that default because those users cannot create tasks.
     *                                    The terminology browser passes true so reviewers can still select a project.
     */
    default List<AuthoringProject> listProjects(Boolean lightweight, Boolean ignoreProductCodeFilter, Boolean excludeArchived, boolean includeReviewerOnlyProjects) throws BusinessServiceException {
        return listProjects(lightweight, ignoreProductCodeFilter, excludeArchived);
    }

    List<AuthoringProject> listMyProjects(String username, Boolean lightweight) throws BusinessServiceException;

    AuthoringProject retrieveProject(String projectKey) throws BusinessServiceException;

    AuthoringProject retrieveProject(String projectKey, boolean lightweight) throws BusinessServiceException;

    void lockProject(String projectKey) throws BusinessServiceException;

    void unlockProject(String projectKey) throws BusinessServiceException;

    String getProjectBaseUsingCache(String projectKey) throws BusinessServiceException;

    LoadingCache<String, ProjectDetails> getProjectDetailsCache();

    void addCommentLogErrors(String projectKey, String commentString) throws BusinessServiceException;

    void updateProjectCustomFields(String projectKey, ProjectFieldUpdateRequest request) throws BusinessServiceException;

    List<String> retrieveProjectRoles(String projectKey) throws BusinessServiceException;

    void updateProjectRoles(String projectKey, ProjectRoleUpdateRequest request) throws BusinessServiceException;
}
