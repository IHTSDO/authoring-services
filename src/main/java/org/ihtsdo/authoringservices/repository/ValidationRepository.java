package org.ihtsdo.authoringservices.repository;

import org.ihtsdo.authoringservices.entity.Validation;
import org.springframework.data.repository.CrudRepository;

import java.util.Collection;
import java.util.List;

public interface ValidationRepository extends CrudRepository<Validation, Long> {

    Validation findByBranchPath(String branchPath);

    List<Validation> findAllByBranchPathIn(Collection<String> branchPaths);

    Validation findByRunId(Long runId);
}
