package org.ihtsdo.snowowl.authoring.single.api.service.dao;

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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;

import static java.lang.String.format;

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
		resourceManager.moveResource(fromPath, toPath);
	}
}
