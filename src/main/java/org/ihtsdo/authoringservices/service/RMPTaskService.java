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

    @Autowired
    public RMPTaskService(RMPTaskRepository rmpTaskRepository, EmailService emailService, IMSClientFactory imsClientFactory) {
        this.rmpTaskRepository = rmpTaskRepository;
        this.emailService = emailService;
        this.imsClientFactory = imsClientFactory;
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
            updatedTask.setId(existingTask.getId());
            RMPTaskStatus updatedTaskStatus = updatedTask.getStatus();
            boolean statusChanged = !updatedTask.getStatus().equals(existingTask.getStatus());
            RMPTask savedTask = rmpTaskRepository.save(updatedTask);
            if (statusChanged) {
                notifyStatusChange(savedTask, updatedTaskStatus.getLabel());
            }
            return savedTask;
        });
    }

    public boolean deleteTask(long id) {
        if (rmpTaskRepository.existsById(id)) {
            rmpTaskRepository.deleteById(id);
            return true;
        }
        return false;
    }

    public Optional<RMPTask> updateTaskStatus(long id, String newStatus) throws BusinessServiceException {
        Optional<RMPTask> taskOptional = rmpTaskRepository.findById(id);
        if (taskOptional.isPresent()) {
            RMPTask task = taskOptional.get();
            RMPTaskStatus currentStatus = task.getStatus();
            if (currentStatus.equals(RMPTaskStatus.valueOf(newStatus))) {
                throw new BusinessServiceException(String.format("Unable to update the RMP task status. The status has already been %s", newStatus));
            }
            task.setStatus(RMPTaskStatus.fromLabel(newStatus));
            RMPTask savedTask = rmpTaskRepository.save(task);
            notifyStatusChange(savedTask, newStatus);
            return Optional.of(savedTask);
        }
        throw new ResourceNotFoundException(String.format("RMP task %s not found", id));
    }

    private void notifyStatusChange(RMPTask task, String newStatus) {
        String currentUser = SecurityUtil.getUsername();
        Collection<User> recipients = new ArrayList<>();
        if (!currentUser.equals(task.getAssignee())) {
            recipients.add(imsClientFactory.getClient().getUserDetails(task.getAssignee()));
        }
        if (!currentUser.equals(task.getReporter())) {
            recipients.add(imsClientFactory.getClient().getUserDetails(task.getReporter()));
        }
        emailService.sendRMPTaskStatusChangeNotification(task.getId(), task.getSummary(), newStatus, recipients);
    }
}
