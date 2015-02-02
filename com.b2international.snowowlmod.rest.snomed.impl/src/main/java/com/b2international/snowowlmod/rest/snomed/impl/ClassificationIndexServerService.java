/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowlmod.rest.snomed.impl;

import static com.google.common.collect.Lists.newArrayList;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import com.b2international.snowowlmod.rest.snomed.impl.domain.classification.RelationshipChangeList;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;

import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.datastore.BranchPathUtils;
import com.b2international.snowowl.datastore.index.DocIdCollector;
import com.b2international.snowowl.datastore.index.DocIdCollector.DocIdsIterator;
import com.b2international.snowowl.datastore.server.index.SingleDirectoryIndexServerService;
import com.b2international.snowowl.rest.impl.domain.StorageRef;
import com.b2international.snowowl.rest.snomed.domain.RelationshipModifier;
import com.b2international.snowowl.rest.snomed.domain.classification.ChangeNature;
import com.b2international.snowowl.rest.snomed.domain.classification.ClassificationStatus;
import com.b2international.snowowl.rest.snomed.domain.classification.IClassificationRun;
import com.b2international.snowowl.rest.snomed.domain.classification.IEquivalentConcept;
import com.b2international.snowowl.rest.snomed.domain.classification.IEquivalentConceptSet;
import com.b2international.snowowl.rest.snomed.domain.classification.IRelationshipChange;
import com.b2international.snowowl.rest.snomed.domain.classification.IRelationshipChangeList;
import com.b2international.snowowl.rest.snomed.exception.ClassificationRunNotFoundException;
import com.b2international.snowowlmod.rest.snomed.impl.domain.classification.ClassificationRun;
import com.b2international.snowowlmod.rest.snomed.impl.domain.classification.EquivalentConcept;
import com.b2international.snowowlmod.rest.snomed.impl.domain.classification.EquivalentConceptSet;
import com.b2international.snowowlmod.rest.snomed.impl.domain.classification.RelationshipChange;
import com.b2international.snowowl.snomed.SnomedConstants.Concepts;
import com.b2international.snowowl.snomed.datastore.SnomedConceptIndexEntry;
import com.b2international.snowowl.snomed.reasoner.classification.AbstractEquivalenceSet;
import com.b2international.snowowl.snomed.reasoner.classification.EquivalenceSet;
import com.b2international.snowowl.snomed.reasoner.classification.GetResultResponseChanges;
import com.b2international.snowowl.snomed.reasoner.classification.entry.AbstractChangeEntry.Nature;
import com.b2international.snowowl.snomed.reasoner.classification.entry.RelationshipChangeEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

/**
 * @author apeteri
 */
public class ClassificationIndexServerService extends SingleDirectoryIndexServerService {

	private final ObjectMapper objectMapper;

	public ClassificationIndexServerService(final File indexRootPath) {
		super(indexRootPath, OpenMode.CREATE);
		objectMapper = new ObjectMapper();
	}

	public List<IClassificationRun> getAllClassificationRuns(final StorageRef storageRef, final String userId) throws IOException {

		final BooleanQuery query = new BooleanQuery(true);
		query.add(new TermQuery(new Term("class", ClassificationRun.class.getSimpleName())), Occur.MUST);
		query.add(new TermQuery(new Term("version", storageRef.getCodeSystemVersion().getVersionId())), Occur.MUST);
		query.add(new TermQuery(new Term("userId", userId)), Occur.MUST);
		query.add(new TermQuery(new Term("branchPath", storageRef.getBranchPath().getPath())), Occur.MUST);

		return this.<IClassificationRun>search(query, ClassificationRun.class);
	}

	public IClassificationRun getClassificationRun(final StorageRef storageRef, final String classificationId, final String userId) throws IOException {
		final BooleanQuery query = createClassQuery(ClassificationRun.class.getSimpleName(), classificationId, storageRef, userId);

		try {
			return Iterables.getOnlyElement(search(query, ClassificationRun.class, 1));
		} catch (final NoSuchElementException e) {
			throw new ClassificationRunNotFoundException(classificationId);
		}
	}

	public void insertOrUpdateClassificationRun(final String versionId, final IBranchPath branchPath, final ClassificationRun classificationRun) throws IOException {

		final Document updateDocument = new Document();
		updateDocument.add(new StringField("class", ClassificationRun.class.getSimpleName(), Store.NO));
		updateDocument.add(new StringField("id", classificationRun.getId(), Store.NO));
		updateDocument.add(new StringField("version", versionId, Store.YES));
		updateDocument.add(new StringField("userId", classificationRun.getUserId(), Store.YES));
		updateDocument.add(new StringField("branchPath", branchPath.getPath(), Store.YES));
		updateDocument.add(new StoredField("source", objectMapper.writer().writeValueAsString(classificationRun)));

		final BooleanQuery query = new BooleanQuery(true);
		query.add(new TermQuery(new Term("class", ClassificationRun.class.getSimpleName())), Occur.MUST);
		query.add(new TermQuery(new Term("id", classificationRun.getId())), Occur.MUST);

		writer.deleteDocuments(query);
		writer.addDocument(updateDocument);
		commit();
	}

	public void updateClassificationRunStatus(final UUID id, final ClassificationStatus newStatus) throws IOException {

		final Document sourceDocument = getClassificationRunDocument(id);
		if (null == sourceDocument) {
			return;
		}

		final String version = sourceDocument.get("version");
		final IBranchPath branchPath = BranchPathUtils.createPath(sourceDocument.get("branchPath"));
		final ClassificationRun classificationRun = objectMapper.reader(ClassificationRun.class).readValue(sourceDocument.get("source"));

		if (newStatus.equals(classificationRun.getStatus())) {
			return;
		}

		classificationRun.setStatus(newStatus);

		if (ClassificationStatus.COMPLETED.equals(newStatus) && null == classificationRun.getCompletionDate()) {
			classificationRun.setCompletionDate(new Date());
		}

		insertOrUpdateClassificationRun(version, branchPath, classificationRun);
	}

	public void deleteClassificationData(final UUID id) throws IOException {
		// Removes all documents, not just the classification run document
		writer.deleteDocuments(new Term("id", id.toString()));
		commit();
	}

	public void indexChanges(final GetResultResponseChanges changes) throws IOException {

		final UUID id = changes.getClassificationId();
		final Document sourceDocument = getClassificationRunDocument(id);
		if (null == sourceDocument) {
			return;
		}

		final String version = sourceDocument.get("version");
		final IBranchPath branchPath = BranchPathUtils.createPath(sourceDocument.get("branchPath"));
		final String userId = sourceDocument.get("userId");

		for (final AbstractEquivalenceSet equivalenceSet : changes.getEquivalenceSets()) {

			final List<IEquivalentConcept> convertedEquivalentConcepts = newArrayList();
			for (final SnomedConceptIndexEntry equivalentEntry : equivalenceSet.getConcepts()) {
				addEquivalentConcept(convertedEquivalentConcepts, equivalentEntry);
			}

			if (equivalenceSet instanceof EquivalenceSet) {
				addEquivalentConcept(convertedEquivalentConcepts, ((EquivalenceSet) equivalenceSet).getSuggestedConcept());
			}
			
			final EquivalentConceptSet convertedEquivalenceSet = new EquivalentConceptSet();
			convertedEquivalenceSet.setUnsatisfiable(equivalenceSet.isUnsatisfiable());
			convertedEquivalenceSet.setEquivalentConcepts(convertedEquivalentConcepts);

			indexResult(id, version, branchPath, userId, EquivalentConceptSet.class, convertedEquivalenceSet);
		}

		for (final RelationshipChangeEntry relationshipChange : changes.getRelationshipEntries()) {

			final RelationshipChange convertedRelationshipChange = new RelationshipChange();
			convertedRelationshipChange.setChangeNature(Nature.INFERRED.equals(relationshipChange.getNature()) ? ChangeNature.INFERRED : ChangeNature.REDUNDANT);
			convertedRelationshipChange.setDestinationId(Long.toString(relationshipChange.getDestination().getId()));
			convertedRelationshipChange.setDestinationNegated(relationshipChange.isDestinationNegated());
			convertedRelationshipChange.setGroup(relationshipChange.getGroup());

			final String modifierId = Long.toString(relationshipChange.getModifier().getId());
			convertedRelationshipChange.setModifier(Concepts.UNIVERSAL_RESTRICTION_MODIFIER.equals(modifierId) ? RelationshipModifier.UNIVERSAL : RelationshipModifier.EXISTENTIAL);
			convertedRelationshipChange.setSourceId(Long.toString(relationshipChange.getSource().getId()));
			convertedRelationshipChange.setTypeId(Long.toString(relationshipChange.getType().getId()));
			convertedRelationshipChange.setUnionGroup(relationshipChange.getUnionGroup());

			indexResult(id, version, branchPath, userId, RelationshipChange.class, convertedRelationshipChange);
		}

		commit();
	}

	private void addEquivalentConcept(final List<IEquivalentConcept> convertedEquivalentConcepts, final SnomedConceptIndexEntry equivalentEntry) {
		final EquivalentConcept convertedConcept = new EquivalentConcept();
		convertedConcept.setId(equivalentEntry.getId());
		convertedConcept.setLabel(equivalentEntry.getLabel());

		convertedEquivalentConcepts.add(convertedConcept);
	}

	/**
	 * @param storageRef
	 * @param classificationId
	 * @param userId
	 * @return
	 */
	public List<IEquivalentConceptSet> getEquivalentConceptSets(final StorageRef storageRef, final String classificationId, final String userId) throws IOException {

		final BooleanQuery query = createClassQuery(EquivalentConceptSet.class.getSimpleName(), classificationId, storageRef, userId);
		return this.<IEquivalentConceptSet>search(query, EquivalentConceptSet.class);
	}

	/**
	 * @param storageRef
	 * @param classificationId
	 * @param userId
	 * @param limit 
	 * @param offset 
	 * @return
	 */
	public IRelationshipChangeList getRelationshipChanges(final StorageRef storageRef, final String classificationId, final String userId, final int offset, final int limit) throws IOException {

		final BooleanQuery query = createClassQuery(RelationshipChange.class.getSimpleName(), classificationId, storageRef, userId);
		final RelationshipChangeList result = new RelationshipChangeList();
		
		result.setTotal(getHitCount(query));
		result.setChanges(this.<IRelationshipChange>search(query, RelationshipChange.class, offset, limit));
		
		return result;
	}

	private <T> void indexResult(final UUID id, final String version, final IBranchPath branchPath, final String userId, 
			final Class<T> clazz, final T value) throws IOException {

		final Document updateDocument = new Document();
		updateDocument.add(new StringField("class", clazz.getSimpleName(), Store.NO));
		updateDocument.add(new StringField("id", id.toString(), Store.NO));
		updateDocument.add(new StringField("version", version, Store.NO));
		updateDocument.add(new StringField("userId", userId, Store.NO));
		updateDocument.add(new StringField("branchPath", branchPath.getPath(), Store.NO));
		updateDocument.add(new StoredField("source", objectMapper.writer().writeValueAsString(value)));

		writer.addDocument(updateDocument);
	}

	private Document getClassificationRunDocument(final UUID id) throws IOException {
		final BooleanQuery query = new BooleanQuery(true);
		query.add(new TermQuery(new Term("class", ClassificationRun.class.getSimpleName())), Occur.MUST);
		query.add(new TermQuery(new Term("id", id.toString())), Occur.MUST);

		final Document sourceDocument = Iterables.getFirst(search(query, 1), null);
		return sourceDocument;
	}

	private BooleanQuery createClassQuery(final String className, final String classificationId, final StorageRef storageRef, final String userId) {

		final BooleanQuery query = new BooleanQuery(true);
		query.add(new TermQuery(new Term("class", className)), Occur.MUST);
		query.add(new TermQuery(new Term("id", classificationId)), Occur.MUST);
		query.add(new TermQuery(new Term("version", storageRef.getCodeSystemVersion().getVersionId())), Occur.MUST);
		query.add(new TermQuery(new Term("userId", userId)), Occur.MUST);
		query.add(new TermQuery(new Term("branchPath", storageRef.getBranchPath().getPath())), Occur.MUST);
		return query;
	}

	private <T> List<T> search(final Query query, final Class<? extends T> sourceClass) throws IOException {
		return search(query, sourceClass, Integer.MAX_VALUE);
	}

	private <T> List<T> search(final Query query, final Class<? extends T> sourceClass, final int limit) throws IOException {
		return search(query, sourceClass, 0, limit);
	}

	private <T> List<T> search(final Query query, final Class<? extends T> sourceClass, final int offset, final int limit) throws IOException {
		IndexSearcher searcher = null;

		try {

			searcher = manager.acquire();

			final TopDocs docs = searcher.search(query, null, offset + limit, Sort.INDEXORDER, false, false);
			final ScoreDoc[] scoreDocs = docs.scoreDocs;
			final ImmutableList.Builder<T> resultBuilder = ImmutableList.builder();

			for (int i = offset; i < offset + limit && i < scoreDocs.length; i++) {
				final Document sourceDocument = searcher.doc(scoreDocs[i].doc, ImmutableSet.of("source"));
				final String source = sourceDocument.get("source");
				final T deserializedSource = objectMapper.reader(sourceClass).readValue(source);
				resultBuilder.add(deserializedSource);
			}

			return resultBuilder.build();

		} finally {

			if (null != searcher) {
				manager.release(searcher);
			}
		}
	}

	private List<Document> search(final Query query, final int limit) throws IOException {
		IndexSearcher searcher = null;

		try {

			searcher = manager.acquire();
			final TopDocs docs = searcher.search(query, null, limit, Sort.INDEXORDER, false, false);
			final ImmutableList.Builder<Document> resultBuilder = ImmutableList.builder();

			for (final ScoreDoc scoreDoc : docs.scoreDocs) {
				resultBuilder.add(searcher.doc(scoreDoc.doc));
			}

			return resultBuilder.build();

		} finally {

			if (null != searcher) {
				manager.release(searcher);
			}
		}
	}

	private int getHitCount(final Query query) throws IOException {
		IndexSearcher searcher = null;

		try {

			searcher = manager.acquire();
			final int expectedSize = searcher.getIndexReader().maxDoc();
			final DocIdCollector collector = DocIdCollector.create(expectedSize);

			searcher.search(query, collector);

			int totalHits = 0;
			final DocIdsIterator itr = collector.getDocIDs().iterator();
			while (itr.next()) {
				totalHits++;
			}
			
			return totalHits;

		} finally {

			if (null != searcher) {
				manager.release(searcher);
			}
		}
	}
}
