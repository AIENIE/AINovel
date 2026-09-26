package com.ainovel.app.material;

import com.ainovel.app.material.dto.*;
import com.ainovel.app.admin.ops.OpsRecordFileSink;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;

/** Full local administrator governance is deliberately separate from owner APIs. */
@Service
public class MaterialAdminService {
    private final MaterialService materials;
    private final MaterialAdminReadRepository readRepository;
    private final MaterialDuplicateService duplicates;
    private final OpsRecordFileSink audit;
    public MaterialAdminService(MaterialService materials, MaterialAdminReadRepository readRepository,
                                MaterialDuplicateService duplicates, OpsRecordFileSink audit) {
        this.materials = materials; this.readRepository = readRepository;
        this.duplicates = duplicates; this.audit = audit;
    }
    static void requireAdmin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getAuthorities().stream()
                .noneMatch(a -> "AUTH_LOCAL_ADMIN".equals(a.getAuthority()))) throw new AccessDeniedException("LOCAL_ADMIN_REQUIRED");
    }
    @Transactional(readOnly = true)
    public MaterialAdminReadRepository.Page pending(int page, int size) {
        requireAdmin();
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid page or size (size: 1-100)");
        }
        return readRepository.pending(page, size);
    }
    @Transactional
    public MaterialDto review(UUID id, String action, MaterialReviewRequest request) {
        requireAdmin(); var result = materials.reviewInternal(id, action, request); record("material." + action, id); return result;
    }
    @Transactional
    public MaterialDto merge(MaterialMergeRequest request) {
        requireAdmin(); var result = materials.mergeInternal(request, true); record("material.merge", result.id()); return result;
    }
    @Transactional(readOnly = true)
    public List<Map<String,Object>> citations(UUID id) { requireAdmin(); return materials.citationsInternal(id, true); }
    public MaterialDuplicateService.Job findDuplicates() { requireAdmin(); var job = duplicates.start(); record("material.duplicates.start", job.id()); return job; }
    public MaterialDuplicateService.Job duplicateStatus(UUID id) { requireAdmin(); return duplicates.status(id); }
    public MaterialDuplicateService.ResultPage duplicateResults(UUID id, int page, int size) { requireAdmin(); return duplicates.results(id, page, size); }
    public MaterialDuplicateService.Job cancelDuplicates(UUID id) { requireAdmin(); var result = duplicates.cancel(id); record("material.duplicates.cancel", id); return result; }
    private void record(String action, UUID id) { audit.appendAudit(Map.of("category", "admin", "action", action,
            "actor", "admin", "targetType", "material", "targetId", id.toString(), "result", "SUCCESS", "severity", "INFO")); }
}
