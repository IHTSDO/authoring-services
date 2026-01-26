package org.ihtsdo.authoringservices.service.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonElement;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.apache.commons.io.IOUtils;
import org.ihtsdo.authoringservices.configuration.UiStateStorageConfiguration;
import org.ihtsdo.authoringservices.service.exceptions.PathNotProvidedException;
import org.ihtsdo.otf.dao.s3.S3ClientImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;

@Service
public final class UiStateResourceService extends AbstractResourceService {

    @Value("${ui-state.storage.useCloud}")
    private boolean useCloud;

    @Value("${ui-state.storage.cloud.bucketName}")
    private String bucketName;

    @Value("${ui-state.storage.cloud.path}")
    private String path;

	@Autowired
	ObjectMapper objectMapper;

    /**
     * Builds the service to read/write/delete and move files from S3 storage.
     */
    public UiStateResourceService(@Autowired final UiStateStorageConfiguration uiStateStorageConfiguration) {
		super(uiStateStorageConfiguration);
	}

	@Override
	public void write(final String path, final JsonNode data) throws IOException {
		if (path == null) {
			throw new PathNotProvidedException("Panel path is null while trying to write to the resource.");
		}
		if (data == null || data.isEmpty() ) {
			throw new IOException("Data to be written to the resource is null or empty.");
		}
		resourceManager.writeResource(path, IOUtils.toInputStream(data.toPrettyString(), StandardCharsets.UTF_8));
	}

	@Override
	public JsonNode read(final String path) throws IOException {
		if (path == null) {
			throw new PathNotProvidedException("Panel path is null while trying to read the resource stream.");
		}

		InputStream inputStream = resourceManager.readResourceStreamOrNullIfNotExists(path);
		if (inputStream == null) {
			throw new NoSuchFileException(String.format("File %s does not exist in S3.", path));
		}

		try (inputStream) {
			// Let Jackson parse the stream directly into a JsonNode
			return objectMapper.readTree(inputStream);
		}
	}



	@Override
	public String read(final File file) throws IOException {
		if (file == null) {
			throw new FileNotFoundException("File is null while trying to read the resource stream.");
		}
		return IOUtils.toString(resourceManager.readResourceStream(file.getPath()), StandardCharsets.UTF_8);
	}

	@Override
	public void delete(final String path) throws IOException {
		if (path == null) {
			throw new PathNotProvidedException("Panel path is null while trying to delete the resource.");
		}
		resourceManager.deleteResource(path);
	}

	@Override
    public void move(final String fromPath, final String toPath) throws IOException {
        if (fromPath == null || toPath == null) {
            throw new PathNotProvidedException("Either the from/to path is null, both are required for the move operation to proceed.");
        }
        if (this.useCloud) {
            S3ClientImpl s3Client = new S3ClientImpl(S3Client.builder().region(DefaultAwsRegionProviderChain.builder().build().getRegion()).build());
            String fromPathFull = (path != null ? path : "") + fromPath;
			ListObjectsResponse objectListing = s3Client.listObjects(bucketName, (path != null ? path : "") + fromPath);
            for (S3Object s3Object : objectListing.contents()) {
                String key = s3Object.key().substring(fromPathFull.length());
                resourceManager.moveResource(fromPath + key, toPath + key);
            }
        } else {
            resourceManager.moveResource(fromPath, toPath);
        }
    }
}
