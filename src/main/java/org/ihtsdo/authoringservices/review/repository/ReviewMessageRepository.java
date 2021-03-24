package org.ihtsdo.authoringservices.review.repository;

import org.ihtsdo.authoringservices.review.domain.Branch;
import org.ihtsdo.authoringservices.review.domain.ReviewMessage;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ReviewMessageRepository extends CrudRepository<ReviewMessage, Long> {

	List<ReviewMessage> findByBranch(Branch branch);

}
