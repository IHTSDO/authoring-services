package org.ihtsdo.authoringservices.service;

import org.ihtsdo.authoringservices.entity.RMPTask;
import org.ihtsdo.authoringservices.repository.RMPTaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class RMPTaskService {

    private final RMPTaskRepository rmpTaskRepository;

    @Autowired
    public RMPTaskService(RMPTaskRepository rmpTaskRepository) {
        this.rmpTaskRepository = rmpTaskRepository;
    }

    public List<RMPTask> getAllTasks() {
        Iterable<RMPTask> iterable = rmpTaskRepository.findAll();
        return StreamSupport.stream(iterable.spliterator(), false).collect(Collectors.toList());
    }

    public Optional<RMPTask> getTaskById(long id) {
        return rmpTaskRepository.findById(id);
    }

    public RMPTask createTask(RMPTask rmpTask) {
        return rmpTaskRepository.save(rmpTask);
    }

    public Optional<RMPTask> updateTask(long id, RMPTask updatedTask) {
        return rmpTaskRepository.findById(id).map(existingTask -> {
            updatedTask.setId(existingTask.getId());
            return rmpTaskRepository.save(updatedTask);
        });
    }

    public boolean deleteTask(long id) {
        if (rmpTaskRepository.existsById(id)) {
            rmpTaskRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
