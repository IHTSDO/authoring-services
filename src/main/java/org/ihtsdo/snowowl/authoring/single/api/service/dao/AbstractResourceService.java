package org.ihtsdo.snowowl.authoring.single.api.service.dao;

import org.ihtsdo.otf.resourcemanager.ResourceConfiguration;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.springframework.core.io.ResourceLoader;

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
	 * @param resourceLoader        Used to load the resource.
	 */
	public AbstractResourceService(final ResourceConfiguration resourceConfiguration,
								   final ResourceLoader resourceLoader) {
		this.resourceManager = new ResourceManager(resourceConfiguration, resourceLoader);
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