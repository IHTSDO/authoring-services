package org.ihtsdo.authoringservices.repository;

import org.ihtsdo.authoringservices.entity.Branch;
import org.springframework.data.repository.CrudRepository;

public interface BranchRepository extends CrudRepository<Branch, Long> {

	Branch findOneByProjectAndTask(String project, String task);
}
