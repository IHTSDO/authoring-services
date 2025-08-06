package org.ihtsdo.authoringservices.service;

import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import org.ihtsdo.authoringservices.domain.RMPTaskStatus;
import org.ihtsdo.authoringservices.domain.User;
import org.ihtsdo.authoringservices.entity.QRMPTask;
import org.ihtsdo.authoringservices.entity.RMPTask;
import org.ihtsdo.authoringservices.repository.RMPTaskRepository;
import org.ihtsdo.authoringservices.service.client.IMSClientFactory;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

@Service
public class RMPTaskService {

    private final RMPTaskRepository rmpTaskRepository;

    private final EmailService emailService;

    private final IMSClientFactory imsClientFactory;

    @Autowired
    public RMPTaskService(RMPTaskRepository rmpTaskRepository, EmailService emailService, IMSClientFactory imsClientFactory) {
        this.rmpTaskRepository = rmpTaskRepository;
        this.emailService = emailService;
        this.imsClientFactory = imsClientFactory;
    }

    public Page<RMPTask> findTasks(String country, String reporter, Pageable pageable) {
        if (StringUtils.hasLength(country) || StringUtils.hasLength(reporter)) {
            if (StringUtils.hasLength(country) && StringUtils.hasLength(reporter)) {
                return rmpTaskRepository.findAllByCountryAndReporter(country, reporter, pageable);
            } else if (StringUtils.hasLength(country)) {
                return rmpTaskRepository.findAllByCountry(country, pageable);
            } else {
                return rmpTaskRepository.findAllByReporter(reporter, pageable);
            }
        }
        return rmpTaskRepository.findAll(pageable);
    }

    public Page<RMPTask> searchTasks(String country, String criteria, Set<String> reporters, Set<String> assignees, Set<RMPTaskStatus> statuses, Pageable pageable) {
        QRMPTask qRequest = QRMPTask.rMPTask;
        BooleanExpression predicate = qRequest.country.eq(country);
        if (statuses != null && !statuses.isEmpty()) {
            predicate = predicate.and(qRequest.status.in(statuses));
        }
        boolean ignoreReporterFilter = false;
        if (reporters != null && !reporters.isEmpty()) {
            predicate = predicate.and(qRequest.reporter.in(reporters));
            ignoreReporterFilter = true;
        }
        boolean ignoreAssigneeFilter = false;
        if (assignees != null && !assignees.isEmpty()) {
            predicate = predicate.and(qRequest.assignee.in(assignees));
            ignoreAssigneeFilter = true;
        }
        if(criteria != null) {
            predicate = predicate.andAnyOf(buildSearchPredicate(criteria, ignoreReporterFilter, ignoreAssigneeFilter));
        }

        return rmpTaskRepository.findAll(predicate, pageable);
    }

    private static Predicate[] buildSearchPredicate(String searchString, boolean ignoreReporterFilter, boolean ignoreAssigneeFilter) {
        QRMPTask qRequest = QRMPTask.rMPTask;
        BooleanExpression searchRequestId = org.apache.commons.lang3.StringUtils.isNumeric(searchString) ? qRequest.id.eq(Long.parseLong(searchString)) : null;
        BooleanExpression searchSummary = qRequest.summary.containsIgnoreCase(searchString);
        BooleanExpression searchType = qRequest.type.containsIgnoreCase(searchString);
        BooleanExpression searchAssignee = ignoreAssigneeFilter ? null : qRequest.assignee.stringValue().containsIgnoreCase(searchString);
        BooleanExpression searchReporter = ignoreReporterFilter ? null : qRequest.reporter.containsIgnoreCase(searchString);

        return new Predicate[]{searchRequestId, searchSummary, searchType, searchAssignee, searchReporter};
    }

    public Optional<RMPTask> getTaskById(long id) {
        return rmpTaskRepository.findById(id);
    }

    public RMPTask createTask(RMPTask rmpTask) {
        rmpTask.setStatus(RMPTaskStatus.NEW);
        rmpTask.setReporter(SecurityUtil.getUsername());
        rmpTask.setAssignee(null);
        return rmpTaskRepository.save(rmpTask);
    }

    public Optional<RMPTask> updateTask(long id, RMPTask updatedTask) {
        Optional<RMPTask> taskOptional = rmpTaskRepository.findById(id);
        if (taskOptional.isEmpty()) throw new ResourceNotFoundException(String.format("RMP task %s not found", id));

        RMPTask existingTask = taskOptional.get();
        RMPTaskStatus updatedTaskStatus = updatedTask.getStatus();
        boolean statusChanged = !updatedTask.getStatus().equals(existingTask.getStatus());
        if (statusChanged && !validTaskStatusTransition(existingTask.getStatus(), updatedTaskStatus)) {
            throw new IllegalStateException(String.format("Unable to transition status from %s to %s", existingTask.getStatus().name(), updatedTaskStatus.name()));
        }

        existingTask.setStatus(updatedTask.getStatus());
        existingTask.setType(updatedTask.getType());
        existingTask.setCountry(updatedTask.getCountry());
        existingTask.setReporter(updatedTask.getReporter());
        existingTask.setAssignee(updatedTask.getAssignee());
        existingTask.setSummary(updatedTask.getSummary());
        existingTask.setLanguageRefset(updatedTask.getLanguageRefset());
        existingTask.setContextRefset(updatedTask.getContextRefset());
        existingTask.setConcept(updatedTask.getConcept());
        existingTask.setConceptId(updatedTask.getConceptId());
        existingTask.setConceptName(updatedTask.getConceptName());
        existingTask.setRelationshipType(updatedTask.getRelationshipType());
        existingTask.setRelationshipTarget(updatedTask.getRelationshipTarget());
        existingTask.setExistingRelationship(updatedTask.getExistingRelationship());
        existingTask.setMemberConceptIds(updatedTask.getMemberConceptIds());
        existingTask.setEclQuery(updatedTask.getEclQuery());
        existingTask.setExistingDescription(updatedTask.getExistingDescription());
        existingTask.setNewDescription(updatedTask.getNewDescription());
        existingTask.setNewFSN(updatedTask.getNewFSN());
        existingTask.setNewPT(updatedTask.getNewPT());
        existingTask.setNewSynonyms(updatedTask.getNewSynonyms());
        existingTask.setParentConcept(updatedTask.getParentConcept());
        existingTask.setJustification(updatedTask.getJustification());
        existingTask.setReference(updatedTask.getReference());

        RMPTask savedRmpTask = rmpTaskRepository.save(existingTask);
        if (statusChanged) {
            notifyStatusChange(savedRmpTask, updatedTaskStatus.getLabel());
        }
        return Optional.of(savedRmpTask);
    }

    @Transactional
    public boolean deleteTask(long id) {
        Optional<RMPTask> rmpTaskOptional = rmpTaskRepository.findById(id);
        if (rmpTaskOptional.isPresent()) {
            rmpTaskRepository.delete(rmpTaskOptional.get());
            return true;
        }
        return false;
    }

    public Optional<RMPTask> updateTaskStatus(long id, String newStatus) {
        Optional<RMPTask> taskOptional = rmpTaskRepository.findById(id);
        if (taskOptional.isPresent()) {
            RMPTask rmpTask = taskOptional.get();
            RMPTaskStatus currentStatus = rmpTask.getStatus();
            if (currentStatus.equals(RMPTaskStatus.valueOf(newStatus))) {
                throw new IllegalStateException(String.format("Unable to update the RMP task status. The status has already been %s", newStatus));
            }
            if (!validTaskStatusTransition(currentStatus, RMPTaskStatus.valueOf(newStatus))) {
                throw new IllegalStateException(String.format("Unable to transition status from %s to %s", currentStatus.name(), newStatus));
            }

            rmpTask.setStatus(RMPTaskStatus.valueOf(newStatus));
            RMPTask savedRmpTask = rmpTaskRepository.save(rmpTask);
            notifyStatusChange(savedRmpTask, RMPTaskStatus.valueOf(newStatus).getLabel());
            return Optional.of(savedRmpTask);
        }
        throw new ResourceNotFoundException(String.format("RMP task %s not found", id));
    }

    private boolean validTaskStatusTransition(RMPTaskStatus currentStatus, RMPTaskStatus newStatus) {
        return switch (newStatus) {
            case ACCEPTED -> currentStatus.equals(RMPTaskStatus.NEW);
            case IN_PROGRESS ->
                    currentStatus.equals(RMPTaskStatus.ACCEPTED) || currentStatus.equals(RMPTaskStatus.ON_HOLD) || currentStatus.equals(RMPTaskStatus.CLARIFICATION_REQUESTED)
                            || currentStatus.equals(RMPTaskStatus.UNDER_APPEAL) || currentStatus.equals(RMPTaskStatus.APPEAL_CLARIFICATION_REQUESTED);
            case READY_FOR_RELEASE ->
                    currentStatus.equals(RMPTaskStatus.IN_PROGRESS) || currentStatus.equals(RMPTaskStatus.PUBLISHED);
            case PUBLISHED -> currentStatus.equals(RMPTaskStatus.READY_FOR_RELEASE);
            case ON_HOLD, CLOSED -> currentStatus.equals(RMPTaskStatus.IN_PROGRESS);
            case CLARIFICATION_REQUESTED -> currentStatus.equals(RMPTaskStatus.ACCEPTED) || currentStatus.equals(RMPTaskStatus.IN_PROGRESS);
            case APPEAL_CLARIFICATION_REQUESTED, APPEAL_REJECTED -> currentStatus.equals(RMPTaskStatus.UNDER_APPEAL);
            case UNDER_APPEAL ->
                    currentStatus.equals(RMPTaskStatus.REJECTED) || currentStatus.equals(RMPTaskStatus.APPEAL_CLARIFICATION_REQUESTED);
            default -> true;
        };
    }

    private void notifyStatusChange(RMPTask task, String newStatus) {
        String currentUser = SecurityUtil.getUsername();
        Collection<User> recipients = new ArrayList<>();
        if (task.getAssignee() != null && !currentUser.equals(task.getAssignee())) {
            recipients.add(imsClientFactory.getClient().getUserDetails(task.getAssignee()));
        }
        if (task.getReporter() != null && !currentUser.equals(task.getReporter())) {
            recipients.add(imsClientFactory.getClient().getUserDetails(task.getReporter()));
        }
        emailService.sendRMPTaskStatusChangeNotification(task.getId(), task.getSummary(), newStatus, recipients);
    }
}
