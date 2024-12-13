package org.ihtsdo.authoringservices.service.impl;

import com.google.common.collect.ImmutableMap;
import org.ihtsdo.authoringservices.domain.AuthoringProject;
import org.ihtsdo.authoringservices.domain.ValidationJobStatus;
import org.ihtsdo.authoringservices.entity.Validation;
import org.ihtsdo.authoringservices.service.BranchService;
import org.ihtsdo.authoringservices.service.ValidationService;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.otf.rest.client.terminologyserver.PathHelper;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Branch;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public abstract class ProjectServiceBase {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected abstract List<AuthoringProject> buildAuthoringProjects(Collection<?> projects, Boolean lightweight );

    protected Branch getParentBranch(BranchService branchService, Map<String, Branch> parentBranchCache, String parentPath) throws ServiceException {
        Branch parentBranchOrNull = parentBranchCache.get(parentPath);
        if (parentBranchOrNull == null) {
            parentBranchOrNull = branchService.getBranchOrNull(parentPath);

        }
        return parentBranchOrNull;
    }

    protected CodeSystem getCodeSystemForProject(List<CodeSystem> codeSystems, String parentPath) {
        CodeSystem codeSystem = codeSystems.stream().filter(c -> parentPath.equals(c.getBranchPath())).findFirst().orElse(null);
        if (codeSystem == null && parentPath.contains("/")) {
            // Attempt match using branch grandfather
            String grandfatherPath = PathHelper.getParentPath(parentPath);
            codeSystem = codeSystems.stream().filter(c -> grandfatherPath.equals(c.getBranchPath())).findFirst().orElse(null);
        }
        return codeSystem;
    }

    protected void populateValidationStatusForProjects(ValidationService validationService, Set<String> branchPaths, List<AuthoringProject> authoringProjects, Boolean lightweight) {
        if (!Boolean.TRUE.equals(lightweight)) {
            final ImmutableMap<String, Validation> validationMap;
            try {
                validationMap = validationService.getValidations(branchPaths);
                for (AuthoringProject authoringProject : authoringProjects) {
                    populateValidationStatusForProject(authoringProject, validationMap);
                }
            } catch (ExecutionException e) {
                logger.warn("Failed to fetch validation statuses for branch paths {}", branchPaths);
            }
        }
    }

    protected void populateValidationStatusForProject(AuthoringProject authoringProject, ImmutableMap<String, Validation> validationMap) {
        try {
            String branchPath = authoringProject.getBranchPath();
            Validation validation = validationMap.get(branchPath);
            if (validation != null) {
                if (ValidationJobStatus.COMPLETED.name().equals(validation.getStatus())
                        && validation.getContentHeadTimestamp() != null
                        && authoringProject.getBranchHeadTimestamp() != null
                        && !authoringProject.getBranchHeadTimestamp().equals(validation.getContentHeadTimestamp())) {
                    authoringProject.setValidationStatus(ValidationJobStatus.STALE.name());
                } else {
                    authoringProject.setValidationStatus(validation.getStatus());
                }
            }

        } catch (Exception e) {
            logger.error("Failed to recover/set validation status for " + authoringProject.getKey(), e);
        }
    }
}
