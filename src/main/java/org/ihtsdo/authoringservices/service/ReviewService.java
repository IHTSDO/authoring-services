package org.ihtsdo.authoringservices.service;

import jakarta.transaction.Transactional;
import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.authoringservices.entity.Branch;
import org.ihtsdo.authoringservices.entity.ReviewConceptView;
import org.ihtsdo.authoringservices.entity.ReviewMessage;
import org.ihtsdo.authoringservices.repository.BranchRepository;
import org.ihtsdo.authoringservices.repository.ReviewConceptViewRepository;
import org.ihtsdo.authoringservices.repository.ReviewMessageRepository;
import org.ihtsdo.authoringservices.service.factory.TaskServiceFactory;
import org.ihtsdo.otf.rest.exception.BadRequestException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ReviewService {

	@Autowired
	private ReviewMessageRepository messageRepository;

	@Autowired
	private ReviewConceptViewRepository reviewConceptViewRepository;

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private EmailService emailService;

	@Autowired
	private TaskServiceFactory taskServiceFactory;

	@Transactional
	public List<ReviewConcept> retrieveTaskReviewConceptDetails(String projectKey, String taskKey, String username) {
		final Branch branch = branchRepository.findOneByProjectAndTask(projectKey, taskKey);
		return getReviewConcepts(username, branch);
	}

	@Transactional
	public List<ReviewConcept> retrieveProjectReviewConceptDetails(String projectKey, String username) {
		final Branch branch = branchRepository.findOneByProjectAndTask(projectKey, null);
		return getReviewConcepts(username, branch);
	}

	private List<ReviewConcept> getReviewConcepts(String username, Branch branch) {
		final List<ReviewConcept> reviewConcepts = new ArrayList<>();
		if (branch != null) {
			final Map<String, List<ReviewMessage>> conceptMessagesMap = getConceptMessagesMap(branch);
			final Map<String, Date> conceptViewDatesMap = getLatestConceptViewDatesMap(username, branch);
			final Set<String> conceptIds = new HashSet<>(conceptMessagesMap.keySet());
			conceptIds.addAll(conceptViewDatesMap.keySet());
			for (String conceptId : conceptIds) {
				final List<ReviewMessage> messages = conceptMessagesMap.get(conceptId);
				reviewConcepts
						.add(new ReviewConcept(conceptId, messages != null ? messages : new ArrayList<>(),
								conceptViewDatesMap.get(conceptId)));
			}
		}
		return reviewConcepts;
	}

	private Map<String, Date> getLatestConceptViewDatesMap(String username, Branch branch) {
		final List<ReviewConceptView> reviewConceptViews = reviewConceptViewRepository
				.findByBranchAndUsernameOrderByViewDateAsc(branch, username);
		final Map<String, Date> reviewConceptViewDateMap = new HashMap<>();
		for (ReviewConceptView reviewConceptView : reviewConceptViews) {
			reviewConceptViewDateMap.put(reviewConceptView.getConceptId(), reviewConceptView.getViewDate());
		}
		return reviewConceptViewDateMap;
	}

	/**
	 * Builds a map of conceptIds and messages with that concept in the subject.
	 * 
	 * @param branch
	 * @return
	 */
	private Map<String, List<ReviewMessage>> getConceptMessagesMap(Branch branch) {
		final List<ReviewMessage> reviewMessages = messageRepository.findByBranch(branch);
		Map<String, List<ReviewMessage>> conceptMessagesMap = new HashMap<>();
		for (ReviewMessage reviewMessage : reviewMessages) {
			for (String conceptId : reviewMessage.getSubjectConceptIds()) {
				conceptMessagesMap.computeIfAbsent(conceptId, k -> new ArrayList<>());
				conceptMessagesMap.get(conceptId).add(reviewMessage);
			}
		}
		return conceptMessagesMap;
	}

	public ReviewMessage postReviewMessage(String projectKey, String taskKey, ReviewMessageCreateRequest createRequest,
			String fromUsername) throws BusinessServiceException {
		final List<String> subjectConceptIds = createRequest.getSubjectConceptIds();
		if (subjectConceptIds == null || subjectConceptIds.isEmpty()) {
			throw new BadRequestException("There must be at least one id in subjectConceptIds");
		}
		final Branch branch = getCreateBranch(projectKey, taskKey);
		final ReviewMessage message = messageRepository.save(new ReviewMessage(branch, createRequest.getMessageHtml(),
				subjectConceptIds, createRequest.isFeedbackRequested(), fromUsername));
		for (ReviewMessageSentListener listener : getReviewMessageSentListeners()) {
			listener.messageSent(message, createRequest.getEvent());
		}
		// Send email to Author/Reviewers
		if (taskKey != null) {
			AuthoringTask authoringTask = taskServiceFactory.getInstanceByKey(taskKey).retrieveTask(projectKey, taskKey, true, true);
			if (authoringTask.getAssignee() != null || authoringTask.getReviewers() != null) {
				List<User> recipients = new ArrayList<>();
				boolean isTaskAuthor = authoringTask.getAssignee() != null && fromUsername.equals(authoringTask.getAssignee().getUsername());
				if (isTaskAuthor && authoringTask.getReviewers() != null) {
					recipients.addAll(authoringTask.getReviewers());
				} else if (!isTaskAuthor && authoringTask.getAssignee() != null) {
					recipients.add(authoringTask.getAssignee());
				}
				recipients = recipients.stream().filter(item -> !item.getUsername().equals(fromUsername)).toList();
				emailService.sendCommentAddedNotification(projectKey, taskKey, authoringTask.getSummary(), createRequest.getMessageHtml(), recipients);
			}
		}

		return message;
	}

	public void recordConceptView(String projectKey, String taskKey, String conceptId, String username) {
		final Branch branch = getCreateBranch(projectKey, taskKey);
		reviewConceptViewRepository.save(new ReviewConceptView(branch, conceptId, username));
	}

	private synchronized Branch getCreateBranch(String projectKey, String taskKey) {
		Branch branch = branchRepository.findOneByProjectAndTask(projectKey, taskKey);
		if (branch == null) {
			branch = branchRepository.save(new Branch(projectKey, taskKey));
		}
		return branch;
	}

	public TaskMessagesDetail getTaskMessagesDetail(String projectKey, String taskKey, String username) {
		TaskMessagesDetail detail = new TaskMessagesDetail();

		detail.setTaskMessagesStatus(TaskMessagesStatus.none);
		final Branch branch = branchRepository.findOneByProjectAndTask(projectKey, taskKey);
		if (branch != null) {
			final List<ReviewConcept> reviewConcepts = getReviewConcepts(username, branch);
			for (ReviewConcept reviewConcept : reviewConcepts) {

				// get the view date and save in task messages detail
				Date viewDate = reviewConcept.getViewDate();
				if(null != detail.getViewDate() && null != viewDate) {
					if (viewDate.after(detail.getViewDate())) {
						detail.setViewDate(viewDate);
					}
				} else {
					detail.setViewDate(viewDate);
				}			

				// check dates and messages status
				for (ReviewMessage reviewMessage : reviewConcept.getMessages()) {

					final Date messageDate = reviewMessage.getCreationDate();

					// If last message date null, simply set
					if (detail.getLastMessageDate() == null) {
						detail.setLastMessageDate(messageDate);
					}

					// otherwise check for later date
					else if (messageDate != null && messageDate.after(detail.getLastMessageDate())) {
						detail.setLastMessageDate(messageDate);
					}

					// if another user left message after view date, mark unread
					if (!username.equals(reviewMessage.getFromUsername()) && (viewDate == null || (messageDate != null && messageDate.after(viewDate)))) {
						detail.setTaskMessagesStatus(TaskMessagesStatus.unread);
					}

					// if not already marked unread, mark read
					else if (!detail.getTaskMessagesStatus().equals(TaskMessagesStatus.unread)) {
						detail.setTaskMessagesStatus(TaskMessagesStatus.read);
					}
				}

			}
		}
		return detail;
	}

	/**
	 * Autowire ReviewMessageSentListener beans
	 * 
	 * @return
	 */
	private Collection<ReviewMessageSentListener> getReviewMessageSentListeners() {
		return applicationContext.getBeansOfType(ReviewMessageSentListener.class).values();
	}
}
