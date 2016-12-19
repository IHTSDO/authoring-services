package org.ihtsdo.snowowl.authoring.single.api.batchImport.service;

import java.util.List;
import java.util.Set;

import org.ihtsdo.otf.rest.client.snowowl.pojo.RelationshipPojo;
import org.ihtsdo.otf.rest.exception.ProcessingException;
import org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo.BatchImportExpression;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.b2international.snowowl.snomed.api.domain.browser.ISnomedBrowserRelationship;

public class BatchImportServiceTest {
	
	@Autowired
	private BatchImportService service = new BatchImportService();

	@Test
	public void test() throws ProcessingException {
		String testExpression = "=== 64572001 | Disease |: { 363698007 | Finding site | = 38848004 | Duodenal structure , 116676008 | Associated morphology | = 24551003 | Arteriovenous malformation , 246454002 | Occurrence | = 255399007 | Congenital} ";
		BatchImportExpression exp = BatchImportExpression.parse(testExpression);
		Set<RelationshipPojo> relationships = service.convertExpressionToRelationships("NEW_SCTID", exp);
		//Parent + 3 defining attributes = 4 relationships
		Assert.assertTrue(relationships.size() == 4);
	}

}
