package org.ihtsdo.snowowl.authoring.single.api.service.dao;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.apache.commons.io.IOUtils;
import org.ihtsdo.otf.dao.s3.S3ClientImpl;
import org.ihtsdo.snowowl.authoring.single.api.configuration.UiStateStorageConfiguration;
import org.ihtsdo.snowowl.authoring.single.api.service.exceptions.PathNotProvidedException;
import org.json.JSONException;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;

import org.slf4j.LoggerFactory;

import static java.lang.String.format;

@Service
public final class UiStateResourceService extends AbstractResourceService {

	/**
	 * Builds the service to read/write/delete and move files from S3 storage.
	 *
	 * @param cloudResourceLoader Checks to make sure that the S3 path exists and is also
	 *                            used to get the resource.
	 */

    @Value("${ui-state.storage.useCloud}")
    private boolean useCloud;

    @Value("${aws.key}")
    private String awsKey;

    @Value("${aws.secretKey}")
    private String secretKey;

    @Value("${ui-state.storage.cloud.bucketName}")
    private String bucketName;

    @Value("${ui-state.storage.cloud.path}")
    private String path;

	public UiStateResourceService(@Autowired final UiStateStorageConfiguration uiStateStorageConfiguration,
								  @Autowired final ResourceLoader cloudResourceLoader) {
		super(uiStateStorageConfiguration, cloudResourceLoader);
	}

	@Override
	public final void write(final String path,
							final String data) throws IOException {
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
    public final void move(final String fromPath,
                           final String toPath) throws IOException {
        if (fromPath == null || toPath == null) {
            throw new PathNotProvidedException("Either the from/to path is null, both are required for the move operation to proceed.");
        }
        if (this.useCloud) {
            S3ClientImpl s3Client = new S3ClientImpl(new BasicAWSCredentials(awsKey, secretKey));
            String fromPathFull = (path != null ? path : "") + fromPath;
            ObjectListing objectListing = s3Client.listObjects(bucketName, (path != null ? path : "") + fromPath);
            for (S3ObjectSummary summary : objectListing.getObjectSummaries()) {
                String key = summary.getKey().substring(fromPathFull.length());
                resourceManager.moveResource(fromPath + key, toPath + key);
            }
        } else {
            resourceManager.moveResource(fromPath, toPath);
        }
    }
}
