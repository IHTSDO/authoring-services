package org.ihtsdo.authoringservices.repository;

import org.ihtsdo.authoringservices.entity.RMPTask;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface RMPTaskPagingAndSortingRepository extends PagingAndSortingRepository<RMPTask, Long> {
}
