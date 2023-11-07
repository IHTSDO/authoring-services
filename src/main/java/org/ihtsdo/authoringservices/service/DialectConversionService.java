package org.ihtsdo.authoringservices.service;

import com.google.common.collect.Sets;
import io.awspring.cloud.s3.ObjectMetadata;
import jakarta.annotation.PostConstruct;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.authoringservices.domain.DialectVariations;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.otf.dao.s3.S3ClientImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.*;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class DialectConversionService {

	private final S3ClientImpl s3Client;
	private final String bucket;
	private final String usToGbTermsMapPath;
	private final String usToGbSynonymsMapPath;
	private final Logger logger = LoggerFactory.getLogger(getClass());
    private Map<String, String> dialectUsToGbMap;
    private Map<String, Set<String>> dialectSynonymsUsToGbMap;
    private final boolean awsResourceEnabled;

    public DialectConversionService(
			@Value("${aws.resources.enabled}") boolean awsResourceEnabled,
			@Value("${aws.key}") String accessKey,
			@Value("${aws.secretKey}") String secretKey,
			@Value("${aws.s3.spell-check.bucket}") String bucket,
			@Value("${aws.s3.dialect.us-to-gb-map.path}") String usToGbTermsMapPath,
			@Value("${aws.s3.dialect.us-to-gb-synonyms-map.path}") String usToGbSynonymsMapPath) {
		this.s3Client = new S3ClientImpl(S3Client.builder().region(DefaultAwsRegionProviderChain.builder().build().getRegion()).build());
		this.bucket = bucket;
		this.usToGbTermsMapPath = usToGbTermsMapPath;
		this.usToGbSynonymsMapPath = usToGbSynonymsMapPath;
		this.dialectUsToGbMap = new HashMap<>();
		this.dialectSynonymsUsToGbMap = new HashMap<>();
        this.awsResourceEnabled = awsResourceEnabled;
    }

	@PostConstruct
	public void loadList() throws ServiceException {
	    if (awsResourceEnabled) {
            doLoadList(getMapObject());
            doLoadSynonymsList(getSynonymsMapObject());
        } else {
            logger.info("AWS resources disabled, not loading US-GB dialect conversion map.");
        }
	}

	private void doLoadList(InputStream objectContent) throws ServiceException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(objectContent))) {
			logger.info("Loading US to GB dialect conversion map");
			reader.readLine();// Discard header
			Map<String, String> newDialectUsToGbMap = new HashMap<>();
			String line;
			while ((line = reader.readLine()) != null) {
				String[] split = line.split("\\t");
				newDialectUsToGbMap.put(split[0], split[1]);
			}
			dialectUsToGbMap = newDialectUsToGbMap;
		} catch (IOException e) {
			throw new ServiceException("Failed to load spelling list from S3.", e);
		}
	}
	
	private void doLoadSynonymsList(InputStream objectContent) throws ServiceException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(objectContent))) {
			logger.info("Loading US to GB dialect synonyms conversion map");
			reader.readLine();// Discard header
			Map<String, Set<String>> newDialectUsToGbMap = new HashMap<>();
			String line;
			while ((line = reader.readLine()) != null) {
				String[] split = line.split("\\t");
				String[] synSplit = split[1].split("\\|");
				newDialectUsToGbMap.put(split[0], Sets.newHashSet(org.apache.commons.lang.StringUtils.stripAll(synSplit)));
			}
			dialectSynonymsUsToGbMap = newDialectUsToGbMap;
		} catch (IOException e) {
			throw new ServiceException("Failed to load spelling list from S3.", e);
		}
	}

	public Map<String, String> getAvailableEnUsToEnGbConversions(Set<String> words) {
		Map<String, String> conversions = new HashMap<>();
		for (String usWord : words) {
			if (!StringUtils.isEmpty(usWord)) {
				String gbWord = dialectUsToGbMap.get(usWord.toLowerCase());
				if (!StringUtils.isEmpty(gbWord)) {
					// Preserve first letter capitalization
					if (usWord.substring(0, 1).equals(usWord.substring(0, 1).toUpperCase())) {
						gbWord = gbWord.substring(0, 1).toUpperCase() + gbWord.substring(1);
					}
					conversions.put(usWord, gbWord);
				}
			}
		}
		return conversions;
	}
	
	public Map<String, Set<String>> getAvailableSynonymsEnUsToEnGbConversions(Set<String> words) {
		Map<String, Set<String>> conversions = new HashMap<>();
		for (String usWord : words) {
			if (!StringUtils.isEmpty(usWord)) {
				Set<String> gbWordList = dialectSynonymsUsToGbMap.get(usWord.toLowerCase());
				if (!CollectionUtils.isEmpty(gbWordList)) {
					Set<String> newGBWordList = new HashSet<>();
					for (String gbWord : gbWordList) {
						// Preserve first letter capitalization
						if (usWord.substring(0, 1).equals(usWord.substring(0, 1).toUpperCase())) {
							newGBWordList.add(gbWord.substring(0, 1).toUpperCase() + gbWord.substring(1));
						} else {
							newGBWordList.add(gbWord);
						}
					}
					conversions.put(usWord, newGBWordList);
				}
			}
		}
		return conversions;
	}
	
	public DialectVariations getAcceptableTermsAndAvailableSynonymsEnUsToEnGbConversions(Set<String> words){
		DialectVariations result = new DialectVariations();
		if (CollectionUtils.isEmpty(words)) {
			return new DialectVariations();
		}
		result.setMap(getAvailableEnUsToEnGbConversions(words));
		result.setSynonyms(getAvailableSynonymsEnUsToEnGbConversions(words));
		
		return result;
	}

	public InputStream getMapObject() {
		return s3Client.getObject(bucket, usToGbTermsMapPath);
	}
	
	public InputStream getSynonymsMapObject() {
		return s3Client.getObject(bucket, usToGbSynonymsMapPath);
	}

	public void replaceMap(MultipartFile file) throws IOException, ServiceException {
		ObjectMetadata objectMetadata = ObjectMetadata.builder().contentDisposition(String.valueOf(file.getSize())).build();
		try (InputStream inputStream = file.getInputStream()) {
			//AccessControlList acl = s3Client.getObjectAcl(bucket, usToGbTermsMapPath);
			s3Client.putObject(bucket, usToGbTermsMapPath, inputStream, objectMetadata);
			//s3Client.setObjectAcl(bucket, usToGbTermsMapPath, acl);
			loadList();
		}
	}
	
	public void replaceSynonymsMap(MultipartFile file) throws IOException, ServiceException {
		ObjectMetadata objectMetadata = ObjectMetadata.builder().contentDisposition(String.valueOf(file.getSize())).build();
		try (InputStream inputStream = file.getInputStream()) {
//			AccessControlList acl = s3Client.getObjectAcl(bucket, usToGbSynonymsMapPath);
			s3Client.putObject(bucket, usToGbSynonymsMapPath, inputStream, objectMetadata);
//			s3Client.setObjectAcl(bucket, usToGbSynonymsMapPath, acl);
			loadList();
		}
	}

	public void addWordPair(String newUsWord, String newGbWord) throws IOException, ServiceException {
		logger.info("Adding word pair to US/GB map '{}' '{}'", newUsWord, newGbWord);
		updateList((reader, writer) -> {

			// Write header
			writer.write(reader.readLine());
			writer.newLine();

			boolean inserted = false;
			String listPair;
			while ((listPair = reader.readLine()) != null) {
				String usWord = listPair.split("\\t")[0];
				if (!inserted) {
					int comparison = newUsWord.compareToIgnoreCase(usWord);
					if (comparison == 0) {
						throw new IllegalArgumentException(String.format("US Word '%s' is already present in this dialect map.", usWord));
					} else if (comparison < 0) {
							String newPair = newUsWord + "\t" + newGbWord;
						logger.info("Inserting '{}' before '{}'", newPair, listPair);
						writer.write(newPair);
						writer.newLine();
						inserted = true;
					}
				}
				writer.write(listPair);
				writer.newLine();
			}
			return true;
		});
	}
	
	public void addSynonymsWordPair(String newUsWord, String newGbWord) throws IOException, ServiceException {
		logger.info("Adding word pair to US/GB synonyms mapping '{}' '{}'", newUsWord, newGbWord);
		updateSynonymsList((reader, writer) -> {

			// Write header
			writer.write(reader.readLine());
			writer.newLine();

			boolean inserted = false;
			String listPair;
			while ((listPair = reader.readLine()) != null) {
				String usWord = listPair.split("\\t")[0];
				if (!inserted) {
					int comparison = newUsWord.compareToIgnoreCase(usWord);
					if (comparison == 0) {
						throw new IllegalArgumentException(String.format("US Word '%s' is already present in this dialect synonyms mapping.", usWord));
					} else {
						String newPair = newUsWord + "\t" + newGbWord;
						logger.info("Inserting '{}' before '{}'", newPair, listPair);
						writer.write(newPair);
						writer.newLine();
						inserted = true;
					}
				}
				writer.write(listPair);
				writer.newLine();
			}
			return true;
		});
	}

	public boolean deleteWordPair(String usWord) throws IOException, ServiceException {
		return updateList((reader, writer) -> {

			// Write header
			writer.write(reader.readLine());
			writer.newLine();

			boolean wordFound = false;
			String line;
			while ((line = reader.readLine()) != null) {
				String listUsWord = line.split("\\t")[0];
				if (!wordFound && listUsWord.equalsIgnoreCase(usWord)) {
					// Don't write this word pair to the new file
					wordFound = true;
				} else {
					writer.write(line);
					writer.newLine();
				}
			}
			if (!wordFound) {
				throw new IllegalArgumentException(String.format("Word '%s' is not in the map.", usWord));
			}
			logger.info("Removing word pair from US/GB map '{}'", line);
			return wordFound;
		});
	}
	
	public boolean deleteSynonymsWordPair(String usWord) throws IOException, ServiceException {
		return updateSynonymsList((reader, writer) -> {

			// Write header
			writer.write(reader.readLine());
			writer.newLine();

			boolean wordFound = false;
			String line;
			while ((line = reader.readLine()) != null) {
				String listUsWord = line.split("\\t")[0];
				if (!wordFound && listUsWord.equalsIgnoreCase(usWord)) {
					// Don't write this word pair to the new file
					wordFound = true;
				} else {
					writer.write(line);
					writer.newLine();
				}
			}
			if (!wordFound) {
				throw new IllegalArgumentException(String.format("Word '%s' is not in the synonyms mapping.", usWord));
			}
			logger.info("Removing word pair from US/GB synonyms mapping '{}'", line);
			return wordFound;
		});
	}

	private boolean updateList(FileModifier fileModifier) throws IOException, ServiceException {
		try (InputStream inputStream = getMapObject()) {
			File modifiedList = Files.createTempFile("us-gb-map", "txt").toFile();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
				boolean changes;
				try (BufferedWriter writer = new BufferedWriter(new FileWriter(modifiedList))) {
					changes = fileModifier.modifyFile(reader, writer);
				}
				if (changes) {
//					AccessControlList acl = s3Client.getObjectAcl(bucket, usToGbTermsMapPath);
					s3Client.putObject(bucket, usToGbTermsMapPath, modifiedList);
//					s3Client.setObjectAcl(bucket, usToGbTermsMapPath, acl);
					logger.info("Load US/GB map");
					doLoadList(new FileInputStream(modifiedList));
				}
				return changes;
			} finally {
				modifiedList.delete();
			}
		}
	}
	
	private boolean updateSynonymsList(FileModifier fileModifier) throws IOException, ServiceException {
		try (InputStream inputStream = getSynonymsMapObject()) {
			File modifiedList = Files.createTempFile("us-gb-synonyms", "txt").toFile();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
				boolean changes;
				try (BufferedWriter writer = new BufferedWriter(new FileWriter(modifiedList))) {
					changes = fileModifier.modifyFile(reader, writer);
				}
				if (changes) {
//					AccessControlList acl = s3Client.getObjectAcl(bucket, usToGbSynonymsMapPath);
					s3Client.putObject(bucket, usToGbSynonymsMapPath, modifiedList);
//					s3Client.setObjectAcl(bucket, usToGbSynonymsMapPath, acl);
					logger.info("Load US/GB synonyms");
					doLoadSynonymsList(new FileInputStream(modifiedList));
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
