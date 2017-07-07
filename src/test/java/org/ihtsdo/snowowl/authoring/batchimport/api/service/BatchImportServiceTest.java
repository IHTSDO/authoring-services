package org.ihtsdo.snowowl.authoring.batchimport.api.service;

import java.util.Set;

import org.ihtsdo.otf.rest.client.snowowl.pojo.RelationshipPojo;
import org.ihtsdo.otf.rest.exception.ProcessingException;
import org.ihtsdo.snowowl.authoring.batchimport.api.pojo.batch.BatchImportExpression;
import org.ihtsdo.snowowl.authoring.batchimport.api.service.BatchImportService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class BatchImportServiceTest {
	
	@Autowired
	BatchImportService service;

	@Test
	public void test() throws ProcessingException {
		String testExpression = "=== 64572001 | Disease |: { 363698007 | Finding site | = 38848004 | Duodenal structure , 116676008 | Associated morphology | = 24551003 | Arteriovenous malformation , 246454002 | Occurrence | = 255399007 | Congenital} ";
		BatchImportExpression exp = BatchImportExpression.parse(testExpression);
		Set<RelationshipPojo> rel = service.convertExpressionToRelationships("NEW_SCTID", exp);
		//Parent + 3 defining attributes = 4 relationships
		Assert.assertTrue(rel.size() == 4);
	}

}
