package org.ihtsdo.snowowl.authoring.single.api.rest;

import com.amazonaws.services.s3.model.S3Object;
import io.swagger.annotations.ApiOperation;
import org.ihtsdo.snowowl.authoring.single.api.service.DialectConversionService;
import org.ihtsdo.snowowl.authoring.single.api.service.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.lang.model.type.NullType;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

@RestController
public class DialectConversionController {

	@Autowired
	private DialectConversionService dialectConversionService;

	@ApiOperation(value = "Convert words from the EN-US to EN-GB dialect.",
			notes = "Submit an array of words. The response is a map of any words found in " +
					"the US dialect with an equivalent word in the GB dialect.")
	@ResponseBody
	@RequestMapping(value = "/dialect/en-us/map/en-gb", method = RequestMethod.GET, produces = "application/json")
	public Map<String, String> convertEnUsToEnGb(@RequestParam Set<String> words) {
		return dialectConversionService.getAvailableEnUsToEnGbConversions(words);
	}

	@ApiOperation(value = "Add a pair of words to the EN-US to EN-GB dialect map.",
			notes = "The word pair is inserted in the list maintaining alphabetical order. " +
					"The dialect map is reloaded automatically after the list is updated.")
	@RequestMapping(value = "/dialect/en-us/map/en-gb", method = RequestMethod.PUT)
	public void addEnUSToEnGbMapEntry(@RequestParam String enUsWord, @RequestParam String enGbWord) throws IOException, ServiceException {
		dialectConversionService.addWordPair(enUsWord, enGbWord);
	}

	@ApiOperation(value = "Remove a pair of words from the EN-US to EN-GB dialect map.",
			notes = "Only the EN-US word is required to find and remove the map entry. " +
					"This function uses a case insensitive search. " +
					"If the word is not found in the list the response will be 404. " +
					"The dialect map is reloaded automatically if the word is found after the map is updated.")
	@RequestMapping(value = "/dialect/en-us/map/en-gb", method = RequestMethod.DELETE)
	public ResponseEntity<NullType> deleteEnUSToEnGbMapEntry(@RequestParam String enUsWord) throws IOException, ServiceException {
		if (dialectConversionService.deleteWordPair(enUsWord)) {
			return ResponseEntity.noContent().build();
		}
		return ResponseEntity.notFound().build();
	}

	@ApiOperation(value = "Download the whole EN-US to EN-GB dialect map.",
			notes = "Visit this endpoint URL directly in your browser, loading through Swagger may not work.")
	@ResponseBody
	@RequestMapping(value = "/dialect/en-us/map/en-gb/file", method = RequestMethod.GET, produces = "application/octet-stream")
	public ResponseEntity<InputStreamResource> downloadEnUSToEnGbMap() {
		S3Object listObject = dialectConversionService.getMapObject();
		return ResponseEntity.ok()
				.contentLength(listObject.getObjectMetadata().getContentLength())
				.contentType(MediaType.APPLICATION_OCTET_STREAM)
				.header("content-disposition", "attachment; filename=\"dialect_map_en-us_to_en-gb.txt\"")
				.body(new InputStreamResource(listObject.getObjectContent()));
	}

	@ApiOperation(value = "Replace the whole EN-US to EN-GB dialect map.",
			notes = "The dialect map is reloaded automatically once the map is updated.")
	@ResponseBody
	@RequestMapping(value = "/dialect/en-us/map/en-gb/file", method = RequestMethod.POST, produces = "application/json")
	public void replaceEnUSToEnGbMap(@RequestParam("file") MultipartFile file) throws IOException, ServiceException {
		dialectConversionService.replaceMap(file);
	}

}
