package org.ihtsdo.authoringservices.service;

import com.amazonaws.auth.BasicAWSCredentials;
import org.ihtsdo.otf.dao.s3.S3Client;
import org.ihtsdo.otf.dao.s3.S3ClientImpl;
import org.ihtsdo.otf.dao.s3.helper.FileHelper;
import org.ihtsdo.otf.dao.s3.helper.S3ClientHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
public class DailyBuildService {

	public static final String SEPARATOR = "/";
	public static final String ZIP_FILE_EXTENSION = ".zip";
	private static final String SNAPSHOTS_FOLDER = "SNAPSHOTS";

	private FileHelper fileHelper;

	private S3Client s3Client;

	@Value("${dailybuild.storage.cloud.path}")
	private String dailyBuildStoragePath;

	public DailyBuildService(@Value("${dailybuild.storage.cloud.bucketName}") final String bucketName,
								  @Value("${aws.key}") String accessKey,
								  @Value("${aws.secretKey}") String secretKey) {
		this.s3Client = new S3ClientImpl(new BasicAWSCredentials(accessKey, secretKey));
		this.fileHelper = new FileHelper(bucketName, s3Client, new S3ClientHelper(this.s3Client));
	}

	public String getLatestDailyBuildFileName(final String codeSystemShortName) {
		String directoryPath = (dailyBuildStoragePath.endsWith(SEPARATOR) ? dailyBuildStoragePath : dailyBuildStoragePath + SEPARATOR) + SNAPSHOTS_FOLDER + SEPARATOR + codeSystemShortName + SEPARATOR;
		List<String> paths = this.fileHelper.listFiles(directoryPath);
		List<String> fileNames = new ArrayList<>();
		for (String path : paths) {
			if (path.endsWith(ZIP_FILE_EXTENSION)) {
				if (path.contains(SEPARATOR)) {
					fileNames.add(path.substring(path.lastIndexOf(SEPARATOR)) + 1);
				} else {
					fileNames.add(path);
				}
			}
		}

		if (!CollectionUtils.isEmpty(fileNames)) {
			Comparator<String> reverseComparator = Comparator.reverseOrder();
			fileNames.sort(reverseComparator);
			return fileNames.get(0);
		}

		return null;
	}

	public InputStream downloadDailyBuildPackage(final String codeSystemShortName, final String filename) {
		String directoryPath = (dailyBuildStoragePath.endsWith(SEPARATOR) ? dailyBuildStoragePath : dailyBuildStoragePath + SEPARATOR) + SNAPSHOTS_FOLDER + SEPARATOR + codeSystemShortName + SEPARATOR;
		return this.fileHelper.getFileStream(directoryPath + filename);
	}
}
