package org.ihtsdo.authoringservices.repository;

import org.ihtsdo.authoringservices.domain.TaskStatus;
import org.ihtsdo.authoringservices.domain.TaskType;
import org.ihtsdo.authoringservices.entity.Project;
import org.ihtsdo.authoringservices.entity.Task;
import org.springframework.data.repository.CrudRepository;

import java.util.Collection;
import java.util.List;

public interface TaskRepository extends CrudRepository<Task, String> {

    List<Task> findByProjectAndStatusNotInOrderByUpdatedDateDesc(Project project, Collection<TaskStatus> excludedStatuses);

    List<Task> findByProjectInAndAssigneeAndStatusNotInOrderByUpdatedDateDesc(Collection<Project> projects, String assignee, Collection<TaskStatus> excludedStatuses);

    List<Task> findByProjectInAndAssigneeAndTypeNotInAndStatusNotInOrderByUpdatedDateDesc(Collection<Project> projects, String assignee, Collection<TaskType> excludedTaskTypes, Collection<TaskStatus> excludedStatuses);

    List<Task> findByProjectInAndAssigneeAndTypeAndStatusNotInOrderByUpdatedDateDesc(Collection<Project> projects, String assignee, TaskType type, Collection<TaskStatus> excludedStatuses);

    List<Task> findByProjectInAndAssigneeNotAndStatusInOrderByUpdatedDateDesc(Collection<Project> projects, String assignee, Collection<TaskStatus> statuses);

    List<Task> findByProjectAndStatus(Project project, TaskStatus status);

    List<Task> findByNameContaining(String name);

    List<Task> findByProject(Project project);
}