package org.ihtsdo.snowowl.authoring.api.services;

import com.b2international.snowowl.api.domain.IComponentRef;
import com.b2international.snowowl.api.impl.domain.InternalStorageRef;
import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.datastore.index.IndexQueryBuilder;
import com.b2international.snowowl.datastore.index.IndexUtils;
import com.b2international.snowowl.snomed.datastore.SnomedConceptIndexEntry;
import com.b2international.snowowl.snomed.datastore.browser.SnomedIndexBrowserConstants;
import com.b2international.snowowl.snomed.datastore.index.SnomedConceptIndexQueryAdapter;
import com.b2international.snowowl.snomed.datastore.index.SnomedIndexService;
import org.apache.lucene.util.BytesRef;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ContentServiceImpl implements ContentService {

	private final SnomedIndexService snomedIndexService;

	public ContentServiceImpl() {
		snomedIndexService = ApplicationContext.getServiceForClass(SnomedIndexService.class);
	}

	@Override
	public Set<String> getDescendantIds(final IComponentRef ref) {
		InternalStorageRef internalRef = (InternalStorageRef) ref;
		internalRef.checkStorageExists();

		IBranchPath branchPath = internalRef.getBranchPath();
		IndexQueryAdapter indexQueryAdapter = new IndexQueryAdapter(ref.getComponentId());
		Set<String> conceptIds = new HashSet<>();
		List<SnomedConceptIndexEntry> entries = snomedIndexService.search(branchPath, indexQueryAdapter);
		for (SnomedConceptIndexEntry entry : entries) {
			conceptIds.add(entry.getId());
		}
		return conceptIds;
	}

	private static final class IndexQueryAdapter extends SnomedConceptIndexQueryAdapter {

		public static final int SEARCH_PARENT = 1 << 8;
		public static final int SEARCH_ANCESTOR = 1 << 9;

		protected IndexQueryAdapter(String conceptId) {
			super(conceptId, SEARCH_PARENT | SEARCH_ANCESTOR | SEARCH_ACTIVE_CONCEPTS, null);
		}

		@Override
		protected IndexQueryBuilder createIndexQueryBuilder() {
			BytesRef conceptIdByteRef = IndexUtils.longToPrefixCoded(this.searchString);
			IndexQueryBuilder queryBuilder = new IndexQueryBuilder()
					.matchExactTerm(SnomedIndexBrowserConstants.CONCEPT_PARENT, conceptIdByteRef)
					.matchExactTerm(SnomedIndexBrowserConstants.CONCEPT_ANCESTOR, conceptIdByteRef);
			return super.createIndexQueryBuilder().require(queryBuilder);
		}
	}

}