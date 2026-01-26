package org.ihtsdo.authoringservices.service.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ihtsdo.authoringservices.configuration.UiStateStorageConfiguration;
import org.ihtsdo.authoringservices.service.exceptions.PathNotProvidedException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class UiStateResourceServiceTest {

	private static final UiStateResourceService uiStateResourceService = new UiStateResourceService(Mockito.mock(UiStateStorageConfiguration.class));

	private static final ObjectMapper mapper = new ObjectMapper();
	private static final ObjectNode EMPTY_OBJECT = mapper.createObjectNode();

	@Test
	public void testWriteThrowsPathNotProvidedExceptionWhenPathIsNull() {
	    assertThrows(PathNotProvidedException.class, () -> uiStateResourceService.write(null, EMPTY_OBJECT));
	}

	@Test
	public void testWriteThrowsJSONExceptionWhenDataIsNull() {
        assertThrows(IOException.class, () -> uiStateResourceService.write("", EMPTY_OBJECT));
    }

    @Test
	public void testReadThrowsPathNotProvidedExceptionWhenPathIsNull() {
        assertThrows(PathNotProvidedException.class, () -> uiStateResourceService.read((String) null));
	}

	@Test
	public void testReadThrowsFileNotFoundExceptionWhenPathIsNull() {
        assertThrows(FileNotFoundException.class, () -> uiStateResourceService.read((File) null));
    }

    @Test
	public void testDeleteThrowsPathNotProvidedExceptionWhenPathIsNull() {
        assertThrows(PathNotProvidedException.class, () -> uiStateResourceService.delete(null));
	}

    @Test
	public void testMoveThrowsPathNotProvidedExceptionWhenFromPathIsNull() {
        assertThrows(PathNotProvidedException.class, () -> uiStateResourceService.move(null, ""));
    }

	@Test
	public void testMoveThrowsPathNotProvidedExceptionWhenToPathIsNull() {
        assertThrows(PathNotProvidedException.class, () -> uiStateResourceService.move("", null));
    }
}
