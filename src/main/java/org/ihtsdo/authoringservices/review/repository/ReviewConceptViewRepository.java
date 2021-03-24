package org.ihtsdo.authoringservices.review.repository;

import org.ihtsdo.authoringservices.review.domain.Branch;
import org.ihtsdo.authoringservices.review.domain.ReviewConceptView;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ReviewConceptViewRepository extends CrudRepository<ReviewConceptView, Long> {

	List<ReviewConceptView> findByBranchAndUsernameOrderByViewDateAsc(Branch branch, String username);

}
