package org.ihtsdo.authoringservices.review.repository;

import org.ihtsdo.authoringservices.review.domain.Branch;
import org.springframework.data.repository.CrudRepository;

public interface BranchRepository extends CrudRepository<Branch, Long> {

	Branch findOneByProjectAndTask(String project, String task);
}
