package org.ihtsdo.snowowl.authoring.batchimport.api.pojo.batch;

import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.otf.rest.client.snowowl.pojo.RelationshipPojo;
import org.ihtsdo.otf.rest.exception.ProcessingException;
import org.ihtsdo.snowowl.authoring.batchimport.api.service.BatchImportService;
import org.ihtsdo.snowowl.authoring.batchimport.api.service.VerhoeffCheck;

public class BatchImportGroup {

	private int groupNumber;
	private List <RelationshipPojo> relationships = new ArrayList<>();
	
	private BatchImportGroup(int groupNumber) {
		this.groupNumber = groupNumber;
	}
	
	public static BatchImportGroup parse(int groupNumber, String expression, String moduleId) throws ProcessingException {
		BatchImportGroup thisGroup = new BatchImportGroup(groupNumber);
		String[] attributes = expression.split(BatchImportExpression.ATTRIBUTE_SEPARATOR);
		int attributeNumber = 0;
		for (String thisAttribute : attributes) {
			String tmpId = "rel_" + groupNumber + "." + (attributeNumber++);
			RelationshipPojo relationship = parseAttribute(groupNumber, tmpId, thisAttribute, moduleId);
			thisGroup.relationships.add(relationship);
		}
		return thisGroup;
	}

	private static RelationshipPojo parseAttribute(int groupNum, String tmpId, String thisAttribute, String moduleId) throws ProcessingException {
		//Expected format  type=value so bomb out if we don't end up with two concepts
		String[] attributeParts = thisAttribute.split(BatchImportExpression.TYPE_SEPARATOR);
		if (attributeParts.length != 2) {
			throw new ProcessingException("Unable to detect type=value in attribute: " + thisAttribute);
		}
		//Check we have SCTIDs that pass the Verhoeff check
		boolean verhoeffOK = false;
		try {
			verhoeffOK = VerhoeffCheck.validateLastChecksumDigit(attributeParts[0]);
		} finally {
			if (!verhoeffOK) {
				throw new ProcessingException("Attribute type is not a valid SCTID: " + attributeParts[0]);
			}
		}
		verhoeffOK = false;
		try {
			verhoeffOK = VerhoeffCheck.validateLastChecksumDigit(attributeParts[1]);
		} finally {
			if (!verhoeffOK) {
				throw new ProcessingException("Attribute destination is not a valid SCTID: " + attributeParts[1]);
			}
		}
		
		return BatchImportService.createRelationship(groupNum, tmpId, null, attributeParts[0], attributeParts[1], moduleId);
	}

	public int getGroupNumber() {
		return groupNumber;
	}

	public List<RelationshipPojo> getRelationships() {
		return relationships;
	}

}
