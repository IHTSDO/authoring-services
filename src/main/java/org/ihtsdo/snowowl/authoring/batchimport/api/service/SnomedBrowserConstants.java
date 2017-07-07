package org.ihtsdo.snowowl.authoring.batchimport.api.service;

public interface SnomedBrowserConstants {
	
	final String SCTID_ISA = "116680003";
	final String SCTID_EN_GB = "900000000000508004";
	final String SCTID_EN_US = "900000000000509007";
	
	public static final String EN_LANGUAGE_CODE = "en";

	enum RelationshipModifier { EXISTENTIAL }
	
	enum CharacteristicType { STATED, INFERRED }
	
	enum Acceptability { ACCEPTABLE, PREFERRED }
	
	enum CaseSignificance { ENTIRE_TERM_CASE_SENSITIVE, CASE_INSENSITIVE, INITIAL_CHARACTER_CASE_INSENSITIVE }
	
	enum DescriptionType { SYNONYM, FSN }
	
}
