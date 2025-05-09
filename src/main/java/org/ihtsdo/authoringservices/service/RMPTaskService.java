package org.ihtsdo.authoringservices.service;

import org.ihtsdo.authoringservices.domain.RMPTaskStatus;
import org.ihtsdo.authoringservices.domain.User;
import org.ihtsdo.authoringservices.entity.RMPTask;
import org.ihtsdo.authoringservices.repository.RMPTaskCrudRepository;
import org.ihtsdo.authoringservices.repository.RMPTaskPagingAndSortingRepository;
import org.ihtsdo.authoringservices.service.client.IMSClientFactory;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

@Service
public class RMPTaskService {

    private final RMPTaskCrudRepository rmpTaskCrudRepository;

    private final RMPTaskPagingAndSortingRepository rmpTaskPagingAndSortingRepository;

    private final EmailService emailService;

    private final IMSClientFactory imsClientFactory;

    private final CommentService commentService;

    @Autowired
    public RMPTaskService(RMPTaskCrudRepository rmpTaskCrudRepository, EmailService emailService, IMSClientFactory imsClientFactory, CommentService commentService, RMPTaskPagingAndSortingRepository rmpTaskPagingAndSortingRepository) {
        this.rmpTaskCrudRepository = rmpTaskCrudRepository;
        this.emailService = emailService;
        this.imsClientFactory = imsClientFactory;
        this.commentService = commentService;
        this.rmpTaskPagingAndSortingRepository = rmpTaskPagingAndSortingRepository;
    }

    public Page<RMPTask> getAllTasks(Pageable pageable) {
        return rmpTaskPagingAndSortingRepository.findAll(pageable);
    }

    public Optional<RMPTask> getTaskById(long id) {
        return rmpTaskCrudRepository.findById(id);
    }

    public RMPTask createTask(RMPTask rmpTask) {
        rmpTask.setStatus(RMPTaskStatus.NEW);
        rmpTask.setReporter(SecurityUtil.getUsername());
        return rmpTaskCrudRepository.save(rmpTask);
    }

    public Optional<RMPTask> updateTask(long id, RMPTask updatedTask) {
        Optional<RMPTask> taskOptional = rmpTaskCrudRepository.findById(id);
        if (taskOptional.isEmpty()) throw new ResourceNotFoundException(String.format("RMP task %s not found", id));

        RMPTask existingTask = taskOptional.get();
        RMPTaskStatus updatedTaskStatus = updatedTask.getStatus();
        boolean statusChanged = !updatedTask.getStatus().equals(existingTask.getStatus());
        if (statusChanged && !validTaskStatusTransition(existingTask.getStatus(), updatedTaskStatus)) {
            throw new IllegalStateException(String.format("Unable to transition status from %s to %s", existingTask.getStatus().name(), updatedTaskStatus.name()));
        }

        existingTask.setStatus(updatedTask.getStatus());
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

        RMPTask savedRmpTask = rmpTaskCrudRepository.save(existingTask);
        if (statusChanged) {
            notifyStatusChange(savedRmpTask, updatedTaskStatus.getLabel());
        }
        return Optional.of(savedRmpTask);
    }

    @Transactional
    public boolean deleteTask(long id) {
        Optional<RMPTask> rmpTaskOptional = rmpTaskCrudRepository.findById(id);
        if (rmpTaskOptional.isPresent()) {
            commentService.deleteCommentsByRmpTask(rmpTaskOptional.get());
            rmpTaskCrudRepository.delete(rmpTaskOptional.get());
            return true;
        }
        return false;
    }

    public Optional<RMPTask> updateTaskStatus(long id, String newStatus) {
        Optional<RMPTask> taskOptional = rmpTaskCrudRepository.findById(id);
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
            RMPTask savedRmpTask = rmpTaskCrudRepository.save(rmpTask);
            notifyStatusChange(savedRmpTask, newStatus);
            return Optional.of(savedRmpTask);
        }
        throw new ResourceNotFoundException(String.format("RMP task %s not found", id));
    }

    private boolean validTaskStatusTransition(RMPTaskStatus currentStatus, RMPTaskStatus newStatus) {
        return switch (newStatus) {
            case ACCEPTED -> currentStatus.equals(RMPTaskStatus.NEW);
            case IN_PROGRESS -> currentStatus.equals(RMPTaskStatus.ACCEPTED) || currentStatus.equals(RMPTaskStatus.ON_HOLD) || currentStatus.equals(RMPTaskStatus.CLARIFICATION_REQUESTED)
                    || currentStatus.equals(RMPTaskStatus.UNDER_APPEAL) || currentStatus.equals(RMPTaskStatus.APPEAL_CLARIFICATION_REQUESTED);
            case READY_FOR_RELEASE -> currentStatus.equals(RMPTaskStatus.IN_PROGRESS) || currentStatus.equals(RMPTaskStatus.PUBLISHED);
            case PUBLISHED ->  currentStatus.equals(RMPTaskStatus.READY_FOR_RELEASE);
            case ON_HOLD, CLOSED ->  currentStatus.equals(RMPTaskStatus.IN_PROGRESS);
            case CLARIFICATION_REQUESTED -> currentStatus.equals(RMPTaskStatus.ACCEPTED);
            case APPEAL_CLARIFICATION_REQUESTED, APPEAL_REJECTED -> currentStatus.equals(RMPTaskStatus.UNDER_APPEAL);
            case UNDER_APPEAL -> currentStatus.equals(RMPTaskStatus.REJECTED) || currentStatus.equals(RMPTaskStatus.APPEAL_CLARIFICATION_REQUESTED);
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
