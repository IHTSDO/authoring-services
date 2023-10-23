package org.ihtsdo.authoringservices.service.dao;

import org.apache.commons.io.IOUtils;
import org.ihtsdo.authoringservices.configuration.UiStateStorageConfiguration;
import org.ihtsdo.authoringservices.service.exceptions.PathNotProvidedException;
import org.ihtsdo.otf.dao.s3.S3ClientImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.S3Object;
import us.monoid.json.JSONException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;

import static java.lang.String.format;

@Service
public final class UiStateResourceService extends AbstractResourceService {

    @Value("${ui-state.storage.useCloud}")
    private boolean useCloud;

    @Value("${ui-state.storage.cloud.bucketName}")
    private String bucketName;

    @Value("${ui-state.storage.cloud.path}")
    private String path;

    /**
     * Builds the service to read/write/delete and move files from S3 storage.
     */
    public UiStateResourceService(@Autowired final UiStateStorageConfiguration uiStateStorageConfiguration) {
		super(uiStateStorageConfiguration);
	}

	@Override
	public final void write(final String path, final String data) throws IOException, JSONException {
		if (path == null) {
			throw new PathNotProvidedException("Panel path is null while trying to write to the resource.");
		}
		if (data == null) {
			throw new JSONException("Data to be written to the resource is null.");
		}
		resourceManager.writeResource(path, IOUtils.toInputStream(data, StandardCharsets.UTF_8));
	}

	@Override
	public final String read(final String path) throws IOException {
		if (path == null) {
			throw new PathNotProvidedException("Panel path is null while trying to read the resource stream.");
		}
		InputStream inputStream = resourceManager.readResourceStreamOrNullIfNotExists(path);
		if (inputStream == null) {
			throw new NoSuchFileException(format("File %s does not exist in S3.", path));
		}
		return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
	}

	@Override
	public final String read(final File file) throws IOException {
		if (file == null) {
			throw new FileNotFoundException("File is null while trying to read the resource stream.");
		}
		return IOUtils.toString(resourceManager.readResourceStream(file.getPath()), StandardCharsets.UTF_8);
	}

	@Override
	public final void delete(final String path) throws IOException {
		if (path == null) {
			throw new PathNotProvidedException("Panel path is null while trying to delete the resource.");
		}
		resourceManager.deleteResource(path);
	}

	@Override
    public final void move(final String fromPath, final String toPath) throws IOException {
        if (fromPath == null || toPath == null) {
            throw new PathNotProvidedException("Either the from/to path is null, both are required for the move operation to proceed.");
        }
        if (this.useCloud) {
            S3ClientImpl s3Client = new S3ClientImpl(S3Client.builder().credentialsProvider(ProfileCredentialsProvider.create()).build());
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
