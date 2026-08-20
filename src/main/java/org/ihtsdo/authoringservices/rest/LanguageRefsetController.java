package org.ihtsdo.authoringservices.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ihtsdo.authoringservices.service.LanguageRefsetService;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ConceptMiniPojo;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "Language Reference Sets")
@RestController
@RequestMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
public class LanguageRefsetController {

	public record LanguageRefset(String conceptId, boolean active, String fsn, String pt) {
	}

	private final LanguageRefsetService languageRefsetService;

	public LanguageRefsetController(LanguageRefsetService languageRefsetService) {
		this.languageRefsetService = languageRefsetService;
	}

	@Operation(summary = "List language reference sets on a branch",
			description = "Returns only language reference sets (type 900000000000506000) for the given branch, "
					+ "so clients do not need to fetch all reference set members and filter client-side. "
					+ "Preferred terms are resolved using the Accept-Language header.",
			parameters = {
					@Parameter(name = HttpHeaders.ACCEPT_LANGUAGE, in = ParameterIn.HEADER, required = false,
							description = "Language dialects used to resolve preferred terms. "
									+ "Supports SNOMED dialect syntax, for example en-US;q=0.8,en-GB;q=0.5 "
									+ "or en-X-900000000000509007,en-X-900000000000508004,en.",
							example = "en-US;q=0.8,en-GB;q=0.5")
			})
	@ApiResponse(responseCode = "200", description = "OK")
	@GetMapping(value = "/branches/{branch}/language-refsets")
	public List<LanguageRefset> getLanguageRefsets(@PathVariable String branch,
			@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
		String branchPath = BranchPathUriUtil.decodePath(branch);
		List<LanguageRefset> results = new ArrayList<>();
		for (ConceptMiniPojo concept : languageRefsetService.getLanguageRefsets(branchPath, acceptLanguage)) {
			if (concept != null && StringUtils.hasLength(concept.getConceptId())) {
				results.add(toLanguageRefset(concept));
			}
		}
		return results;
	}

	private LanguageRefset toLanguageRefset(ConceptMiniPojo concept) {
		return new LanguageRefset(concept.getConceptId(), Boolean.TRUE.equals(concept.getActive()), termOf(concept.getFsn()), termOf(concept.getPt()));
	}

	private String termOf(ConceptMiniPojo.DescriptionMiniPojo descriptionMiniPojo) {
		if (descriptionMiniPojo != null && StringUtils.hasLength(descriptionMiniPojo.getTerm())) {
			return descriptionMiniPojo.getTerm();
		}
		return "";
	}
}
