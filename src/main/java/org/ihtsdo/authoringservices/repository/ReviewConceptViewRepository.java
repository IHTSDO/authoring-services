package org.ihtsdo.authoringservices.repository;

import org.ihtsdo.authoringservices.entity.Branch;
import org.ihtsdo.authoringservices.entity.ReviewConceptView;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ReviewConceptViewRepository extends CrudRepository<ReviewConceptView, Long> {

	List<ReviewConceptView> findByBranchAndUsernameOrderByViewDateAsc(Branch branch, String username);

}
