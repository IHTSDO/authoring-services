package org.ihtsdo.authoringservices.repository;

import org.ihtsdo.authoringservices.entity.Project;
import org.springframework.data.repository.CrudRepository;

public interface ProjectRepository extends CrudRepository<Project, String> {

}