package org.ihtsdo.snowowl.authoring.single.api.service.loinc;

import org.ihtsdo.otf.rest.client.snowowl.pojo.ConceptPojo;
import org.ihtsdo.otf.rest.client.snowowl.pojo.RelationshipPojo;
import org.ihtsdo.otf.snomedboot.domain.ConceptConstants;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LOINCReferenceSetExportServiceTest {

	private LOINCReferenceSetExportService loincReferenceSetExportService;

	@Before
	public void setup() {
		loincReferenceSetExportService = new LOINCReferenceSetExportService();
	}

	@Test
	public void testGenerateCompositionalGrammar() throws Exception {
		ConceptPojo concept = new ConceptPojo();
		concept.add(new RelationshipPojo(0, ConceptConstants.isA, "71388002", LOINCReferenceSetExportService.STATED_RELATIONSHIP));
		assertEquals("71388002", loincReferenceSetExportService.generateCompositionalGrammar(concept));

		concept.add(new RelationshipPojo(0, ConceptConstants.isA, "138875005", LOINCReferenceSetExportService.STATED_RELATIONSHIP));
		assertEquals("71388002 + 138875005", loincReferenceSetExportService.generateCompositionalGrammar(concept));

		RelationshipPojo method = new RelationshipPojo(0, "260686004", "129264002", LOINCReferenceSetExportService.STATED_RELATIONSHIP);
		concept.add(method);
		assertEquals("71388002 + 138875005 :\n" +
				"\t260686004 = 129264002", loincReferenceSetExportService.generateCompositionalGrammar(concept));

		RelationshipPojo procedureSite = new RelationshipPojo(0, "405813007", "73903008", LOINCReferenceSetExportService.STATED_RELATIONSHIP);
		concept.add(procedureSite);
		assertEquals("71388002 + 138875005 :\n" +
				"\t260686004 = 129264002,\n" +
				"\t405813007 = 73903008", loincReferenceSetExportService.generateCompositionalGrammar(concept));

		procedureSite.setGroupId(1);
		assertEquals("71388002 + 138875005 :\n" +
				"\t260686004 = 129264002\n" +
				"{\t405813007 = 73903008 }", loincReferenceSetExportService.generateCompositionalGrammar(concept));

		method.setGroupId(1);
		assertEquals("71388002 + 138875005 :\n" +
				"{\t260686004 = 129264002,\n" +
				"\t405813007 = 73903008 }", loincReferenceSetExportService.generateCompositionalGrammar(concept));

		procedureSite.setGroupId(2);
		assertEquals("71388002 + 138875005 :\n" +
				"{\t260686004 = 129264002 }\n" +
				"{\t405813007 = 73903008 }", loincReferenceSetExportService.generateCompositionalGrammar(concept));

	}

}
