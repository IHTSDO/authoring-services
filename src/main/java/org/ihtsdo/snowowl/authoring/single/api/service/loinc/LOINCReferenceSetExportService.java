package org.ihtsdo.snowowl.authoring.single.api.service.loinc;

import com.google.common.collect.ComparisonChain;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowOwlRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.AxiomPojo;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ConceptPojo;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.DescriptionPojo;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RelationshipPojo;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.domain.ConceptConstants;
import org.ihtsdo.otf.snomedboot.factory.ImpotentComponentFactory;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowOwlRestClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;

@Service
public class LOINCReferenceSetExportService {

	@Value("${snomed.loinc.moduleId}")
	private String loincModuleConceptId;

	@Value("${snomed.loinc.termToExpressionRefsetId}")
	private String loincRefsetId;

	@Value("${snomed.loinc.codeSystemConceptId}")
	private String loincCodeSystemConceptId;

	@Value("${snomed.loinc.originallyInLoincConceptId}")
	private String originallyInLoincConceptId;

	@Autowired
	private SnowOwlRestClientFactory snowOwlRestClientFactory;

	private static final String TAB = "\t";

	static final String STATED_RELATIONSHIP = "STATED_RELATIONSHIP";
	private static final String CORRELATION_ID_PREFIX = "Correlation ID:";
	private static final String LOINC_UNIQUE_ID_PREFIX = "LOINC Unique ID:";

	private static final String refsetHeader = "id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tmapTarget\texpression\tdefinitionStatusId\tcorrelationId\tcontentOriginId";

	private final Logger logger = LoggerFactory.getLogger(getClass());
	public static final Comparator<RelationshipPojo> RELATIONSHIP_COMPARATOR = (r1, r2) -> ComparisonChain.start()
			.compare(r1.getGroupId(), r2.getGroupId())
			.compare(Long.parseLong(r1.getType().getConceptId()), Long.parseLong(r2.getType().getConceptId()))
			.compare(Long.parseLong(r1.getTarget().getConceptId()), Long.parseLong(r2.getTarget().getConceptId()))
			.result();

	public void exportDelta(String branchPath, InputStream previousLoincRF2SnapshotFile, OutputStream outputStream) throws BusinessServiceException {
		SnowOwlRestClient terminologyServerClient = snowOwlRestClientFactory.getClient();

		Map<String, String> loincToRefsetMemberIdMap = Collections.emptyMap();
		if (previousLoincRF2SnapshotFile != null) {
			loincToRefsetMemberIdMap = loadLoincToRefsetMemberIdMap(previousLoincRF2SnapshotFile);
		}

		// Collect IDs of concepts with logical changes
		Set<String> conceptsWithLogicalChanges = getIdsOfConceptsWithLogicalChanges(branchPath, terminologyServerClient);
		logger.info("{} concepts found with logical changes for LOINC export.", conceptsWithLogicalChanges.size());

		try {
			try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
				// Write header
				writer.write(refsetHeader);
				writer.newLine();

				for (String conceptsWithLogicalChange : conceptsWithLogicalChanges) {
					ConceptPojo concept = terminologyServerClient.getConcept(branchPath, conceptsWithLogicalChange);
					String correlationId = extractIdFromTerms(concept, CORRELATION_ID_PREFIX);
					String loincUniqueId = extractIdFromTerms(concept, LOINC_UNIQUE_ID_PREFIX);
					String compositionalGrammar = generateCompositionalGrammar(concept);

					// id
					// Previously released refset member UUID (if file provided and record previously released) or new random UUID
					writer.write(loincToRefsetMemberIdMap.getOrDefault(loincUniqueId, UUID.randomUUID().toString()));
					writer.write(TAB);

					// effectiveTime
					// This will be inserted by the release service
					writer.write(TAB);

					// active
					writer.write(concept.isActive() ? "1" : "0");
					writer.write(TAB);

					// moduleId
					writer.write(concept.getModuleId());
					writer.write(TAB);

					// refsetId
					writer.write(loincRefsetId);
					writer.write(TAB);

					// referencedComponentId
					writer.write(loincCodeSystemConceptId);
					writer.write(TAB);

					// mapTarget
					writer.write(loincUniqueId);
					writer.write(TAB);

					// expression
					writer.write(compositionalGrammar.replaceAll("\n", "").replace("\t", "").replace(" ", ""));
					writer.write(TAB);

					// definitionStatusId
					writer.write(concept.getDefinitionStatus().getConceptId());
					writer.write(TAB);

					// correlationId
					writer.write(correlationId);
					writer.write(TAB);

					// contentOriginId
					writer.write(originallyInLoincConceptId);

					writer.newLine();
				}
			}
		} catch (IOException | RestClientException e) {
			throw new BusinessServiceException("Failed to create LOINC refset content.", e);
		}
	}

	public String generateCompositionalGrammar(ConceptPojo concept) {
		SortedSet<RelationshipPojo> parents = new TreeSet<>(RELATIONSHIP_COMPARATOR);
		Map<Integer, Set<RelationshipPojo>> relationshipMap = new HashMap<>();
		Set<AxiomPojo> classAxioms = concept.getClassAxioms();
		if (classAxioms != null) {
			for (AxiomPojo classAxiom : classAxioms) {
				classAxiom.getRelationships()
						.forEach(relationship -> {
							if (ConceptConstants.isA.equals(relationship.getType().getConceptId())) {
								parents.add(relationship);
							} else {
								relationshipMap.computeIfAbsent(relationship.getGroupId(), k -> new TreeSet<>(RELATIONSHIP_COMPARATOR)).add(relationship);
							}
						});

			}
		}

		StringBuilder expression = new StringBuilder();
		for (RelationshipPojo parent : parents) {
			if (expression.length() > 0) expression.append(" + ");
			expression.append(parent.getTarget().getConceptId());
		}

		if (!relationshipMap.isEmpty()) {
			expression.append(" :");
		}

		for (int groupId : relationshipMap.keySet()) {
			expression.append("\n");
			if (groupId > 0) {
				expression.append("{");
			}
			int relationshipIndex = 0;
			for (RelationshipPojo relationship : relationshipMap.get(groupId)) {
				if (relationshipIndex++ > 0) expression.append(",\n");
				expression.append("\t").append(relationship.getType().getConceptId()).append(" = ").append(relationship.getTarget().getConceptId());
			}
			if (groupId > 0) {
				expression.append(" }");
			}
		}
		return expression.toString();
	}

	private Map<String, String> loadLoincToRefsetMemberIdMap(InputStream previousLoincRF2SnapshotFile) {
		// 0	1				2		3			4			5						6			7			8					9				10
		// id	effectiveTime	active	moduleId	refsetId	referencedComponentId	mapTarget	Expression	definitionStatusId	correlationId	contentOriginId

		Map<String, String> loincToRefsetMemberIdMap = new HashMap<>();
		try {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(previousLoincRF2SnapshotFile))) {
				reader.readLine();
				String line;
				while((line = reader.readLine()) != null) {
					String[] values = line.split("\\t");
					loincToRefsetMemberIdMap.put(values[6], values[0]);
				}
			}
		} catch (IOException e) {
			logger.warn("Failed to read previous LOINC file.", e);
			// Assumed to be bad input
			throw new IllegalArgumentException("Failed to read previous LOINC file.", e);
		}
		return loincToRefsetMemberIdMap;
	}

	private String extractIdFromTerms(ConceptPojo concept, String prefix) throws BusinessServiceException {
		for (DescriptionPojo description : concept.getDescriptions()) {
			if (description.getTerm().startsWith(prefix)) {
				String id = description.getTerm().substring(prefix.length());
				if (!id.isEmpty()) {
					return id;
				}
			}
		}
		throw new BusinessServiceException("LOINC Concept " + concept.getConceptId() + " can not be exported " +
				"because there is no term with prefix " + prefix);
	}

	private Set<String> getIdsOfConceptsWithLogicalChanges(String branchPath, SnowOwlRestClient terminologyServerClient) throws BusinessServiceException {
		Set<String> conceptsWithLogicalChanges = new HashSet<>();
		// Export RF2 Delta
		File deltaExportZip = terminologyServerClient.export(new SnowOwlRestClient.ExportConfigurationBuilder()
				.setType(SnowOwlRestClient.ExportType.DELTA)
				.setBranchPath(branchPath)
				.setIncludeUnpublished(true)
				.addModuleId(loincModuleConceptId));

		try {
			// Load RF2 Zip collecting logical concept changes
			ReleaseImporter releaseImporter = new ReleaseImporter();
			try (FileInputStream rf2DeltaZipInputStream = new FileInputStream(deltaExportZip)) {
				releaseImporter.loadDeltaReleaseFiles(rf2DeltaZipInputStream, LoadingProfile.light, new ImpotentComponentFactory() {
					@Override
					public void newConceptState(String conceptId, String effectiveTime, String active, String moduleId, String definitionStatusId) {
						conceptsWithLogicalChanges.add(conceptId);
					}

					@Override
					public void newRelationshipState(String id, String effectiveTime, String active, String moduleId, String sourceId, String destinationId, String relationshipGroup, String typeId, String characteristicTypeId, String modifierId) {
						conceptsWithLogicalChanges.add(sourceId);
					}
				});
			}
			deltaExportZip.delete();
		} catch (IOException | ReleaseImportException e) {
			throw new BusinessServiceException("Failed to process the LOINC export. Export file " + deltaExportZip.getAbsoluteFile(), e);
		}

		return conceptsWithLogicalChanges;
	}

}
