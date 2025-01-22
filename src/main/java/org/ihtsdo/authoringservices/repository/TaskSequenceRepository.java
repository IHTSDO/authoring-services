package org.ihtsdo.authoringservices.repository;

import org.ihtsdo.authoringservices.entity.Project;
import org.ihtsdo.authoringservices.entity.TaskSequence;
import org.springframework.data.repository.CrudRepository;

public interface TaskSequenceRepository extends CrudRepository<TaskSequence, Long> {
    TaskSequence findOneByProject(Project project);

}
