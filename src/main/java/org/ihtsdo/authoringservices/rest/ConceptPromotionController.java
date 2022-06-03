package org.ihtsdo.authoringservices.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.ihtsdo.authoringservices.domain.ConceptPromotionRequest;
import org.ihtsdo.authoringservices.service.BranchService;
import org.ihtsdo.authoringservices.service.PromotionService;
import org.ihtsdo.authoringservices.service.SnowstormClassificationClient;
import org.ihtsdo.authoringservices.service.TaskService;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystem;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@Api("Concept Promotion")
@RestController
@RequestMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
public class ConceptPromotionController {

    @Value("${crs.url}")
    private String contentRequestServiceUrl;

    @Autowired
    private TaskService taskService;

    @Autowired
    private PromotionService promotionService;

    @Autowired
    private SnowstormRestClientFactory snowstormRestClientFactory;

    @ApiOperation(value = "Request a new CRS ticket for concept promotion.", notes = "This API can support for either project key + task key or only branch path")
    @RequestMapping(value = "/request-concept-promotion", method = RequestMethod.POST)
    public ResponseEntity <Void> requestConceptPromotion(@RequestBody ConceptPromotionRequest request) throws BusinessServiceException {
        if (!StringUtils.hasLength(request.getBranchPath()) && !StringUtils.hasLength(request.getProjectKey())) {
            throw new IllegalArgumentException("Project Key or branch path is required.");
        }

        SnowstormRestClient snowstormRestClient = snowstormRestClientFactory.getClient();
        String branchPath;
        CodeSystem codeSystem = null;
        if (StringUtils.hasLength(request.getProjectKey())) {
            branchPath = taskService.getBranchPathUsingCache(request.getProjectKey(), request.getTaskKey());
        } else {
            branchPath = request.getBranchPath();
        }
        List <CodeSystem> codeSystems = snowstormRestClient.getCodeSystems();
        for(CodeSystem cs: codeSystems) {
            if (!cs.getBranchPath().equalsIgnoreCase("MAIN") && branchPath.startsWith(cs.getBranchPath())) {
                codeSystem = cs;
                break;
            }
        }
        if (codeSystem == null) {
            throw new IllegalArgumentException(String.format("Extension code system not found for branch %s", branchPath));
        }

        String requestId = promotionService.requestConceptPromotion(request.getConceptId(), request.isIncludeDependencies(), branchPath, codeSystem);
        return new ResponseEntity<>(ControllerHelper.getCreatedLocationHeaders(contentRequestServiceUrl + "request/" , requestId, null), HttpStatus.CREATED);
    }
}
