package org.ihtsdo.authoringservices.service.dao;

import org.ihtsdo.otf.resourcemanager.ResourceConfiguration;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.snomed.otf.script.dao.SimpleStorageResourceLoader;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Objects;

/**
 * Abstract resource service which allows the caller to pass in
 * a class which extends {@link ResourceConfiguration} so that
 * those configuration settings are applied to the internal functions
 * of the {@link ResourceManager}.
 */
public abstract class AbstractResourceService implements ResourceService {

	protected final ResourceManager resourceManager;

	/**
	 * Creates a new {@link AbstractResourceService} which contains
	 * the relevant {@link ResourceConfiguration} settings that the
	 * {@link ResourceManager} should use.
	 *
	 * @param resourceConfiguration Which contains all the relevant
	 *                              configuration settings for the
	 *                              {@link ResourceManager} to use.
	 */
	public AbstractResourceService(final ResourceConfiguration resourceConfiguration) {
        SimpleStorageResourceLoader cloudResourceLoader = null;
        if (resourceConfiguration.isUseCloud()) {
            cloudResourceLoader = new SimpleStorageResourceLoader(S3Client.builder().credentialsProvider(ProfileCredentialsProvider.create()).build());
			cloudResourceLoader.setTaskExecutor(new SimpleAsyncTaskExecutor("cloud-resource-loader"));
        }
		this.resourceManager = new ResourceManager(resourceConfiguration, cloudResourceLoader);
	}

	@Override
	public String toString() {
		return "AbstractResourceService{" +
				"resourceManager=" + resourceManager +
				'}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		AbstractResourceService that = (AbstractResourceService) o;
		return Objects.equals(resourceManager, that.resourceManager);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(resourceManager);
	}
}
