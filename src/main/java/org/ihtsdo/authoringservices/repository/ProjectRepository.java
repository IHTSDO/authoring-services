package org.ihtsdo.authoringservices.repository;

import org.ihtsdo.authoringservices.entity.Project;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ProjectRepository extends CrudRepository<Project, String> {

    List<Project> findByExtensionBase(String extensionBase);

}