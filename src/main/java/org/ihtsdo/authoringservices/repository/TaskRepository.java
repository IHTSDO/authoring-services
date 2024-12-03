package org.ihtsdo.authoringservices.repository;

import org.ihtsdo.authoringservices.entity.Task;
import org.springframework.data.repository.CrudRepository;

public interface TaskRepository extends CrudRepository<Task, String> {

}