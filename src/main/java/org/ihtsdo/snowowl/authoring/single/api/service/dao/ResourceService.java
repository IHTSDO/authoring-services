package org.ihtsdo.snowowl.authoring.single.api.service.dao;

import com.amazonaws.services.s3.model.AmazonS3Exception;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Contains the full life-cycle of operations needed when
 * analysing/processing resources.
 */
public interface ResourceService {

	/**
	 * Writes the data into the resource location, which is found
	 * using the {@code path} provided.
	 *
	 * @param path To the resource which is going to have the data
	 *             inserted into.
	 * @param data Being written into the resource.
	 * @throws IOException If an error occurs while trying to write
	 *                     the {@code data} into the resource.
	 */
	void write(String path, String data) throws IOException;

	/**
	 * Reads the resource from the given path.
	 *
	 * @param path Which is the location to the resource.
	 * @return value which represents the {@code InputStream} of
	 * the resource.
	 * @throws AmazonS3Exception If an error occurs while trying to read
	 *                           the resource.
	 */
	String read(String path) throws AmazonS3Exception;

	/**
	 * Reads the resource from the file location, into memory.
	 *
	 * @param file Which contains the location of the resource.
	 * @return value which represents the {@code InputStream} of
	 * the resource.
	 * @throws AmazonS3Exception     If an error occurs while trying to read
	 *                               the resource.
	 * @throws FileNotFoundException If an error occurs while trying to
	 *                               access the file.
	 */
	String read(File file) throws AmazonS3Exception, FileNotFoundException;

	/**
	 * Deletes the resource, specified by the {@code relativePath}.
	 *
	 * @param path Where the resource resides, which is going
	 *             to be deleted.
	 * @throws IOException If an error occurs while trying to delete the
	 *                     resource.
	 */
	void delete(String path) throws IOException;

	/**
	 * Moves the resource from the current location, to the new
	 * location.
	 *
	 * @param fromPath Where the resource currently
	 *                 resides.
	 * @param toPath   The new location to move the resource
	 *                 to.
	 * @throws AmazonS3Exception If an error occurs while trying to
	 *                           move the file from the current location,
	 *                           to the new location.
	 */
	void move(String fromPath, String toPath) throws AmazonS3Exception;
}
