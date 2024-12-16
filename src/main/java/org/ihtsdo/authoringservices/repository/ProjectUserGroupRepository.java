package org.ihtsdo.authoringservices.repository;

import org.ihtsdo.authoringservices.entity.ProjectUserGroup;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ProjectUserGroupRepository extends CrudRepository<ProjectUserGroup, Long> {
    List<ProjectUserGroup> findByNameIn(List<String> groups);
}
