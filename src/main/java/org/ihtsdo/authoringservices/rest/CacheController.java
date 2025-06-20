package org.ihtsdo.authoringservices.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ihtsdo.authoringservices.domain.AuthoringCodeSystem;
import org.ihtsdo.authoringservices.domain.AuthoringProject;
import org.ihtsdo.authoringservices.service.BranchServiceCache;
import org.ihtsdo.authoringservices.service.CacheService;
import org.ihtsdo.authoringservices.service.CodeSystemService;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.authoringservices.service.factory.ProjectServiceFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Branch;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/cache")
@Tag(name = "Cache", description = "-")
public class CacheController {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final CacheService cacheService;

    private final CodeSystemService codeSystemService;

    private final BranchServiceCache branchServiceCache;

    private final ProjectServiceFactory projectServiceFactory;

    @Autowired
    public CacheController(CacheService cacheService, CodeSystemService codeSystemService, BranchServiceCache branchServiceCache, ProjectServiceFactory projectServiceFactory) {
        this.cacheService = cacheService;
        this.codeSystemService = codeSystemService;
        this.branchServiceCache = branchServiceCache;
        this.projectServiceFactory = projectServiceFactory;
    }

    @Operation(summary = "Clear branch cache", description = "-")
    @PostMapping(value = "/clear-branch-cache")
    public ResponseEntity<Void> clearBranchCache(
            @RequestParam(required = false) String branchPath,
            @RequestParam(required = false) String branchPathStartWith) {
        if (branchPath != null) {
            cacheService.clearBranchCache(branchPath);
        }
        if (branchPathStartWith != null) {
            cacheService.clearBranchCacheStartWith(branchPathStartWith);
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Operation(summary = "Regenerate branch cache for code systems/projects", description = "-")
    @PostMapping(value = "/generate-branch-cache")
    public ResponseEntity<Void> generateBranchCache(
            @RequestParam(required = false) Boolean forCodeSystems,
            @RequestParam(required = false) Boolean forProjects) throws BusinessServiceException {
        if (Boolean.TRUE.equals(forCodeSystems)) {
            regenerateCacheForCodeSystems();
        }
        if (Boolean.TRUE.equals(forProjects)) {
            regenerateCacheForProjects();
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }

    private void regenerateCacheForCodeSystems() throws BusinessServiceException {
        List<AuthoringCodeSystem> authoringCodeSystems = codeSystemService.findAll();
        if (!CollectionUtils.isEmpty(authoringCodeSystems)) {
            authoringCodeSystems.forEach(item -> cacheService.clearBranchCache(item.getBranchPath()));

            final List<Branch> branches = new ArrayList<>();
            authoringCodeSystems.forEach(item -> {
                try {
                    branches.add(branchServiceCache.getBranchOrNull(item.getBranchPath()));
                } catch (ServiceException e) {
                    logger.error("Failed to get branch {}. Message: {}", item.getBranchPath(), e.getMessage(), e);
                }
            });
            logger.info("The cache for branch of {} code systems have been re-generated.", branches.size());
        }
    }

    private void regenerateCacheForProjects() throws BusinessServiceException {
        List<AuthoringProject> internalProjects = new ArrayList<>(projectServiceFactory.getInstance(true).listProjects(true, true));
        List<AuthoringProject> jiraProjects = projectServiceFactory.getInstance(false).listProjects(true, true);
        List<AuthoringProject> projects = filterJiraProjects(jiraProjects, internalProjects);

        if (!CollectionUtils.isEmpty(projects)) {
            projects.forEach(item -> cacheService.clearBranchCache(item.getBranchPath()));

            final List<Branch> branches = new ArrayList<>();
            projects.forEach(item -> {
                try {
                    branches.add(branchServiceCache.getBranchOrNull(item.getBranchPath()));
                } catch (ServiceException e) {
                    logger.error("Failed to get branch {}. Message: {}", item.getBranchPath(), e.getMessage(), e);
                }
            });
            logger.info("The cache for branch of {} projects have been re-generated.", branches.size());
        }
    }

    private List<AuthoringProject> filterJiraProjects(List<AuthoringProject> jiraProjects, List<AuthoringProject> authoringProjects) {
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
