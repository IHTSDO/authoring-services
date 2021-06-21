package org.ihtsdo.authoringservices.repository;

import org.ihtsdo.authoringservices.entity.Branch;
import org.ihtsdo.authoringservices.entity.ReviewMessage;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ReviewMessageRepository extends CrudRepository<ReviewMessage, Long> {

	List<ReviewMessage> findByBranch(Branch branch);

}
