package org.ihtsdo.authoringservices.repository;

import org.ihtsdo.authoringservices.entity.RMPTask;
import org.ihtsdo.authoringservices.entity.RMPTaskAttachment;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface RMPTaskAttachmentRepository extends CrudRepository<RMPTaskAttachment, Long> {

    List<RMPTaskAttachment> findByRmpTaskOrderByCreatedDateAsc(RMPTask rmpTask);

    Optional<RMPTaskAttachment> findByIdAndRmpTask_Id(long id, long rmpTaskId);

}
