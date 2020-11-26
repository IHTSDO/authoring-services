package org.ihtsdo.snowowl.authoring.single.api.rest;

import com.amazonaws.services.s3.model.S3Object;
import io.swagger.annotations.ApiOperation;
import org.ihtsdo.snowowl.authoring.single.api.service.exceptions.ServiceException;
import org.ihtsdo.snowowl.authoring.single.api.service.SpellingListsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.lang.model.type.NullType;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
public class SpellCheckController {

	@Autowired
	private SpellingListsService spellingListsService;

	@ApiOperation(value = "Check the spelling of one or more words, get suggestions back.",
			notes = "Check the spelling of an array of words. " +
					"Lines of text should be split into individual words before sending. A map is returned of any incorrect words with an array of correction suggestions from the latest spelling list.")
	@ResponseBody
	@RequestMapping(value = "/spelling/check", method = RequestMethod.GET, produces = "application/json")
	public Map<String, List<String>> checkWords(@RequestParam Set<String> words) {
		return spellingListsService.checkWordsReturnErrorSuggestions(words);
	}

	@ApiOperation(value = "Add a single word to the spelling list.",
			notes = "The word is inserted in the list maintaining alphabetical order. " +
					"The spell check dictionary is reloaded automatically after the list is updated.")
	@RequestMapping(value = "/spelling/words", method = RequestMethod.PUT)
	public void addWord(@RequestParam String word) throws IOException, ServiceException {
		spellingListsService.addWord(word);
	}

	@ApiOperation(value = "Remove a single word from the spelling list.",
			notes = "This function uses a case insensitive search. " +
					"If the word is not found in the list the response will be 404. " +
					"The spell check dictionary is reloaded automatically if the word is found after the list is updated.")
	@RequestMapping(value = "/spelling/words", method = RequestMethod.DELETE)
	public ResponseEntity<NullType> deleteWord(@RequestParam String word) throws IOException, ServiceException {
		if (spellingListsService.deleteWord(word)) {
			return ResponseEntity.noContent().build();
		}
		return ResponseEntity.notFound().build();
	}

	@ApiOperation(value = "Download the whole spelling list.",
			notes = "Visit this endpoint URL directly in your browser, loading through Swagger may not work.")
	@ResponseBody
	@RequestMapping(value = "/spelling/words/list", method = RequestMethod.GET, produces = "application/octet-stream")
	public ResponseEntity<InputStreamResource> getWordList() {
		S3Object listObject = spellingListsService.getListObject();
		return ResponseEntity.ok()
				.contentLength(listObject.getObjectMetadata().getContentLength())
				.contentType(MediaType.APPLICATION_OCTET_STREAM)
				.body(new InputStreamResource(listObject.getObjectContent()));
	}

	@ApiOperation(value = "Replace the whole spelling list.")
	@ResponseBody
	@RequestMapping(value = "/spelling/words/list", method = RequestMethod.POST, produces = "application/json")
	public void replaceWordList(@RequestParam("file") MultipartFile file) throws IOException, ServiceException {
		spellingListsService.replaceList(file);
	}

}
