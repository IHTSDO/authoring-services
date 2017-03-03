package org.ihtsdo.snowowl.authoring.single.api.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import net.rcarz.jiraclient.JiraException;
import org.apache.http.entity.ContentType;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.*;
import org.ihtsdo.snowowl.authoring.single.api.service.loinc.LOINCReferenceSetExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;

@Api("Export LOINC Reference Set")
@RestController
@RequestMapping
public class LOINCExportController {

	private static final ContentType TSV_CONTENT_TYPE = ContentType.create("text/tab-separated-values", "UTF-8");

	@Autowired
	private LOINCReferenceSetExportService exportService;

	@ApiOperation(value="Retrieve LOINC Reference Set Export")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/loinc-export/{branchPath}", method=RequestMethod.GET, produces="text/tab-separated-values")
	public void listProjects(@PathVariable String branchPath, HttpServletResponse response) throws BusinessServiceException, IOException {
		response.setContentType(TSV_CONTENT_TYPE.toString());
		exportService.exportDelta(branchPath, response.getOutputStream());
	}

}
