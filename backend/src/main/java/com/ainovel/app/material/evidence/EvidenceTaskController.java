package com.ainovel.app.material.evidence;

import static com.ainovel.app.material.evidence.EvidenceTaskDtos.*;

import com.ainovel.app.common.CurrentUserResolver;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/v1/material-evidence/tasks")
public class EvidenceTaskController {
    private final EvidenceTaskService tasks;
    private final CurrentUserResolver users;

    public EvidenceTaskController(EvidenceTaskService tasks, CurrentUserResolver users) {
        this.tasks = tasks;
        this.users = users;
    }

    @PostMapping("/preview")
    public Preview preview(
            @AuthenticationPrincipal UserDetails user, @Valid @RequestBody Request request) {
        return tasks.preview(users.require(user), request);
    }

    @PostMapping
    public Task submit(
            @AuthenticationPrincipal UserDetails user, @Valid @RequestBody Submit request) {
        return tasks.submit(users.require(user), request);
    }

    @GetMapping
    public List<Task> history(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(required = false) UUID manuscript) {
        return tasks.history(users.require(user), manuscript);
    }

    @GetMapping("/{id}")
    public Task task(@AuthenticationPrincipal UserDetails user, @PathVariable String id) {
        return tasks.task(users.require(user), id);
    }

    @PostMapping("/{id}/cancel")
    public Task cancel(@AuthenticationPrincipal UserDetails user, @PathVariable String id) {
        return tasks.cancel(users.require(user), id);
    }

    @PostMapping("/{id}/resume")
    public Task resume(@AuthenticationPrincipal UserDetails user, @PathVariable String id) {
        return tasks.resume(users.require(user), id);
    }
}
