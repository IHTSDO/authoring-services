package org.ihtsdo.snowowl.authoring.single.api.service.dao;

import org.ihtsdo.snowowl.authoring.single.api.configuration.UiStateStorageConfiguration;
import org.ihtsdo.snowowl.authoring.single.api.service.exceptions.PathNotProvidedException;
import org.json.JSONException;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class UiStateResourceServiceTest {

	private static final UiStateResourceService uiStateResourceService =
			new UiStateResourceService(Mockito.mock(UiStateStorageConfiguration.class),
									   Mockito.mock(ResourceLoader.class));

	@Test(expected = PathNotProvidedException.class)
	public void testWriteThrowsPathNotProvidedExceptionWhenPathIsNull() throws IOException {
		uiStateResourceService.write(null, "");
	}

	@Test(expected = JSONException.class)
	public void testWriteThrowsJSONExceptionWhenDataIsNull() throws IOException {
		uiStateResourceService.write("", null);
	}

	@Test(expected = PathNotProvidedException.class)
	public void testReadThrowsPathNotProvidedExceptionWhenPathIsNull() {
		uiStateResourceService.read((String) null);
	}

	@Test(expected = FileNotFoundException.class)
	public void testReadThrowsFileNotFoundExceptionWhenPathIsNull() throws IOException {
		uiStateResourceService.read((File) null);
	}

	@Test(expected = PathNotProvidedException.class)
	public void testDeleteThrowsPathNotProvidedExceptionWhenPathIsNull() throws IOException {
		uiStateResourceService.delete(null);
	}

	@Test(expected = PathNotProvidedException.class)
	public void testMoveThrowsPathNotProvidedExceptionWhenFromPathIsNull() {
		uiStateResourceService.move(null, "");
	}

	@Test(expected = PathNotProvidedException.class)
	public void testMoveThrowsPathNotProvidedExceptionWhenToPathIsNull() {
		uiStateResourceService.move("", null);
	}
}
