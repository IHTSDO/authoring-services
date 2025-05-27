package org.ihtsdo.authoringservices.rest;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.ihtsdo.authoringservices.domain.User;
import org.ihtsdo.authoringservices.entity.Comment;
import org.ihtsdo.authoringservices.entity.RMPTask;
import org.ihtsdo.authoringservices.service.CommentService;
import org.ihtsdo.authoringservices.service.EmailService;
import org.ihtsdo.authoringservices.service.RMPTaskService;
import org.ihtsdo.authoringservices.service.client.IMSClientFactory;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Tag(name = "RMP Tasks")
@RestController
@RequestMapping(value = "/rmp-tasks", produces = {MediaType.APPLICATION_JSON_VALUE})
public class RMPTaskController {

    private final RMPTaskService rmpTaskService;

    private final CommentService commentService;

    private final EmailService emailService;

    private final IMSClientFactory imsClientFactory;

    @Autowired
    public RMPTaskController(RMPTaskService rmpTaskService, CommentService commentService, EmailService emailService, IMSClientFactory imsClientFactory) {
        this.rmpTaskService = rmpTaskService;
        this.commentService = commentService;
        this.emailService = emailService;
        this.imsClientFactory = imsClientFactory;
    }

    @GetMapping
    public Page<RMPTask> getRmpTasks(@RequestParam(value = "country", required = false) String country,
                                     @RequestParam(value = "reporter", required = false) String reporter,
                                     Pageable page) {
        return rmpTaskService.findTasks(country, reporter, ControllerHelper.setPageDefaults(page));
    }

    @GetMapping("/search")
    public Page<RMPTask> searchRmpTasks(@RequestParam(value = "country", required = false) String country,
                                     @RequestParam(value = "criteria", required = false) String criteria,
                                     Pageable page) {
        return rmpTaskService.searchTasks(country, criteria, ControllerHelper.setPageDefaults(page));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RMPTask> getRMPTaskById(@PathVariable long id) {
        return ResponseEntity.of(rmpTaskService.getTaskById(id));
    }

    @PostMapping
    public RMPTask createRMPTask(@RequestBody RMPTask rmpTask) {
        return rmpTaskService.createTask(rmpTask);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RMPTask> updateRMPTask(@PathVariable long id, @RequestBody RMPTask rmpTask) {
        return ResponseEntity.of(rmpTaskService.updateTask(id, rmpTask));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<RMPTask> updateRMPTaskStatus(@PathVariable long id, @RequestParam String status) {
        return ResponseEntity.of(rmpTaskService.updateTaskStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRMPTask(@PathVariable long id) {
        if (rmpTaskService.deleteTask(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/comment")
    public ResponseEntity<List<Comment>> findCommentByRmpTask(@PathVariable long id) {
        RMPTask rmpTask = new RMPTask();
        rmpTask.setId(id);
        List<Comment> comments = commentService.findCommentByRmpTask(rmpTask);
        return ResponseEntity.ok(comments);
    }

    @PostMapping("/{id}/comment")
    public ResponseEntity<Comment> addCommentToRmpTask(@PathVariable long id, @RequestBody Comment comment) {
        Optional<RMPTask> rmpTaskOptional = rmpTaskService.getTaskById(id);
        if (rmpTaskOptional.isPresent()) {
            RMPTask rmpTask = rmpTaskOptional.get();
            comment.setRmpTask(rmpTask);
            comment.setUser(SecurityUtil.getUsername());
            Comment savedComment = commentService.saveComment(comment);
            notifyCommentAdd(comment, rmpTask);
            return ResponseEntity.ok(savedComment);
        }
        return ResponseEntity.notFound().build();
    }

    private void notifyCommentAdd(Comment comment, RMPTask rmpTask) {
        String currentUser = SecurityUtil.getUsername();
        Collection<User> recipients = new ArrayList<>();
        if (rmpTask.getAssignee() != null && !currentUser.equals(rmpTask.getAssignee())) {
            recipients.add(imsClientFactory.getClient().getUserDetails(rmpTask.getAssignee()));
        }
        if (rmpTask.getReporter() != null && !currentUser.equals(rmpTask.getReporter())) {
            recipients.add(imsClientFactory.getClient().getUserDetails(rmpTask.getReporter()));
        }
        this.emailService.sendRMPTaskCommentAddNotification(rmpTask.getId(), rmpTask.getSummary(), comment.getBody(), recipients);
    }
}
