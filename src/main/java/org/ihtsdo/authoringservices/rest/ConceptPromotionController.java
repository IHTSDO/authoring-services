package org.ihtsdo.authoringservices.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ihtsdo.authoringservices.domain.ConceptPromotionRequest;
import org.ihtsdo.authoringservices.service.BranchService;
import org.ihtsdo.authoringservices.service.PromotionService;
import org.ihtsdo.authoringservices.service.PromotionService.CreatedConceptPromotion;
import org.ihtsdo.authoringservices.service.client.ContentRequestServiceClient;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Concept Promotion")
@RestController
@RequestMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
public class ConceptPromotionController {

    @Value("${crs.url}")
    private String contentRequestServiceUrl;

    @Autowired
    private BranchService branchService;

    @Autowired
    private PromotionService promotionService;

    @Autowired
    private SnowstormRestClientFactory snowstormRestClientFactory;

    @Operation(summary = "Request a new CRS ticket for concept promotion", description = "Accepts a project key and task key, or a branch path. Requests for the US extension are created in US CRS; other extensions are created in International CRS.")
    @PostMapping(value = "/request-concept-promotion")
    public ResponseEntity <Void> requestConceptPromotion(@RequestBody ConceptPromotionRequest request) throws BusinessServiceException {
        if (!StringUtils.hasLength(request.getBranchPath()) && !StringUtils.hasLength(request.getProjectKey())) {
            throw new IllegalArgumentException("Project Key or branch path is required.");
        }

        SnowstormRestClient snowstormRestClient = snowstormRestClientFactory.getClient();
        String branchPath;
        CodeSystem codeSystem = null;
        if (StringUtils.hasLength(request.getProjectKey())) {
            branchPath = branchService.getProjectOrTaskBranchPathUsingCache(request.getProjectKey(), request.getTaskKey());
        } else {
            branchPath = request.getBranchPath();
        }
        List <CodeSystem> codeSystems = snowstormRestClient.getCodeSystemsLightweight();
        for(CodeSystem cs: codeSystems) {
            if (!cs.getBranchPath().equalsIgnoreCase("MAIN") && branchPath.startsWith(cs.getBranchPath())) {
                codeSystem = cs;
                break;
            }
        }
        if (codeSystem == null) {
            throw new IllegalArgumentException(String.format("Extension code system not found for branch %s", branchPath));
        }

        CreatedConceptPromotion created = promotionService.requestConceptPromotion(request.getConceptId(), request.isIncludeDependencies(), branchPath, codeSystem);
        String locationBase = created.usExtension()
                ? created.crsBaseUrl() + ContentRequestServiceClient.REQUEST_ENDPOINT + "/"
                : contentRequestServiceUrl + "request/";
        return new ResponseEntity<>(ControllerHelper.getCreatedLocationHeaders(locationBase, created.requestId(), null), HttpStatus.CREATED);
    }
}
