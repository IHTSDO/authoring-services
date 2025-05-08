package org.ihtsdo.authoringservices.rest;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.ihtsdo.authoringservices.entity.RMPTask;
import org.ihtsdo.authoringservices.service.RMPTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "RMP Tasks")
@RestController
@RequestMapping(value = "/rmp-tasks", produces = {MediaType.APPLICATION_JSON_VALUE})
public class RMPTaskController {

    private final RMPTaskService rmpTaskService;

    @Autowired
    public RMPTaskController(RMPTaskService rmpTaskService) {
        this.rmpTaskService = rmpTaskService;
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRMPTask(@PathVariable long id) {
        if (rmpTaskService.deleteTask(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
