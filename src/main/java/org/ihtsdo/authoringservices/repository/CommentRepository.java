package org.ihtsdo.authoringservices.repository;

import org.ihtsdo.authoringservices.entity.Comment;
import org.ihtsdo.authoringservices.entity.RMPTask;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface CommentRepository extends CrudRepository<Comment, Long> {
    List<Comment> findByRmpTaskOrderByCreatedDateAsc(RMPTask rmpTask);

}
