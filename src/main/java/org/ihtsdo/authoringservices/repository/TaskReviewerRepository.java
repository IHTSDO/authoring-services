package org.ihtsdo.authoringservices.repository;

import org.ihtsdo.authoringservices.entity.Task;
import org.ihtsdo.authoringservices.entity.TaskReviewer;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface TaskReviewerRepository extends CrudRepository<TaskReviewer, Long> {

    List<TaskReviewer> findByTask(Task task);

}