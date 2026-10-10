package com.ainovel.app.material.evidence;

import static com.ainovel.app.material.evidence.EvidenceDtos.*;

import com.ainovel.app.common.CurrentUserResolver;
import com.ainovel.app.material.evidence.EvidenceDtos.Package;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/v1/material-evidence")
public class MaterialEvidenceController {
    private final MaterialEvidenceService service;
    private final CurrentUserResolver users;
    private final MaterialSearchService search;
    private final MaterialConfirmationService confirmations;
    private final MaterialHintService hints;

    public MaterialEvidenceController(
            MaterialEvidenceService service,
            CurrentUserResolver users,
            MaterialSearchService search,
            MaterialConfirmationService confirmations,
            MaterialHintService hints) {
        this.service = service;
        this.users = users;
        this.search = search;
        this.confirmations = confirmations;
        this.hints = hints;
    }

    @PostMapping("/search")
    public Results search(
            @AuthenticationPrincipal UserDetails user, @Valid @RequestBody Search request) {
        return search.search(users.require(user), request);
    }

    @PostMapping("/hints")
    public Results hints(
            @AuthenticationPrincipal UserDetails user, @Valid @RequestBody Hint request) {
        return hints.hints(users.require(user), request);
    }

    @PostMapping("/tasks/{task}/confirm")
    public Annotation confirm(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable String task,
            @Valid @RequestBody ConfirmCandidate request) {
        return confirmations.confirm(users.require(user), task, request);
    }

    @GetMapping("/revisions/{revision}/annotations")
    public List<Annotation> annotations(
            @AuthenticationPrincipal UserDetails user, @PathVariable String revision) {
        return confirmations.list(users.require(user), revision);
    }

    @GetMapping("/chunks/{id}")
    public Hit open(@AuthenticationPrincipal UserDetails user, @PathVariable String id) {
        return service.open(users.require(user), id);
    }

    @GetMapping("/works/{story}/settings")
    public Settings settings(@AuthenticationPrincipal UserDetails user, @PathVariable UUID story) {
        return service.settings(users.require(user), story);
    }

    @PutMapping("/works/{story}/settings")
    public Settings settings(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable UUID story,
            @Valid @RequestBody SettingsWrite request) {
        return service.saveSettings(users.require(user), story, request);
    }

    @GetMapping("/manuscripts/{manuscript}/scenes/{scene}/package")
    public Package references(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable UUID manuscript,
            @PathVariable String scene) {
        return service.scenePackage(users.require(user), manuscript, scene);
    }

    @PutMapping("/manuscripts/{manuscript}/scenes/{scene}/package")
    public Package references(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable UUID manuscript,
            @PathVariable String scene,
            @Valid @RequestBody PackageWrite request) {
        return service.savePackage(users.require(user), manuscript, scene, request);
    }

    @GetMapping("/statuses")
    public List<SourceStatus> statuses(@AuthenticationPrincipal UserDetails user) {
        return service.statuses(users.require(user));
    }

    @GetMapping("/sources/{material}/revisions")
    public List<SourceRevision> revisions(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable UUID material,
            @RequestParam(defaultValue = "0") int page) {
        return service.revisions(users.require(user), material, page);
    }

    @GetMapping("/revisions/{revision}/raw")
    public RawRevision rawRevision(
            @AuthenticationPrincipal UserDetails user, @PathVariable String revision) {
        return service.rawRevision(users.require(user), revision);
    }

    @GetMapping("/duplicates")
    public List<Duplicate> duplicates(@AuthenticationPrincipal UserDetails user) {
        return service.duplicates(users.require(user));
    }

    @PostMapping("/entities")
    public Entity entity(
            @AuthenticationPrincipal UserDetails user, @Valid @RequestBody EntityWrite request) {
        return service.createEntity(users.require(user), request);
    }

    @GetMapping("/entities")
    public List<Entity> entities(
            @AuthenticationPrincipal UserDetails user, @RequestParam(defaultValue = "0") int page) {
        return service.entities(users.require(user), page);
    }

    @GetMapping("/entities/{id}/sources")
    public List<Hit> entitySources(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable String id,
            @RequestParam UUID story,
            @RequestParam(defaultValue = "0") int page) {
        return service.entitySources(users.require(user), story, id, page);
    }

    @PostMapping("/manuscripts/{manuscript}/scenes/{scene}/citations")
    public Map<String, String> citation(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable UUID manuscript,
            @PathVariable String scene,
            @Valid @RequestBody CitationWrite request) {
        return Map.of("id", service.citation(users.require(user), manuscript, scene, request));
    }

    @GetMapping("/manuscripts/{manuscript}/scenes/{scene}/citation-body")
    public CitationBody citationBody(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable UUID manuscript,
            @PathVariable String scene) {
        return service.citationBody(users.require(user), manuscript, scene);
    }

    @GetMapping("/manuscripts/{manuscript}/citations")
    public List<Map<String, Object>> citations(
            @AuthenticationPrincipal UserDetails user, @PathVariable UUID manuscript) {
        return service.citations(users.require(user), manuscript);
    }
}
