package org.ihtsdo.authoringservices.repository;

import org.ihtsdo.authoringservices.entity.RVFFailureJiraAssociation;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface RVFFailureJiraAssociationRepository extends CrudRepository<RVFFailureJiraAssociation, Long> {
	List<RVFFailureJiraAssociation> findByReportRunId(Long reportRunId);
}
