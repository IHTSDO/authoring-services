package org.ihtsdo.authoringservices.service;

import org.ihtsdo.authoringservices.service.client.TraceabilityClient;
import org.ihtsdo.authoringservices.service.client.TraceabilityClientFactory;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Branch;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Classification;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ClassificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Shared classification currency / acceptance checks used by review and promotion prerequisites.
 */
@Service
public class ClassificationPrerequisiteService {

	private static final String CLASSIFICATION_SAVE = "CLASSIFICATION_SAVE";
	private static final String STATUS_RUNNING = "RUNNING";
	private static final String STATUS_FAILED = "FAILED";
	private static final String STATUS_COMPLETED = "COMPLETED";
	private static final String STATUS_STALE = "STALE";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final SnowstormClassificationClient classificationClient;
	private final TraceabilityClientFactory traceabilityClientFactory;

	public ClassificationPrerequisiteService(SnowstormClassificationClient classificationClient,
			TraceabilityClientFactory traceabilityClientFactory) {
		this.classificationClient = classificationClient;
		this.traceabilityClientFactory = traceabilityClientFactory;
	}

	public enum Context {
		REVIEW(new Messages(
				"Branch Not Provided: Branch not provided to submit for review. This is a fatal error: contact an administrator",
				"Classification Not Run: No classifications were run on this branch.",
				"Classification Not Current: Classification was run, but modifications were made after the classifier was initiated.",
				"Classification May Not Be Current: Could not determine whether modifications were made after saving the classification.",
				"Classification Not Current: Classification was run, but modifications were made to the task afterwards.",
				"Equivalencies Found: Classification reports equivalent concepts on this branch. You may not submit for review until these are resolved"),
				false,
				false),
		PROMOTION(new Messages(
				"Could Not Retrieve Branch Details: Could not retrieve branch details. This is a fatal error; contact an administrator",
				"Classification Not Run: No classifications were run on this branch. Promote only if you are sure your changes will not affect future classification.",
				"Classification Not Current: Classification was run, but modifications were made after the classifier was initiated. Promote only if you are sure any changes will not affect future classification.",
				"Classification May Not Be Current: Could not determine whether modifications were made after saving the classification. Promote only if you sure any changes will not affect future classification.",
				"Classification Not Current: Classification was run, but modifications were made to the task afterwards. Promote only if you are sure those changes will not affect future classifications.",
				"Equivalencies Found: Classification reports equivalent concepts on this branch. You may not promote until these are resolved"),
				true,
				true);

		private final Messages messages;
		private final boolean treatRunningAsHardBlocker;
		private final boolean normalizeStatus;

		Context(Messages messages, boolean treatRunningAsHardBlocker, boolean normalizeStatus) {
			this.messages = messages;
			this.treatRunningAsHardBlocker = treatRunningAsHardBlocker;
			this.normalizeStatus = normalizeStatus;
		}

		private record Messages(
				String branchMissing,
				String classificationNotRun,
				String completedNotCurrent,
				String savedMayNotBeCurrent,
				String savedNotCurrent,
				String equivalencies) {
		}
	}

	public record ClassificationPrerequisiteResult(
			boolean classificationCurrent,
			String classificationStatus,
			boolean equivalenciesFound,
			List<String> blockers) {
	}

	public TraceabilityClient.ActivitiesPage fetchActivities(String branchPath) {
		TraceabilityClient client = traceabilityClientFactory.getClient();
		if (client == null) {
			logger.debug("Traceability URL not configured; assuming no branch activities");
			TraceabilityClient.ActivitiesPage empty = new TraceabilityClient.ActivitiesPage();
			empty.setContent(List.of());
			empty.setNumberOfElements(0);
			return empty;
		}
		return client.getActivitiesForBranch(branchPath);
	}

	public Classification getLatestClassificationOrNull(String branchPath) {
		try {
			return classificationClient.getLatestClassification(branchPath);
		} catch (RestClientException e) {
			logger.warn("Failed to retrieve latest classification for {}: {}", branchPath, e.getMessage());
			return null;
		}
	}

	public ClassificationPrerequisiteResult evaluate(Branch branch, Classification classification,
			TraceabilityClient.ActivitiesPage activities, Context context) {
		List<String> blockers = new ArrayList<>();
		if (branch == null) {
			blockers.add(context.messages.branchMissing);
			return new ClassificationPrerequisiteResult(false, null, false, blockers);
		}

		if (classification == null) {
			blockers.add(context.messages.classificationNotRun);
			return new ClassificationPrerequisiteResult(false, null, false, blockers);
		}

		ClassificationStatus status = classification.getStatus();
		boolean equivalenciesFound = Boolean.TRUE.equals(classification.getEquivalentConceptsFound());
		boolean statusOk = isClassificationStatusOk(status);
		if (!statusOk) {
			if (context.treatRunningAsHardBlocker
					&& (status == ClassificationStatus.RUNNING || status == ClassificationStatus.SAVING_IN_PROGRESS)) {
				blockers.add("Classification is currently running");
			} else {
				blockers.add("Classification Not Completed: Classification was started for this branch, but either failed or has not completed.");
			}
		}

		boolean current = checkClassificationCurrency(status, classification, branch, activities, blockers, context);
		checkClassificationAcceptance(status, classification, blockers, context, equivalenciesFound);

		boolean classificationCurrent = statusOk && current;
		String classificationStatus = resolveClassificationStatus(context, status, classificationCurrent);
		return new ClassificationPrerequisiteResult(classificationCurrent, classificationStatus, equivalenciesFound, blockers);
	}

	private static String resolveClassificationStatus(Context context, ClassificationStatus status,
			boolean classificationCurrent) {
		if (context.normalizeStatus) {
			return mapClassificationStatus(status, classificationCurrent);
		}
		if (status != null) {
			return status.name();
		}
		return null;
	}

	private static boolean isClassificationStatusOk(ClassificationStatus status) {
		return status == ClassificationStatus.COMPLETED
				|| status == ClassificationStatus.SAVING_IN_PROGRESS
				|| status == ClassificationStatus.SAVED;
	}

	private boolean checkClassificationCurrency(ClassificationStatus status, Classification classification,
			Branch branch, TraceabilityClient.ActivitiesPage activities, List<String> blockers, Context context) {
		if (status == ClassificationStatus.COMPLETED) {
			return isCompletedClassificationCurrent(classification, branch, blockers, context);
		}
		if (status == ClassificationStatus.SAVED) {
			return isSavedClassificationCurrent(classification, activities, blockers, context);
		}
		return true;
	}

	private static boolean isCompletedClassificationCurrent(Classification classification, Branch branch,
			List<String> blockers, Context context) {
		Date creationDate = classification.getCreationDate();
		if (creationDate != null && creationDate.getTime() < branch.getHeadTimestamp()) {
			blockers.add(context.messages.completedNotCurrent);
			return false;
		}
		return true;
	}

	private boolean isSavedClassificationCurrent(Classification classification,
			TraceabilityClient.ActivitiesPage activities, List<String> blockers, Context context) {
		if (classification.getSaveDate() == null) {
			blockers.add(context.messages.savedMayNotBeCurrent);
			return false;
		}
		if (!isClassificationSavedCurrent(activities)) {
			blockers.add(context.messages.savedNotCurrent);
			return false;
		}
		return true;
	}

	private static void checkClassificationAcceptance(ClassificationStatus status, Classification classification,
			List<String> blockers, Context context, boolean equivalenciesFound) {
		boolean hasResults = equivalenciesFound
				|| Boolean.TRUE.equals(classification.getInferredRelationshipChangesFound())
				|| Boolean.TRUE.equals(classification.getRedundantStatedRelationshipsFound());
		if (status != ClassificationStatus.SAVED && hasResults) {
			blockers.add("Classification Not Accepted: Classification results were not accepted to this branch");
		}
		if (equivalenciesFound) {
			blockers.add(context.messages.equivalencies);
		}
	}

	private boolean isClassificationSavedCurrent(TraceabilityClient.ActivitiesPage activities) {
		List<TraceabilityClient.Activity> content = activities.getContent();
		if (content == null || content.isEmpty()) {
			return false;
		}
		TraceabilityClient.Activity lastActivity = content.get(content.size() - 1);
		if (lastActivity.getCommitDate() == null) {
			return false;
		}
		long lastModifiedTime = lastActivity.getCommitDate().getTime();
		long lastClassificationSaved = 0;
		for (TraceabilityClient.Activity activity : content) {
			if (CLASSIFICATION_SAVE.equals(activity.getActivityType()) && activity.getCommitDate() != null) {
				lastClassificationSaved = activity.getCommitDate().getTime();
			}
		}
		return lastClassificationSaved == lastModifiedTime;
	}

	private static String mapClassificationStatus(ClassificationStatus status, boolean current) {
		if (status == null) {
			return null;
		}
		if (status == ClassificationStatus.RUNNING || status == ClassificationStatus.SAVING_IN_PROGRESS) {
			return STATUS_RUNNING;
		}
		if (status == ClassificationStatus.FAILED) {
			return STATUS_FAILED;
		}
		if ((status == ClassificationStatus.COMPLETED || status == ClassificationStatus.SAVED) && !current) {
			return STATUS_STALE;
		}
		if (status == ClassificationStatus.COMPLETED || status == ClassificationStatus.SAVED) {
			return STATUS_COMPLETED;
		}
		return status.name();
	}
}
