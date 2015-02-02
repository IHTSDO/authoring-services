/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowlmod.rest.snomed.impl.service;

import static com.b2international.commons.pcj.LongSets.toStringSet;
import static com.b2international.snowowl.snomed.datastore.browser.SnomedIndexBrowserConstants.ROOT_ID;

import java.util.Collection;
import java.util.List;

import javax.annotation.Resource;

import com.b2international.snowowlmod.rest.snomed.impl.domain.SnomedConceptList;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortField.Type;
import org.apache.lucene.util.BytesRef;

import com.b2international.commons.ClassUtils;
import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.core.api.index.CommonIndexConstants;
import com.b2international.snowowl.datastore.index.IndexQueryBuilder;
import com.b2international.snowowl.datastore.index.IndexUtils;
import com.b2international.snowowl.rest.domain.ComponentCategory;
import com.b2international.snowowl.rest.domain.IComponentList;
import com.b2international.snowowl.rest.domain.IComponentRef;
import com.b2international.snowowl.rest.domain.IStorageRef;
import com.b2international.snowowl.rest.exception.ComponentNotFoundException;
import com.b2international.snowowl.rest.impl.domain.InternalComponentRef;
import com.b2international.snowowl.rest.impl.domain.InternalStorageRef;
import com.b2international.snowowl.rest.snomed.domain.ISnomedConcept;
import com.b2international.snowowl.rest.snomed.service.ISnomedConceptService;
import com.b2international.snowowl.rest.snomed.service.ISnomedTerminologyBrowserService;
import com.b2international.snowowl.snomed.datastore.SnomedConceptIndexEntry;
import com.b2international.snowowl.snomed.datastore.SnomedTerminologyBrowser;
import com.b2international.snowowl.snomed.datastore.browser.SnomedIndexBrowserConstants;
import com.b2international.snowowl.snomed.datastore.index.SnomedConceptIndexQueryAdapter;
import com.b2international.snowowl.snomed.datastore.index.SnomedIndexService;
import com.b2international.snowowl.snomed.datastore.services.SnomedBranchRefSetMembershipLookupService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * @author apeteri
 */
public class SnomedTerminologyBrowserServiceImpl implements ISnomedTerminologyBrowserService {

	private ISnomedConceptService conceptService;

	@Resource
	public void setConceptService(final ISnomedConceptService conceptService) {
		this.conceptService = conceptService;
	}

	private static SnomedIndexService getIndexService() {
		return ApplicationContext.getServiceForClass(SnomedIndexService.class);
	}

	private static SnomedTerminologyBrowser getTerminologyBrowser() {
		return ApplicationContext.getServiceForClass(SnomedTerminologyBrowser.class);
	}

	private static final class SortedTerminologyConceptAdapter extends SnomedConceptIndexQueryAdapter {

		private static final long serialVersionUID = 1L;

		public static final int SEARCH_ROOTS = 1 << 7;
		public static final int SEARCH_PARENT = 1 << 8;
		public static final int SEARCH_ANCESTOR = 1 << 9;

		public SortedTerminologyConceptAdapter(final String conceptId, final int searchFlags, final String[] conceptIds) {
			super(conceptId, searchFlags | SEARCH_ACTIVE_CONCEPTS, conceptIds);
		}

		@Override
		protected IndexQueryBuilder createIndexQueryBuilder() {
			final BytesRef conceptIdBytesRef = IndexUtils.longToPrefixCoded(searchString);
			return super.createIndexQueryBuilder()
					.requireExactTermIf(allFlagsSet(SEARCH_ROOTS), SnomedIndexBrowserConstants.CONCEPT_PARENT, IndexUtils.longToPrefixCoded(ROOT_ID))
					.require(new IndexQueryBuilder()
						.matchExactTermIf(allFlagsSet(SEARCH_PARENT), SnomedIndexBrowserConstants.CONCEPT_PARENT, conceptIdBytesRef)
						.matchExactTermIf(allFlagsSet(SEARCH_ANCESTOR), SnomedIndexBrowserConstants.CONCEPT_ANCESTOR, conceptIdBytesRef)
					);
		}

		@Override
		public Sort createSort() {
			return new Sort(new SortField(CommonIndexConstants.COMPONENT_ID, Type.LONG));
		}
	}

	@Override
	public List<ISnomedConcept> getRootNodes(final IStorageRef ref) {
		final InternalStorageRef internalRef = ClassUtils.checkAndCast(ref, InternalStorageRef.class);
		internalRef.checkStorageExists();

		final IBranchPath branchPath = internalRef.getBranchPath();
		final SortedTerminologyConceptAdapter queryAdapter = new SortedTerminologyConceptAdapter(null, SortedTerminologyConceptAdapter.SEARCH_ROOTS, null);
		final List<SnomedConceptIndexEntry> entries = getIndexService().search(branchPath, queryAdapter);
		return convertEntries(branchPath, entries);
	}

	@Override
	public ISnomedConcept getNode(final IComponentRef nodeRef) {
		return conceptService.read(nodeRef);
	}

	@Override
	public IComponentList<ISnomedConcept> getDescendants(final IComponentRef nodeRef, final boolean direct, final int offset, final int limit) {

		final InternalComponentRef internalRef = ClassUtils.checkAndCast(nodeRef, InternalComponentRef.class);
		internalRef.checkStorageExists();

		final IBranchPath branchPath = internalRef.getBranchPath();
		final String componentId = nodeRef.getComponentId();
		checkConceptExists(branchPath, componentId);

		final int flags = direct ? SortedTerminologyConceptAdapter.SEARCH_PARENT : SortedTerminologyConceptAdapter.SEARCH_PARENT | SortedTerminologyConceptAdapter.SEARCH_ANCESTOR;
		final SortedTerminologyConceptAdapter queryAdapter = new SortedTerminologyConceptAdapter(componentId, flags, null);
		return toComponentList(offset, limit, branchPath, queryAdapter);
	}

	@Override
	public IComponentList<ISnomedConcept> getAncestors(final IComponentRef nodeRef, final boolean direct, final int offset, final int limit) {

		final InternalComponentRef internalRef = ClassUtils.checkAndCast(nodeRef, InternalComponentRef.class);
		internalRef.checkStorageExists();

		final IBranchPath branchPath = internalRef.getBranchPath();
		final String componentId = nodeRef.getComponentId();
		checkConceptExists(branchPath, componentId);

		final Collection<String> ancestorIds;

		if (direct) {
			ancestorIds = getTerminologyBrowser().getSuperTypeIds(branchPath, internalRef.getComponentId()); 
		} else {
			ancestorIds = toStringSet(getTerminologyBrowser().getAllSuperTypeIds(branchPath, Long.valueOf(internalRef.getComponentId())));
		}

		if (ancestorIds.isEmpty()) {
			return emptyComponentList();
		}

		final String[] ancestorIdArray = ancestorIds.toArray(new String[ancestorIds.size()]);
		final SortedTerminologyConceptAdapter queryAdapter = new SortedTerminologyConceptAdapter(null, SortedTerminologyConceptAdapter.SEARCH_ACTIVE_CONCEPTS, ancestorIdArray);
		return toComponentList(offset, limit, branchPath, queryAdapter);
	}

	private void checkConceptExists(final IBranchPath branchPath, final String componentId) {
		if (!getTerminologyBrowser().exists(branchPath, componentId)) {
			throw new ComponentNotFoundException(ComponentCategory.CONCEPT, componentId);
		}
	}

	private IComponentList<ISnomedConcept> toComponentList(final int offset, final int limit, final IBranchPath branchPath, final SortedTerminologyConceptAdapter queryAdapter) {
		final int totalMembers = getIndexService().getHitCount(branchPath, queryAdapter);
		final List<SnomedConceptIndexEntry> entries = getIndexService().search(branchPath, queryAdapter, offset, limit);
		final List<ISnomedConcept> concepts = convertEntries(branchPath, entries);

		final SnomedConceptList result = new SnomedConceptList();
		result.setTotalMembers(totalMembers);
		result.setMembers(concepts);
		return result;
	}

	private List<ISnomedConcept> convertEntries(final IBranchPath branchPath, final List<SnomedConceptIndexEntry> entries) {
		final SnomedConceptConverter converter = new SnomedConceptConverter(new SnomedBranchRefSetMembershipLookupService(branchPath));
		return ImmutableList.copyOf(Lists.transform(entries, converter));
	}

	private IComponentList<ISnomedConcept> emptyComponentList() {
		final SnomedConceptList result = new SnomedConceptList();
		result.setMembers(ImmutableList.<ISnomedConcept>of());
		return result;
	}
}
