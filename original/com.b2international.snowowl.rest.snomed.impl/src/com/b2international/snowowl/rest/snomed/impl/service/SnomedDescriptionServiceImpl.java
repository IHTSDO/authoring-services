/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.impl.service;

import static com.google.common.collect.Maps.newHashMap;

import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.b2international.commons.ClassUtils;
import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.ComponentIdentifierPair;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.datastore.BranchPathUtils;
import com.b2international.snowowl.rest.domain.ComponentCategory;
import com.b2international.snowowl.rest.domain.IComponentRef;
import com.b2international.snowowl.rest.impl.domain.InternalComponentRef;
import com.b2international.snowowl.rest.snomed.domain.Acceptability;
import com.b2international.snowowl.rest.snomed.domain.CaseSignificance;
import com.b2international.snowowl.rest.snomed.domain.ISnomedDescription;
import com.b2international.snowowl.rest.snomed.domain.ISnomedDescriptionInput;
import com.b2international.snowowl.rest.snomed.domain.ISnomedDescriptionUpdate;
import com.b2international.snowowl.rest.snomed.exception.PreferredTermNotFoundException;
import com.b2international.snowowl.rest.snomed.service.ISnomedDescriptionService;
import com.b2international.snowowl.snomed.Description;
import com.b2international.snowowl.snomed.SnomedConstants.Concepts;
import com.b2international.snowowl.snomed.SnomedConstants.LanguageCodeReferenceSetIdentifierMapping;
import com.b2international.snowowl.snomed.SnomedFactory;
import com.b2international.snowowl.snomed.datastore.SnomedDatastoreActivator;
import com.b2international.snowowl.snomed.datastore.SnomedDescriptionLookupService;
import com.b2international.snowowl.snomed.datastore.SnomedEditingContext;
import com.b2international.snowowl.snomed.datastore.SnomedRefSetEditingContext;
import com.b2international.snowowl.snomed.datastore.index.SnomedDescriptionIndexEntry;
import com.b2international.snowowl.snomed.datastore.index.SnomedDescriptionIndexQueryAdapter;
import com.b2international.snowowl.snomed.datastore.index.SnomedIndexService;
import com.b2international.snowowl.snomed.datastore.index.refset.SnomedRefSetIndexEntry;
import com.b2international.snowowl.snomed.datastore.services.ISnomedComponentService;
import com.b2international.snowowl.snomed.snomedrefset.SnomedLanguageRefSetMember;
import com.b2international.snowowl.snomed.snomedrefset.SnomedRefSetType;
import com.b2international.snowowl.snomed.snomedrefset.SnomedStructuralRefSet;
import com.google.common.collect.Collections2;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.primitives.Longs;

/**
 * @author apeteri
 */
public class SnomedDescriptionServiceImpl 
	extends AbstractSnomedComponentServiceImpl<ISnomedDescriptionInput, ISnomedDescription, ISnomedDescriptionUpdate, Description> 
	implements ISnomedDescriptionService {

	private static SnomedIndexService getIndexService() {
		return ApplicationContext.getServiceForClass(SnomedIndexService.class);
	}

	private static ISnomedComponentService getSnomedComponentService() {
		return ApplicationContext.getServiceForClass(ISnomedComponentService.class);
	}

	private final SnomedDescriptionLookupService snomedDescriptionLookupService = new SnomedDescriptionLookupService();

	public SnomedDescriptionServiceImpl() {
		super(SnomedDatastoreActivator.REPOSITORY_UUID, ComponentCategory.DESCRIPTION);
	}

	private SnomedDescriptionConverter getDescriptionConverter(final IBranchPath branchPath) {
		return new SnomedDescriptionConverter(getMembershipLookupService(branchPath));
	}

	@Override
	protected boolean componentExists(final IComponentRef ref) {
		final InternalComponentRef internalRef = ClassUtils.checkAndCast(ref, InternalComponentRef.class);
		return snomedDescriptionLookupService.exists(internalRef.getBranchPath(), internalRef.getComponentId());
	}

	@Override
	protected Description convertAndRegister(final ISnomedDescriptionInput input, final SnomedEditingContext editingContext) {
		final Description description = SnomedFactory.eINSTANCE.createDescription();

		description.setId(input.getIdGenerationStrategy().getId());
		description.setActive(true);
		description.unsetEffectiveTime();
		description.setReleased(false);
		description.setModule(getModuleConcept(input, editingContext));
		description.setConcept(getConcept(input.getConceptId(), editingContext));
		description.setCaseSignificance(getConcept(input.getCaseSignificance().getConceptId(), editingContext));
		description.setType(getConcept(input.getTypeId(), editingContext));
		description.setTerm(input.getTerm());
		description.setLanguageCode(input.getLanguageCode());

		updateAcceptabilityMap(input.getAcceptability(), description, editingContext);
		return description;
	}

	@Override
	protected ISnomedDescription doRead(final IComponentRef ref) {
		final InternalComponentRef internalRef = ClassUtils.checkAndCast(ref, InternalComponentRef.class);
		final SnomedDescriptionIndexEntry descriptionIndexEntry = snomedDescriptionLookupService.getComponent(internalRef.getBranchPath(), internalRef.getComponentId());
		return getDescriptionConverter(internalRef.getBranchPath()).apply(descriptionIndexEntry);
	}

	@Override
	public List<ISnomedDescription> readConceptDescriptions(final IComponentRef conceptRef) {
		// TODO: check that concept exists?

		final InternalComponentRef internalConceptRef = ClassUtils.checkAndCast(conceptRef, InternalComponentRef.class);
		final SnomedDescriptionIndexQueryAdapter queryAdapter = SnomedDescriptionIndexQueryAdapter.findByConceptId(internalConceptRef.getComponentId());
		final Collection<SnomedDescriptionIndexEntry> descriptionIndexEntries = getIndexService().searchUnsorted(internalConceptRef.getBranchPath(), queryAdapter);
		final Collection<ISnomedDescription> transformedDescriptions = Collections2.transform(descriptionIndexEntries, getDescriptionConverter(internalConceptRef.getBranchPath()));

		return SnomedComponentOrdering.id().immutableSortedCopy(transformedDescriptions);
	}

	private Description getDescription(final String descriptionId, final SnomedEditingContext editingContext) {
		return snomedDescriptionLookupService.getComponent(descriptionId, editingContext.getTransaction());
	}

	@Override
	protected void doUpdate(final IComponentRef ref, final ISnomedDescriptionUpdate update, final SnomedEditingContext editingContext) {
		final Description description = getDescription(ref.getComponentId(), editingContext);

		boolean changed = false;
		changed |= updateModule(update.getModuleId(), description, editingContext);
		changed |= updateStatus(update.isActive(), description, editingContext);
		changed |= updateCaseSignificance(update.getCaseSignificance(), description, editingContext);

		// XXX: acceptability changes do not push the effective time forward on the description 
		updateAcceptabilityMap(update.getAcceptability(), description, editingContext);

		if (changed) {
			description.unsetEffectiveTime();
		}
	}

	private boolean updateCaseSignificance(final CaseSignificance newCaseSignificance, final Description description, final SnomedEditingContext editingContext) {
		if (null == newCaseSignificance) {
			return false;
		}

		final String existingCaseSignificanceId = description.getCaseSignificance().getId();
		final String newCaseSignificanceId = newCaseSignificance.getConceptId();
		if (!existingCaseSignificanceId.equals(newCaseSignificanceId)) {
			description.setCaseSignificance(getConcept(newCaseSignificanceId, editingContext));
			return true;
		} else {
			return false;
		}
	}

	private void updateAcceptabilityMap(final Map<String, Acceptability> newAcceptabilityMap, final Description description, final SnomedEditingContext editingContext) {
		if (null == newAcceptabilityMap) {
			return;
		}

		final Map<String, Acceptability> languageMembersToCreate = newHashMap(newAcceptabilityMap);
		final List<SnomedLanguageRefSetMember> languageMembers = ImmutableList.copyOf(description.getLanguageRefSetMembers());
		for (final SnomedLanguageRefSetMember languageMember : languageMembers) {
			if (!languageMember.isActive()) {
				continue;
			}

			final String languageRefSetId = languageMember.getRefSetIdentifierId();
			final Acceptability currentAcceptability = Acceptability.getByConceptId(languageMember.getAcceptabilityId());
			final Acceptability newAcceptability = newAcceptabilityMap.get(languageRefSetId);

			if (!currentAcceptability.equals(newAcceptability)) {
				removeOrDeactivate(languageMember);
			} else {
				languageMembersToCreate.remove(languageRefSetId);
			}
		}

		for (final Entry<String, Acceptability> languageMemberEntry : languageMembersToCreate.entrySet()) {
			addLanguageMember(description, editingContext, languageMemberEntry.getKey(), languageMemberEntry.getValue());
		}

		final IBranchPath branchPath = BranchPathUtils.createPath(editingContext.getTransaction());

		for (final Entry<String, Acceptability> languageMemberEntry : languageMembersToCreate.entrySet()) {
			if (Acceptability.PREFERRED.equals(languageMemberEntry.getValue())) {
				final Set<String> synonymAndDescendantIds = getSnomedComponentService().getSynonymAndDescendantIds(branchPath);
				if (synonymAndDescendantIds.contains(description.getType().getId())) {
					updateOtherPreferredDescriptions(description.getConcept().getDescriptions(), description, languageMemberEntry.getKey(), 
							synonymAndDescendantIds, editingContext);
				}
			}
		}
	}

	// Partially taken from WidgetBeanUpdater
	private void addLanguageMember(final Description description, final SnomedEditingContext editingContext, final String languageRefSetId, 
			final Acceptability acceptability) {

		final SnomedRefSetEditingContext refSetEditingContext = editingContext.getRefSetEditingContext();
		final SnomedStructuralRefSet languageRefSet = getStructuralRefSet(languageRefSetId, refSetEditingContext.getTransaction());
		final ComponentIdentifierPair<String> acceptibilityPair = SnomedRefSetEditingContext.createConceptTypePair(acceptability.getConceptId());
		final ComponentIdentifierPair<String> referencedComponentPair = SnomedRefSetEditingContext.createDescriptionTypePair(description.getId());
		final SnomedLanguageRefSetMember newMember = refSetEditingContext.createLanguageRefSetMember(referencedComponentPair, acceptibilityPair, description.getModule().getId(), languageRefSet);

		description.getLanguageRefSetMembers().add(newMember);
	}

	private void updateOtherPreferredDescriptions(final List<Description> descriptions, final Description preferredDescription, final String languageRefSetId, 
			final Set<String> synonymAndDescendantIds, final SnomedEditingContext editingContext) {

		for (final Description description : descriptions) {
			if (!description.isActive() || description.equals(preferredDescription)) {
				continue;
			}

			if (!synonymAndDescendantIds.contains(description.getType().getId())) {
				continue;
			}

			for (final SnomedLanguageRefSetMember languageMember : description.getLanguageRefSetMembers()) {
				if (!languageMember.isActive()) {
					continue;
				}

				if (!languageMember.getRefSetIdentifierId().equals(languageRefSetId)) {
					continue;
				}

				if (languageMember.getAcceptabilityId().equals(Concepts.REFSET_DESCRIPTION_ACCEPTABILITY_PREFERRED)) {
					removeOrDeactivate(languageMember);
					addLanguageMember(description, editingContext, languageRefSetId, Acceptability.ACCEPTABLE);
					break;
				}
			}
		}
	}

	@Override
	protected void doDelete(final IComponentRef ref, final SnomedEditingContext editingContext) {
		final Description description = getDescription(ref.getComponentId(), editingContext);
		editingContext.delete(description);
	}

	@Override
	public ISnomedDescription getPreferredTerm(final IComponentRef conceptRef, final Enumeration<Locale> locales) {

		final InternalComponentRef internalRef = ClassUtils.checkAndCast(conceptRef, InternalComponentRef.class);
		internalRef.checkStorageExists();

		final IBranchPath branchPath = internalRef.getBranchPath();
		final ImmutableBiMap<Locale, String> languageIdMap = createLanguageIdMap(locales, branchPath);
		final Multimap<Locale, ISnomedDescription> descriptionsByLocale = HashMultimap.create();
		final List<ISnomedDescription> descriptions = readConceptDescriptions(conceptRef);
		final Set<String> synonymAndDescendantIds = getSnomedComponentService().getSynonymAndDescendantIds(branchPath);

		for (final ISnomedDescription description : descriptions) {
			if (!synonymAndDescendantIds.contains(description.getTypeId())) {
				continue;
			}

			for (final Entry<String, Acceptability> acceptabilityEntry : description.getAcceptabilityMap().entrySet()) {
				final String languageRefSetId = acceptabilityEntry.getKey();
				if (Acceptability.PREFERRED.equals(acceptabilityEntry.getValue()) && languageIdMap.containsValue(languageRefSetId)) {
					descriptionsByLocale.put(languageIdMap.inverse().get(languageRefSetId), description);
				}
			}
		}

		for (final Locale locale : languageIdMap.keySet()) {
			final Collection<ISnomedDescription> matchingDescriptions = descriptionsByLocale.get(locale);
			if (!matchingDescriptions.isEmpty()) {
				return matchingDescriptions.iterator().next();
			}
		}

		throw new PreferredTermNotFoundException(conceptRef.getComponentId());
	}

	/*
	 * FIXME:
	 * - Add a warning if a locale could not be converted to a language reference set ID?
	 * - The user cannot refer to the same reference set ID via multiple locales (specifying en-GB and en-GB-x-900000000000508004 will
	 *   throw an exception)
	 * - Caching results
	 * - Different branch paths can have different available language refsets, looks like something which could be added to SnomedComponentService
	 * - Better fallback mechanism?
	 */
	private ImmutableBiMap<Locale, String> createLanguageIdMap(final Enumeration<Locale> locales, final IBranchPath branchPath) {
		final ImmutableBiMap.Builder<Locale, String> resultBuilder = ImmutableBiMap.builder();

		while (locales.hasMoreElements()) {

			final Locale locale = locales.nextElement();

			final String mappedRefSetId = LanguageCodeReferenceSetIdentifierMapping.getReferenceSetIdentifier(locale.toLanguageTag().toLowerCase());
			if (null != mappedRefSetId) {
				resultBuilder.put(locale, mappedRefSetId);
				continue;
			}

			final String extension = locale.getExtension('x');
			if (null != extension && null != Longs.tryParse(extension)) {
				final SnomedRefSetIndexEntry refSet = snomedRefSetLookupService.getComponent(branchPath, extension);
				if (SnomedRefSetType.LANGUAGE.equals(refSet.getType())) {
					resultBuilder.put(locale, extension);
				}
			}
		}

		final ImmutableBiMap<Locale, String> result = resultBuilder.build();
		return !result.isEmpty() ? result : ImmutableBiMap.of(Locale.forLanguageTag("en-US"), Concepts.REFSET_LANGUAGE_TYPE_US);
	}
}
