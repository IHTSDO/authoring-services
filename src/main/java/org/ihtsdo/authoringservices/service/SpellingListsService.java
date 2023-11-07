package org.ihtsdo.authoringservices.service;

import io.awspring.cloud.s3.ObjectMetadata;
import jakarta.annotation.PostConstruct;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.otf.dao.s3.S3ClientImpl;
import org.ihtsdo.otf.spellcheck.service.SpellCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.s3.S3Client;

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
    private final boolean awsResourceEnabled;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public SpellingListsService(
            @Value("${aws.resources.enabled}") boolean awsResourceEnabled,
			@Value("${aws.s3.spell-check.bucket}") String bucket,
			@Value("${aws.s3.spell-check.path}") String path) throws IOException {

	    this.s3Client = new S3ClientImpl(S3Client.builder().region(DefaultAwsRegionProviderChain.builder().build().getRegion()).build());
		this.bucket = bucket;
		this.path = path;
		this.spellCheckService = new SpellCheckService();
        this.awsResourceEnabled = awsResourceEnabled;
    }

	@PostConstruct
	public void loadList() throws ServiceException {
	    if (awsResourceEnabled) {
            try (InputStream inputStream = getListObject()) {
                synchronized (spellCheckService) {
                    doLoadList(inputStream);
                }
            } catch (IOException e) {
                throw new ServiceException("Failed to load spelling list from S3.", e);
            }
        } else {
	        logger.info("AWS resources disabled, not loading spelling list.");
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

	public InputStream getListObject() {
		return s3Client.getObject(bucket, path);
	}

	public void replaceList(MultipartFile file) throws IOException, ServiceException {
		ObjectMetadata objectMetadata = ObjectMetadata.builder().contentDisposition(String.valueOf(file.getSize())).build();
		try (InputStream inputStream = file.getInputStream()) {
			s3Client.putObject(bucket, path, inputStream, objectMetadata);
			loadList();
		}
	}

	public void addWord(String newWord) throws IOException {
		logger.info("Adding word to spelling list '{}'", newWord);
		updateList((reader, writer) -> {

			// Write header
			writer.write(reader.readLine());
			writer.newLine();

			boolean inserted = false;
			String listWord;
			while ((listWord = reader.readLine()) != null) {
				if (!inserted) {
					int comparison = newWord.compareToIgnoreCase(listWord);
					if (comparison == 0) {
						throw new IllegalArgumentException(String.format("Word '%s' is already in the list.", newWord));
					} else if (comparison < 0) {
						logger.info("Inserting {} before {}", newWord, listWord);
						writer.write(newWord);
						writer.newLine();
						inserted = true;
					}
				}
				writer.write(listWord);
				writer.newLine();
			}
			return true;
		});
	}

	public boolean deleteWord(String word) throws IOException, ServiceException {
		return updateList((reader, writer) -> {

			// Write header
			writer.write(reader.readLine());
			writer.newLine();

			boolean wordFound = false;
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
			if (!wordFound) {
				throw new IllegalArgumentException(String.format("Word '%s' is not in the list.", word));
			}
			logger.info("Removing word from spelling list '{}'", word);
			return wordFound;
		});
	}

	private boolean updateList(FileModifier fileModifier) throws IOException {
		try (InputStream inputStream = getListObject()) {
			File modifiedList = Files.createTempFile("temp-spelling-list", "txt").toFile();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
				boolean changes;
				try (BufferedWriter writer = new BufferedWriter(new FileWriter(modifiedList))) {
					changes = fileModifier.modifyFile(reader, writer);
				}
				if (changes) {
//					AccessControlList acl = s3Client.getObjectAcl(bucket, path);
					s3Client.putObject(bucket, path, modifiedList);
//					s3Client.setObjectAcl(bucket, path, acl);
					doLoadList(new FileInputStream(modifiedList));
				}
				return changes;
			} finally {
				modifiedList.delete();
			}
		}
	}

	private interface FileModifier {
		boolean modifyFile(BufferedReader reader, BufferedWriter writer) throws IOException;
	}
}
