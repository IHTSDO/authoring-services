package org.ihtsdo.snowowl.authoring.single.api.service.dao;

import com.amazonaws.services.s3.model.AmazonS3Exception;
import org.apache.commons.io.IOUtils;
import org.ihtsdo.snowowl.authoring.single.api.configuration.UiStateStorageConfiguration;
import org.ihtsdo.snowowl.authoring.single.api.service.exceptions.PathNotProvidedException;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public final class UiStateResourceService extends AbstractResourceService {

	/**
	 * Builds the service to read/write/delete and move files from S3 storage.
	 *
	 * @param cloudResourceLoader Checks to make sure that the S3 path exists and is also
	 *                            used to get the resource.
	 */
	public UiStateResourceService(@Autowired final UiStateStorageConfiguration uiStateStorageConfiguration,
			@Autowired final ResourceLoader cloudResourceLoader) {
		super(uiStateStorageConfiguration, cloudResourceLoader);
	}

	@Override
	public final void write(final String path, final String data) throws IOException {
		if (path == null) {
			throw new PathNotProvidedException("Panel path is null while trying to write to the resource.");
		}
		if (data == null) {
			throw new JSONException("Data to be written to the resource is null.");
		}
		resourceManager.writeResource(path, IOUtils.toInputStream(data, StandardCharsets.UTF_8));
	}

	@Override
	public final String read(final String path) throws AmazonS3Exception {
		if (path == null) {
			throw new PathNotProvidedException("Panel path is null while trying to read the resource stream.");
		}
		try {
			return IOUtils.toString(resourceManager.readResourceStream(path), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new AmazonS3Exception(e.getMessage(), e);
		}
	}

	@Override
	public final String read(final File file) throws AmazonS3Exception, FileNotFoundException {
		if (file == null) {
			throw new FileNotFoundException("File is null while trying to read the resource stream.");
		}
		try {
			return IOUtils.toString(resourceManager.readResourceStream(file.getPath()), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new AmazonS3Exception(e.getMessage(), e);
		}
	}

	@Override
	public final void delete(final String path) throws IOException {
		if (path == null) {
			throw new PathNotProvidedException("Panel path is null while trying to delete the resource.");
		}
		resourceManager.deleteResource(path);
	}

	@Override
	public final void move(final String fromPath, final String toPath) throws AmazonS3Exception {
		if (fromPath == null || toPath == null) {
			throw new PathNotProvidedException("Either the from/to path is null, both are required for the move operation to proceed.");
		}
		try {
			resourceManager.moveResource(fromPath, toPath);
		} catch (IOException e) {
			throw new AmazonS3Exception(e.getMessage(), e);
		}
	}
}
