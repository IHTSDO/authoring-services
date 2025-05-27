package org.ihtsdo.authoringservices.repository;

import org.ihtsdo.authoringservices.entity.RMPTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface RMPTaskRepository extends JpaRepository<RMPTask, Long>, PagingAndSortingRepository<RMPTask, Long>, QuerydslPredicateExecutor<RMPTask>, JpaSpecificationExecutor<RMPTask> {
    Page<RMPTask> findAllByCountry(String country, Pageable pageable);

    Page<RMPTask> findAllByReporter(String reporter, Pageable pageable);

    Page<RMPTask> findAllByCountryAndReporter(String country, String reporter, Pageable pageable);
}
