package org.ihtsdo.authoringservices.service;

import jakarta.transaction.Transactional;
import org.ihtsdo.authoringservices.entity.Comment;
import org.ihtsdo.authoringservices.entity.RMPTask;
import org.ihtsdo.authoringservices.repository.CommentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CommentService {

    private final CommentRepository commentRepository;

    @Autowired
    public CommentService(CommentRepository commentRepository) {
        this.commentRepository = commentRepository;
    }

    // Create or Update a Comment
    public Comment saveComment(Comment comment) {
        return commentRepository.save(comment);
    }

    @Transactional
    public boolean deleteCommentById(Long id) {
        Optional<Comment> commentOptional = commentRepository.findById(id);
        if (commentOptional.isPresent()) {
            commentRepository.delete(commentOptional.get());
            return true;
        }
        return false;
    }

    public List<Comment> findCommentByRmpTask(RMPTask rmpTask) {
        return commentRepository.findByRmpTaskOrderByCreatedDateAsc(rmpTask);
    }

    public void deleteCommentsByRmpTask(RMPTask rmpTask) {
        commentRepository.deleteAll(commentRepository.findByRmpTaskOrderByCreatedDateAsc(rmpTask));
    }
}
