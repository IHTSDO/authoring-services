package org.ihtsdo.authoringservices.service;

import org.ihtsdo.authoringservices.domain.RMPTaskStatus;
import org.ihtsdo.authoringservices.domain.User;
import org.ihtsdo.authoringservices.entity.RMPTask;
import org.ihtsdo.authoringservices.repository.RMPTaskRepository;
import org.ihtsdo.authoringservices.service.client.IMSClientFactory;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class RMPTaskService {

    private final RMPTaskRepository rmpTaskRepository;

    private final EmailService emailService;

    private final IMSClientFactory imsClientFactory;

    private final CommentService commentService;

    @Autowired
    public RMPTaskService(RMPTaskRepository rmpTaskRepository, EmailService emailService, IMSClientFactory imsClientFactory, CommentService commentService) {
        this.rmpTaskRepository = rmpTaskRepository;
        this.emailService = emailService;
        this.imsClientFactory = imsClientFactory;
        this.commentService = commentService;
    }

    public List<RMPTask> getAllTasks() {
        Iterable<RMPTask> iterable = rmpTaskRepository.findAll();
        return StreamSupport.stream(iterable.spliterator(), false).collect(Collectors.toList());
    }

    public Optional<RMPTask> getTaskById(long id) {
        return rmpTaskRepository.findById(id);
    }

    public RMPTask createTask(RMPTask rmpTask) {
        rmpTask.setStatus(RMPTaskStatus.NEW);
        rmpTask.setReporter(SecurityUtil.getUsername());
        return rmpTaskRepository.save(rmpTask);
    }

    public Optional<RMPTask> updateTask(long id, RMPTask updatedTask) {
        return rmpTaskRepository.findById(id).map(existingTask -> {
            RMPTaskStatus updatedTaskStatus = updatedTask.getStatus();
            boolean statusChanged = !updatedTask.getStatus().equals(existingTask.getStatus());

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

            RMPTask savedRmpTask = rmpTaskRepository.save(updatedTask);
            if (statusChanged) {
                notifyStatusChange(savedRmpTask, updatedTaskStatus.getLabel());
            }
            return savedRmpTask;
        });
    }

    @Transactional
    public boolean deleteTask(long id) {
        if (rmpTaskRepository.existsById(id)) {
            RMPTask rmpTask = new RMPTask();
            rmpTask.setId(id);
            commentService.deleteCommentsByRmpTask(rmpTask);
            rmpTaskRepository.deleteById(id);
            return true;
        }
        return false;
    }

    public Optional<RMPTask> updateTaskStatus(long id, String newStatus) throws BusinessServiceException {
        Optional<RMPTask> taskOptional = rmpTaskRepository.findById(id);
        if (taskOptional.isPresent()) {
            RMPTask rmpTask = taskOptional.get();
            RMPTaskStatus currentStatus = rmpTask.getStatus();
            if (currentStatus.equals(RMPTaskStatus.valueOf(newStatus))) {
                throw new BusinessServiceException(String.format("Unable to update the RMP task status. The status has already been %s", newStatus));
            }
            rmpTask.setStatus(RMPTaskStatus.fromLabel(newStatus));
            RMPTask savedRmpTask = rmpTaskRepository.save(rmpTask);
            notifyStatusChange(savedRmpTask, newStatus);
            return Optional.of(savedRmpTask);
        }
        throw new ResourceNotFoundException(String.format("RMP task %s not found", id));
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
