package org.ihtsdo.snowowl.authoring.single.api.service;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import org.ihtsdo.otf.dao.s3.S3ClientImpl;
import org.ihtsdo.otf.spellcheck.service.SpellCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SpellingListsService {

	private final S3ClientImpl s3Client;
	private final String bucket;
	private final String path;
	private final SpellCheckService spellCheckService;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public SpellingListsService(
			@Value("${aws.key}") String accessKey,
			@Value("${aws.secretKey}") String secretKey,
			@Value("${aws.s3.spell-check.bucket}") String bucket,
			@Value("${aws.s3.spell-check.path}") String path) throws ServiceException, IOException {
		this.s3Client = new S3ClientImpl(new BasicAWSCredentials(accessKey, secretKey));
		this.bucket = bucket;
		this.path = path;
		this.spellCheckService = new SpellCheckService();
	}

	@PostConstruct
	public void loadList() throws ServiceException {
		try (S3ObjectInputStream inputStream = getListObject().getObjectContent()) {
			synchronized (spellCheckService) {
				doLoadList(inputStream);
			}
		} catch (IOException e) {
			throw new ServiceException("Failed to load spelling list from S3.", e);
		}
	}

	private void doLoadList(InputStream inputStream) throws IOException {
		logger.info("Load spelling list");
		spellCheckService.clearIndex();
		spellCheckService.loadDictionary(new InputStreamReader(inputStream));
	}

	public Map<String, List<String>> checkWordsReturnErrorSuggestions(Set<String> words) {
		synchronized (spellCheckService) {
			return spellCheckService.checkWordsReturnErrorSuggestions(words);
		}
	}

	public S3Object getListObject() {
		return s3Client.getObject(bucket, path);
	}

	public void replaceList(MultipartFile file) throws IOException, ServiceException {
		ObjectMetadata objectMetadata = new ObjectMetadata();
		objectMetadata.setContentLength(file.getSize());
		try (InputStream inputStream = file.getInputStream()) {
			s3Client.putObject(bucket, path, inputStream, objectMetadata);
			loadList();
		}
	}

	public void addWord(String newWord) throws IOException {
		logger.info("Adding word to spelling list '{}'", newWord);
		try (S3ObjectInputStream inputStream = getListObject().getObjectContent()) {
			File modifiedList = Files.createTempFile("temp-spelling-list", "txt").toFile();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
				try (BufferedWriter writer = new BufferedWriter(new FileWriter(modifiedList))) {
					boolean inserted = false;
					String listWord;
					while ((listWord = reader.readLine()) != null) {
						if (!inserted && newWord.compareToIgnoreCase(listWord) < 0) {
							logger.info("Inserting {} before {}", newWord, listWord);
							writer.write(newWord);
							writer.newLine();
							inserted = true;
						}
						writer.write(listWord);
						writer.newLine();
					}
				}
				s3Client.putObject(bucket, path, modifiedList);
				doLoadList(new FileInputStream(modifiedList));
			} finally {
				modifiedList.delete();
			}
		}
	}

	public boolean deleteWord(String word) throws IOException, ServiceException {
		try (S3ObjectInputStream inputStream = getListObject().getObjectContent()) {
			File modifiedList = Files.createTempFile("temp-spelling-list", "txt").toFile();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
				boolean wordFound = false;
				try (BufferedWriter writer = new BufferedWriter(new FileWriter(modifiedList))) {
					String line;
					while ((line = reader.readLine()) != null) {
						if (!wordFound && line.equalsIgnoreCase(word)) {
							// Don't write this word to the new file
							wordFound = true;
						} else {
							writer.write(line);
							writer.newLine();
						}
					}
				}
				if (wordFound) {
					logger.info("Removing word from spelling list '{}'", word);
					s3Client.putObject(bucket, path, modifiedList);
					doLoadList(new FileInputStream(modifiedList));
				}
				return wordFound;
			} finally {
				modifiedList.delete();
			}
		}
	}
}
