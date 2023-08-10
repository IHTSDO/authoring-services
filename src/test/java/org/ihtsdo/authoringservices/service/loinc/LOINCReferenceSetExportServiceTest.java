package org.ihtsdo.authoringservices.service.loinc;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.AxiomPojo;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ConceptPojo;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RelationshipPojo;
import org.ihtsdo.otf.snomedboot.domain.ConceptConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LOINCReferenceSetExportServiceTest {

	private LOINCReferenceSetExportService loincReferenceSetExportService;

	@BeforeEach
	public void setup() {
		loincReferenceSetExportService = new LOINCReferenceSetExportService();
	}

	@Test
	public void testGenerateCompositionalGrammar() {
		ConceptPojo concept = new ConceptPojo();
		AxiomPojo axiomPojo = new AxiomPojo(
				new RelationshipPojo(0, ConceptConstants.isA, "71388002", LOINCReferenceSetExportService.STATED_RELATIONSHIP)
		);
		concept.addClassAxiom(axiomPojo);
		assertEquals("71388002", loincReferenceSetExportService.generateCompositionalGrammar(concept));

		axiomPojo.add(new RelationshipPojo(0, ConceptConstants.isA, "138875005", LOINCReferenceSetExportService.STATED_RELATIONSHIP));
		assertEquals("71388002 + 138875005", loincReferenceSetExportService.generateCompositionalGrammar(concept));

		RelationshipPojo method = new RelationshipPojo(0, "260686004", "129264002", LOINCReferenceSetExportService.STATED_RELATIONSHIP);
		axiomPojo.add(method);
		assertEquals("71388002 + 138875005 :\n" +
				"\t260686004 = 129264002", loincReferenceSetExportService.generateCompositionalGrammar(concept));

		RelationshipPojo procedureSite = new RelationshipPojo(0, "405813007", "73903008", LOINCReferenceSetExportService.STATED_RELATIONSHIP);
		axiomPojo.add(procedureSite);
		assertEquals("""
                71388002 + 138875005 :
                \t260686004 = 129264002,
                \t405813007 = 73903008""", loincReferenceSetExportService.generateCompositionalGrammar(concept));

		procedureSite.setGroupId(1);
		assertEquals("""
                71388002 + 138875005 :
                \t260686004 = 129264002
                {\t405813007 = 73903008 }""", loincReferenceSetExportService.generateCompositionalGrammar(concept));

		method.setGroupId(1);
		assertEquals("""
                71388002 + 138875005 :
                {\t260686004 = 129264002,
                \t405813007 = 73903008 }""", loincReferenceSetExportService.generateCompositionalGrammar(concept));

		procedureSite.setGroupId(2);
		assertEquals("""
                71388002 + 138875005 :
                {\t260686004 = 129264002 }
                {\t405813007 = 73903008 }""", loincReferenceSetExportService.generateCompositionalGrammar(concept));

	}

}
