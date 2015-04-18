package org.ihtsdo.snowowl.authoring.api.model.logical;

import org.ihtsdo.snowowl.authoring.api.model.AttributeValidationResult;
import org.ihtsdo.snowowl.authoring.api.model.AuthoringContent;
import org.ihtsdo.snowowl.authoring.api.model.AuthoringContentValidationResult;
import org.ihtsdo.snowowl.authoring.api.services.TestContentServiceImpl;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.LinkedHashMap;
import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations={"/test-context.xml"})
public class LogicalModelValidatorAttributesTest {

	@Autowired
	private LogicalModelValidator validator;

	@Autowired
	private TestContentServiceImpl testContentService;

	@Test
	public void testValidateContentSingleAttributeValueSelfValid() throws Exception {
		LogicalModel logicalModel = new LogicalModel(new AttributeRestriction("100", RangeRelationType.SELF, "123"));
		AuthoringContent content = new AuthoringContent();
		LinkedHashMap<String, String> attributeGroup = content.newAttributeGroup();
		attributeGroup.put("100", "123");

		AuthoringContentValidationResult result = validator.validate(content, logicalModel);
		List<AttributeValidationResult> attributeGroupMessages = result.getAttributeGroupsMessages().get(0);
		Assert.assertEquals(1, attributeGroupMessages.size());
		AttributeValidationResult attributeMessages = attributeGroupMessages.get(0);
		Assert.assertEquals("", attributeMessages.getDomainMessage());
		Assert.assertEquals("", attributeMessages.getValueMessage());
	}

	@Test
	public void testValidateContentSingleAttributeValueSelfInvalid() throws Exception {
		LogicalModel logicalModel = new LogicalModel(new AttributeRestriction("100", RangeRelationType.SELF, "123"));
		AuthoringContent content = new AuthoringContent();
		LinkedHashMap<String, String> attributeGroup = content.newAttributeGroup();
		attributeGroup.put("100", "1234");

		AuthoringContentValidationResult result = validator.validate(content, logicalModel);
		List<AttributeValidationResult> attributeGroupMessages = result.getAttributeGroupsMessages().get(0);
		Assert.assertEquals(1, attributeGroupMessages.size());
		AttributeValidationResult attributeMessages = attributeGroupMessages.get(0);
		Assert.assertEquals("", attributeMessages.getDomainMessage());
		Assert.assertEquals("Attribute value must be '123'.", attributeMessages.getValueMessage());
	}

	@Test
	public void testValidateContentSingleAttributeValueDescendantsValid() throws Exception {
		LogicalModel logicalModel = new LogicalModel(new AttributeRestriction("100", RangeRelationType.DESCENDANTS, "123"));
		testContentService.putDescendantIds("123", new String[]{"1234", "12345", "123456"});
		AuthoringContent content = new AuthoringContent();
		LinkedHashMap<String, String> attributeGroup = content.newAttributeGroup();
		attributeGroup.put("100", "1234");

		AuthoringContentValidationResult result = validator.validate(content, logicalModel);
		List<AttributeValidationResult> attributeGroupMessages = result.getAttributeGroupsMessages().get(0);
		Assert.assertEquals(1, attributeGroupMessages.size());
		AttributeValidationResult attributeMessages = attributeGroupMessages.get(0);
		Assert.assertEquals("", attributeMessages.getDomainMessage());
		Assert.assertEquals("", attributeMessages.getValueMessage());
	}

	@Test
	public void testValidateContentSingleAttributeValueDescendantsInvalid() throws Exception {
		LogicalModel logicalModel = new LogicalModel(new AttributeRestriction("100", RangeRelationType.DESCENDANTS, "123"));
		testContentService.putDescendantIds("123", new String[]{"1234", "12345", "123456"});
		AuthoringContent content = new AuthoringContent();
		LinkedHashMap<String, String> attributeGroup = content.newAttributeGroup();
		attributeGroup.put("100", "12444");

		AuthoringContentValidationResult result = validator.validate(content, logicalModel);
		List<AttributeValidationResult> attributeGroupMessages = result.getAttributeGroupsMessages().get(0);
		Assert.assertEquals(1, attributeGroupMessages.size());
		AttributeValidationResult attributeMessages = attributeGroupMessages.get(0);
		Assert.assertEquals("", attributeMessages.getDomainMessage());
		Assert.assertEquals("Attribute value must be a descendant of '123'.", attributeMessages.getValueMessage());
	}

	@Test
	public void testValidateContentSingleAttributeValueDescendantOrSelfValid() throws Exception {
		LogicalModel logicalModel = new LogicalModel();
		List<AttributeRestriction> attributeRestrictions = logicalModel.newAttributeGroup();
		attributeRestrictions.add(new AttributeRestriction("100", RangeRelationType.DESCENDANTS_AND_SELF, "123"));
		attributeRestrictions.add(new AttributeRestriction("200", RangeRelationType.DESCENDANTS_AND_SELF, "300"));
		testContentService.putDescendantIds("123", new String[]{"1234", "12345", "123456"});
		testContentService.putDescendantIds("300", new String[]{"301", "302"});
		AuthoringContent content = new AuthoringContent();
		LinkedHashMap<String, String> attributeGroup = content.newAttributeGroup();
		attributeGroup.put("100", "123"); // Self
		attributeGroup.put("200", "302"); // Descendant

		AuthoringContentValidationResult result = validator.validate(content, logicalModel);
		List<AttributeValidationResult> attributeGroupMessages = result.getAttributeGroupsMessages().get(0);
		Assert.assertEquals(2, attributeGroupMessages.size());
		AttributeValidationResult attributeMessages1 = attributeGroupMessages.get(0);
		Assert.assertEquals("", attributeMessages1.getDomainMessage());
		Assert.assertEquals("", attributeMessages1.getValueMessage());
		AttributeValidationResult attributeMessages2 = attributeGroupMessages.get(1);
		Assert.assertEquals("", attributeMessages2.getDomainMessage());
		Assert.assertEquals("", attributeMessages2.getValueMessage());
	}

	@Test
	public void testValidateContentSingleAttributeValueDescendantOrSelfInvalid() throws Exception {
		LogicalModel logicalModel = new LogicalModel();
		List<AttributeRestriction> attributeRestrictions = logicalModel.newAttributeGroup();
		attributeRestrictions.add(new AttributeRestriction("100", RangeRelationType.DESCENDANTS_AND_SELF, "123"));
		attributeRestrictions.add(new AttributeRestriction("200", RangeRelationType.DESCENDANTS_AND_SELF, "300"));
		testContentService.putDescendantIds("123", new String[]{"1234", "12345", "123456"});
		testContentService.putDescendantIds("300", new String[]{"301", "302"});
		AuthoringContent content = new AuthoringContent();
		LinkedHashMap<String, String> attributeGroup = content.newAttributeGroup();
		attributeGroup.put("100", "100");
		attributeGroup.put("200", "3000");

		AuthoringContentValidationResult result = validator.validate(content, logicalModel);
		List<AttributeValidationResult> attributeGroupMessages = result.getAttributeGroupsMessages().get(0);
		Assert.assertEquals(2, attributeGroupMessages.size());
		AttributeValidationResult attributeMessages1 = attributeGroupMessages.get(0);
		Assert.assertEquals("", attributeMessages1.getDomainMessage());
		Assert.assertEquals("Attribute value must be a descendant of or equal to '123'.", attributeMessages1.getValueMessage());
		AttributeValidationResult attributeMessages2 = attributeGroupMessages.get(1);
		Assert.assertEquals("", attributeMessages2.getDomainMessage());
		Assert.assertEquals("Attribute value must be a descendant of or equal to '300'.", attributeMessages2.getValueMessage());
	}

}