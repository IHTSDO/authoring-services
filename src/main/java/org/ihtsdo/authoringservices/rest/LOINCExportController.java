package org.ihtsdo.authoringservices.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.http.entity.ContentType;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.authoringservices.service.loinc.LOINCReferenceSetExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Tag(name = "Export LOINC Reference Set")
@RestController
@RequestMapping
public class LOINCExportController {

	private static final ContentType TSV_CONTENT_TYPE = ContentType.create("text/tab-separated-values", "UTF-8");
	private static final SimpleDateFormat RELEASE_FILE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");

	@Autowired
	private LOINCReferenceSetExportService exportService;

	@Operation(summary = "Export LOINC Reference Set RF2 Delta without UUIDs")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value="/loinc-export/{branchPath}", method=RequestMethod.GET, produces="text/tab-separated-values")
	public void exportLOINCReferenceSetWithoutUuids(@PathVariable String branchPath, HttpServletResponse response) throws BusinessServiceException, IOException {
		response.setContentType(TSV_CONTENT_TYPE.toString());
		setResponseFilename(response);
		exportService.exportDelta(BranchPathUriUtil.decodePath(branchPath), null, response.getOutputStream());
	}

	@Operation(summary = "Export LOINC Reference Set RF2 Delta with UUIDs")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value="/loinc-export/{branchPath}", method=RequestMethod.POST, consumes = "multipart/form-data", produces="text/tab-separated-values")
	public void exportLOINCReferenceSetWithUuids(@PathVariable String branchPath,
												 @RequestParam MultipartFile previousLoincRF2SnapshotFile,
												 HttpServletResponse response) throws BusinessServiceException, IOException {
		response.setContentType(TSV_CONTENT_TYPE.toString());
		setResponseFilename(response);
		exportService.exportDelta(BranchPathUriUtil.decodePath(branchPath), previousLoincRF2SnapshotFile.getInputStream(), response.getOutputStream());
	}

	private void setResponseFilename(HttpServletResponse response) {
		response.setHeader("Content-Disposition",
				"attachment; filename=\"der2_sRefset_LOINCSimpleMapDelta_INT_" + RELEASE_FILE_DATE_FORMAT.format(new Date()) + ".txt\"");
	}

}
