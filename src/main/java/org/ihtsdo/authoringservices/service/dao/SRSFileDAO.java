package org.ihtsdo.authoringservices.service.dao;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.CharEncoding;
import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.rest.exception.ProcessWorkflowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class SRSFileDAO {
	private final Logger logger = LoggerFactory.getLogger(SRSFileDAO.class);

	private static final String DELTA = "Delta";
	private static final String INTERNATIONAL = "international";
	private static final String TXT = ".txt";
	private static final String FILE_TYPE_INSERT = "****";
	private static final String RELEASE_DATE_INSERT = "########";
	private static final String COUNTRY_OR_NAMSPACE ="$$$";
	private static final String UNKNOWN_EFFECTIVE_DATE = "Unpublished";
	private static final int EFFECTIVE_DATE_COLUMN = 1;
	private static final int TYPE_ID_COLUMN = 6;
	private static final String TEXT_DEFINITION_SCTID = "900000000000550004";
	private static final String ICDO_REFSET_ID = "446608001";
	private static Set<String> ACCEPTABLE_SIMPLEMAP_VALUES;
	private static final String LINE_ENDING = "\r\n";

	private static final String[] FILE_NAMES_TO_BE_EXCLUDED = {"der2_iissscRefset_ICD-9-CMEquivalenceComplexMapReferenceSet"};

	private static final String[] EXTENSION_EXCLUDED_FILES = {"der2_iisssccRefset_ICD-10ComplexMapReferenceSet","der2_sRefset_CTV3SimpleMap",
		"der2_sRefset_SNOMEDRTIDSimpleMap","der2_sRefset_ICD-OSimpleMapReferenceSet"};

	static Map<String, RefsetCombiner> refsetMap;
	static {
		refsetMap = new HashMap<>();
		refsetMap.put("Simple", new RefsetCombiner("der2_Refset_Simple****_$$$_########.txt", new String[] {
				"der2_Refset_NonHumanSimpleReferenceSet****_$$$_########.txt",
				"der2_Refset_VirtualMedicinalProductSimpleReferenceSet****_$$$_########.txt",
				"der2_Refset_VirtualTherapeuticMoietySimpleReferenceSet****_$$$_########.txt", }));

		refsetMap.put("AssociationReference", new RefsetCombiner("der2_cRefset_AssociationReference****_$$$_########.txt", new String[] {
				"der2_cRefset_ALTERNATIVEAssociationReferenceSet****_$$$_########.txt",
				"der2_cRefset_MOVEDFROMAssociationReferenceSet****_$$$_########.txt",
				"der2_cRefset_MOVEDTOAssociationReferenceSet****_$$$_########.txt",
				"der2_cRefset_POSSIBLYEQUIVALENTTOAssociationReferenceSet****_$$$_########.txt",
				"der2_cRefset_REFERSTOConceptAssociationReferenceSet****_$$$_########.txt",
				"der2_cRefset_REPLACEDBYAssociationReferenceSet****_$$$_########.txt",
				"der2_cRefset_SAMEASAssociationReferenceSet****_$$$_########.txt",
				"der2_cRefset_WASAAssociationReferenceSet****_$$$_########.txt", }));

		refsetMap.put("AttributeValue", new RefsetCombiner("der2_cRefset_AttributeValue****_$$$_########.txt", new String[] {
				"der2_cRefset_ConceptInactivationIndicatorReferenceSet****_$$$_########.txt",
				"der2_cRefset_DescriptionInactivationIndicatorReferenceSet****_$$$_########.txt", }));

		refsetMap.put("Language", new RefsetCombiner("der2_cRefset_Language****-en_$$$_########.txt", new String[] {
				"der2_cRefset_GBEnglish****-en-gb_$$$_########.txt", "der2_cRefset_USEnglish****-en-us_$$$_########.txt" }));

		refsetMap.put("DescriptionType", new RefsetCombiner("der2_ciRefset_DescriptionType****_$$$_########.txt",
				new String[] { "der2_ciRefset_DescriptionFormat****_$$$_########.txt" }));

		refsetMap.put("ExtendedMap", new RefsetCombiner("der2_iisssccRefset_ExtendedMap****_$$$_########.txt",
				new String[] { "der2_iisssccRefset_ICD-10ComplexMapReferenceSet****_$$$_########.txt" }));

		refsetMap.put("SimpleMap", new RefsetCombiner("der2_sRefset_SimpleMap****_$$$_########.txt", new String[] {
				"der2_sRefset_CTV3SimpleMap****_$$$_########.txt", "der2_sRefset_ICD-OSimpleMapReferenceSet****_$$$_########.txt",
				"der2_sRefset_SNOMEDRTIDSimpleMap****_$$$_########.txt", "der2_sRefset_GMDNSimpleMapReferenceSet****_$$$_########.txt" }));
		ACCEPTABLE_SIMPLEMAP_VALUES = new HashSet<>();
		ACCEPTABLE_SIMPLEMAP_VALUES.add(ICDO_REFSET_ID);
	}

	public File extractAndConvertExportWithRF2FileNameFormat(File archive, String releaseCenter, String releaseDate) throws ProcessWorkflowException, IOException {
		// We're going to create release files in a temp directory
		File extractDir = Files.createTempDir();
		unzipFlat(archive, extractDir);
		logger.debug("Unzipped files to {}", extractDir.getAbsolutePath());
		
		String countryNamespace = getCountryOrNamespace(extractDir);
		logger.debug("Country or namespace found from file name:{}", countryNamespace);
		if (countryNamespace == null) {
			countryNamespace = "INT";
		}

		renameDKTranslatedConceptsRefsetFile(extractDir, releaseDate);

		// Ensure all files have the correct release date
		enforceReleaseDate(extractDir, releaseDate);
		// suppress files that no longer to be released.
		suppressFilesNotRequired(FILE_NAMES_TO_BE_EXCLUDED, extractDir);
		// exclude files for extension release
		if (!INTERNATIONAL.equalsIgnoreCase(releaseCenter)){
			suppressFilesNotRequired(EXTENSION_EXCLUDED_FILES, extractDir);
		}

		// Merge the refsets into the expected files and replace any "unpublished" dates
		// with today's date
		mergeRefsets(extractDir, DELTA,countryNamespace, releaseDate);
		replaceInFiles(extractDir, UNKNOWN_EFFECTIVE_DATE, releaseDate, EFFECTIVE_DATE_COLUMN);

		// The description file is currently named sct2_Description_${extractType}-en-gb_INT_<date>.txt
		// and we need it to be sct2_Description_${extractType}-en_INT_<date>.txt
		File descriptionFileWrongName = new File(extractDir, "sct2_Description_Delta-en-gb_INT_" + releaseDate + TXT);
		File descriptionFileRightName = new File(extractDir, "sct2_Description_Delta-en_INT_" + releaseDate + TXT);
		if (descriptionFileWrongName.exists()) {
			descriptionFileWrongName.renameTo(descriptionFileRightName);
		} else {
			logger.warn("Was not able to find {} to correct the name", descriptionFileWrongName);
		}

		// Check if there is text definition exported or not. If We don't have a Text Definition file, so create that by extracting rows with TypeId 900000000000550004
		// from sct2_Description_Delta-en_INT_<date>.txt to form sct2_TextDefinition_Delta-en_INT_<date>.txt
		File description = new File(extractDir, "sct2_Description_Delta-en_INT_" + releaseDate + TXT);
		File definition = new File(extractDir, "sct2_TextDefinition_Delta-en_INT_" + releaseDate + TXT);
		if (!definition.exists()) {
			logger.info("No text definition file is being exported therefore it will try to extract data from the description file for type id 900000000000550004" );
			createSubsetFile(description, definition, TYPE_ID_COLUMN, TEXT_DEFINITION_SCTID, true, false);
		}
		return extractDir;
	}
	
	private String getCountryOrNamespace(File extractDir) {
		
		String[] rf2Filenames = extractDir.list(new FilenameFilter() {
			
			@Override
			public boolean accept(File dir, String name) {
				if (name.startsWith("sct2_Concept_") && name.endsWith(TXT)) {
					return true;
				}
				return false;
			}
		});
		
		if (rf2Filenames.length == 1) {
			String[] splits = rf2Filenames[0].split("_");
			if (splits.length == 5) {
				return splits[3];
			}
		}
		return null;
	}


	/**
	 * rename dk and se translated concepts file.
	 *der2_Refset_554831000005107Delta_DK1000005_20160926.txt 
	 */
	private void renameDKTranslatedConceptsRefsetFile(File extractDir, String releaseDate) {
		File wrongName = new File(extractDir, "der2_Refset_554831000005107Delta_DK1000005_" + releaseDate + TXT);
		File updatedName = new File(extractDir, "der2_Refset_DanishTranslatedConceptsSimpleDelta_DK1000005_" + releaseDate + TXT);
		if (wrongName.exists()) {
			wrongName.renameTo(updatedName);
			logger.warn("found wrong file name: {} and updated it to : {}", wrongName, updatedName);
		} 
	
	}

	private void suppressFilesNotRequired(String[] filenamesToBeExcluded, File extractDir) {
		
		List<String> filesToBeRemoved = new ArrayList<>();
		for (final String fileName : filenamesToBeExcluded) {
			String[] filesFound = extractDir.list(new FilenameFilter() {
				
				@Override
				public boolean accept(File dir, String name) {
					if (name.startsWith(fileName)) {
						return true;
					}
					return false;
				}
			});
			filesToBeRemoved.addAll(Arrays.asList(filesFound));
		}
		for (String fileName : filesToBeRemoved) {
			File file = new File(extractDir,fileName);
			if (file.exists()) {
				logger.debug("File is excluded: {}", file.getName());
				file.delete();
			}
		}
	}

	private void enforceReleaseDate(File extractDir, String enforcedReleaseDate) throws ProcessWorkflowException {
		//Loop through all the files in the directory and change the release date if required
		for (File thisFile : extractDir.listFiles()) {
			if (thisFile.isFile()) {
				String thisReleaseDate = findDateInString(thisFile.getName(), true);
				if (thisReleaseDate != null && !thisReleaseDate.equals(enforcedReleaseDate)) {
					logger.debug("Modifying releaseDate in {} to {}", thisFile.getName(), enforcedReleaseDate);
					renameFile(extractDir, thisFile, thisReleaseDate, enforcedReleaseDate);
				}
			}
		}
	}

	private void mergeRefsets(File extractDir, String fileType, String countryNamespace,String releaseDate) throws IOException {
		// Loop through our map of refsets required, and see what contributing files we can match
		for (Map.Entry<String, RefsetCombiner> refset : refsetMap.entrySet()) {

			RefsetCombiner rc = refset.getValue();
			String combinedRefset = getFilename(rc.targetFilePattern, fileType, countryNamespace, releaseDate);
			// Now can we find any of the contributing files to add to that file?
			boolean isFirstContributor = true;
			for (String contributorPattern : rc.sourceFilePatterns) {
				String contributorFilename = getFilename(contributorPattern, fileType, countryNamespace, releaseDate);
				File contributorFile = new File(extractDir, contributorFilename);
				File combinedRefsetFile = new File(extractDir, combinedRefset);
				if (contributorFile.exists()) {
					List<String> fileLines = FileUtils.readLines(contributorFile, StandardCharsets.UTF_8);
					// Don't need the header line for any subsequent files
					if (!isFirstContributor) {
						fileLines.remove(0);
					}
					boolean append = !isFirstContributor;
					FileUtils.writeLines(combinedRefsetFile, CharEncoding.UTF_8, fileLines, LINE_ENDING, append);
					isFirstContributor = false;
					// Now we can delete the contributor so it doesn't get uploaded as another input file
					contributorFile.delete();
				}
			}
			if (isFirstContributor) {
				logger.warn("Failed to find any files to contribute to {}", combinedRefset);
			} else {
				logger.debug("Created combined refset {}", combinedRefset);
			}
		}
	}

	private String getFilename(String filenamePattern, String fileType, String countryNamespace,String date) {
		return filenamePattern.replace(FILE_TYPE_INSERT, fileType).replace(COUNTRY_OR_NAMSPACE,countryNamespace).replace(RELEASE_DATE_INSERT, date);
	}

	private void renameFile(File parentDir, File thisFile, String find, String replace) {
		if (thisFile.exists() && !thisFile.isDirectory()) {
			String currentName = thisFile.getName();
			String newName = currentName.replace(find, replace);
			if (!newName.equals(currentName)) {
				File newFile = new File(parentDir, newName);
				thisFile.renameTo(newFile);
			}
		}
	}

	/**
	 * @param targetDirectory
	 * @param find
	 * @param replace
	 * @param columnNum
	 *            searched for term must match in this column
	 * @throws IOException
	 */
	protected void replaceInFiles(File targetDirectory, String find, String replace, int columnNum) throws IOException {
		Assert.isTrue(targetDirectory.isDirectory(), targetDirectory.getAbsolutePath()
				+ " must be a directory in order to replace text from " + find + " to " + replace);

		logger.info("Replacing {} with {} in target directory {}", find, replace, targetDirectory);
		for (File thisFile : targetDirectory.listFiles()) {
			if (thisFile.exists() && !thisFile.isDirectory()) {
				List<String> oldLines = FileUtils.readLines(thisFile, StandardCharsets.UTF_8);
				List<String> newLines = new ArrayList<String>();
				for (String thisLine : oldLines) {
					String[] columns = thisLine.split("\t");
					if (columns.length > columnNum && columns[columnNum].equals(find)) {
						thisLine = thisLine.replaceFirst(find, replace); // Would be more generic to rebuild from columns
					}
					newLines.add(thisLine);
				}
				FileUtils.writeLines(thisFile, CharEncoding.UTF_8, newLines, LINE_ENDING);
			}
		}
	}

	/*
	 * Creates a file containing all the rows which have "mustMatch" in columnNum. Plus the header row.
	 */
	protected void createSubsetFile(File source, File target, int columnNum, String mustMatch, boolean removeFromOriginal,
			boolean removeId)
			throws IOException {
		if (source.exists() && !source.isDirectory()) {
			logger.debug("Creating {} as a subset of {} and {} rows in original.", target, source, (removeFromOriginal ? "removing"
					: "leaving"));
			List<String> allLines = FileUtils.readLines(source, StandardCharsets.UTF_8);
			List<String> newLines = new ArrayList<>();
			List<String> remainingLines = new ArrayList<>();
			int lineCount = 1;
			for (String thisLine : allLines) {
				String[] columns = thisLine.split("\t");
				if (lineCount == 1 || (columns.length > columnNum && columns[columnNum].equals(mustMatch))) {
					// Are we wiping out the Id (column index 0) before writing?
					if (removeId && lineCount != 1) {
						columns[0] = "";
						String lineWithIDRemoved = StringUtils.join(columns, "\t");
						newLines.add(lineWithIDRemoved);
					} else {
						newLines.add(thisLine);
					}
					if (lineCount == 1) {
						remainingLines.add(thisLine);
					}
				} else {
					remainingLines.add(thisLine);
				}
				lineCount++;
			}
			FileUtils.writeLines(target, CharEncoding.UTF_8, newLines, LINE_ENDING);
			if (removeFromOriginal) {
				FileUtils.writeLines(source, CharEncoding.UTF_8, remainingLines, LINE_ENDING);
			}
		} else {
			logger.warn("Did not find file {} needed to create subset {}", source, target);
		}
	}

	public String findDateInString(String str, boolean optional) throws ProcessWorkflowException {
		Matcher dateMatcher = Pattern.compile("_(\\d{8})").matcher(str);
		if (dateMatcher.find()) {
			return dateMatcher.group(1);
		} else {
			if (optional) {
				logger.warn("Did not find a date in: {} ", str);
			} else {
				throw new ProcessWorkflowException("Unable to determine date from " + str);
			}
		}
		return null;
	}

	public void unzipFlat(File archive, File targetDir) throws ProcessWorkflowException, IOException {

		if (!targetDir.exists() || !targetDir.isDirectory()) {
			throw new ProcessWorkflowException(targetDir + " is not a viable directory in which to extract archive");
		}

		try (ZipInputStream zis = new ZipInputStream(new FileInputStream(archive))) {
			ZipEntry ze = zis.getNextEntry();
			while (ze != null) {
				if (!ze.isDirectory()) {
					Path p = Paths.get(ze.getName());
					String extractedFileName = p.getFileName().toString();
					File extractedFile = new File(targetDir, extractedFileName);
					try (OutputStream out = new FileOutputStream(extractedFile)) {
						IOUtils.copy(zis, out);
						IOUtils.closeQuietly(out);
					}
				}
				ze = zis.getNextEntry();
			}
		}
	}
}
