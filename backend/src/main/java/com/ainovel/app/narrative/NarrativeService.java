package com.ainovel.app.narrative;

import com.ainovel.app.aioperation.*;
import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.user.User;
import com.ainovel.app.v2.V2VersionPersistenceService;
import com.ainovel.app.v2.model.V2ManuscriptBranch;
import com.ainovel.app.v2.repo.V2ManuscriptBranchRepository;
import com.ainovel.app.v2.repo.V2ManuscriptVersionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static com.ainovel.app.narrative.NarrativeDtos.*;
import static com.ainovel.app.narrative.NarrativeText.*;

@Service
public class NarrativeService {
    public static final String PROMPT_VERSION = "narrative-state-p11-v1";
    private final ManuscriptRepository manuscripts;
    private final V2ManuscriptBranchRepository branches;
    private final V2ManuscriptVersionRepository versions;
    private final V2VersionPersistenceService versionService;
    private final NarrativeLedgerRepository ledgers;
    private final NarrativeApprovalRepository approvals;
    private final NarrativeExtractionRepository extractions;
    private final NarrativeRecordRepository records;
    private final NarrativeCommitRepository commits;
    private final AiOperationRepository operations;
    private final CharacterCardRepository characters;
    private final ResourceAccessGuard access;
    private final ObjectMapper json;
    private final Validator validator;

    public NarrativeService(ManuscriptRepository manuscripts, V2ManuscriptBranchRepository branches,
                            V2ManuscriptVersionRepository versions, V2VersionPersistenceService versionService,
                            NarrativeLedgerRepository ledgers, NarrativeApprovalRepository approvals,
                            NarrativeExtractionRepository extractions, NarrativeRecordRepository records,
                            NarrativeCommitRepository commits, AiOperationRepository operations,
                            CharacterCardRepository characters, ResourceAccessGuard access,
                            ObjectMapper json, Validator validator) {
        this.manuscripts = manuscripts; this.branches = branches; this.versions = versions;
        this.versionService = versionService; this.ledgers = ledgers; this.approvals = approvals;
        this.extractions = extractions; this.records = records; this.commits = commits;
        this.operations = operations; this.characters = characters; this.access = access;
        this.json = json; this.validator = validator;
    }

    @Transactional
    public Approved approve(User user, UUID manuscriptId, UUID branchId, ApprovalRequest request, String key) {
        Scope scope = scope(user, manuscriptId, branchId);
        key = key(key);
        String requestHash = hash(write(request));
        NarrativeApproval replay = approvals.findByLedgerBranchIdAndIdempotencyKey(branchId, key).orElse(null);
        if (replay != null) {
            if (!requestHash.equals(replay.getRequestHash())) throw conflict("NARRATIVE_IDEMPOTENCY_CONFLICT");
            NarrativeExtraction extraction = extractions.findByApprovalId(replay.getId()).orElseThrow();
            return new Approved(replay.getId(), extraction.getId(), extraction.getOperationId());
        }
        requireCurrent(scope);
        reconcile(scope);
        checkVersions(scope, request.expectedManuscriptVersion(), request.expectedCanonRevision());
        Position position = positions(tree(scope.manuscript().getOutline().getContentJson())).get(request.sceneId());
        if (position == null) throw invalid("NARRATIVE_SCENE_MISSING");
        List<Block> blocks = blocks(section(scope.manuscript().getSectionsJson(), request.sceneId()));
        if (blocks.isEmpty()) throw invalid("NARRATIVE_EMPTY_SCENE");
        UUID versionId = (UUID) versionService.createVersion(scope.manuscript(), user,
                Map.of("snapshotType", "narrative", "label", "narrative:" + request.sceneId())).get("id");
        var version = versions.findById(versionId).orElseThrow();
        if (!version.getBranch().getId().equals(branchId)) throw conflict("NARRATIVE_BRANCH_CHANGED");
        version.setNarrativeProtected(true);
        NarrativeApproval approval = new NarrativeApproval();
        approval.setLedger(scope.ledger()); approval.setVersion(version); approval.setSceneId(request.sceneId());
        approval.setConfirmedBy(user.getId()); approval.setIdempotencyKey(key); approval.setRequestHash(requestHash);
        approval.setTextHash(textHash(blocks)); approval.setBlocksJson(write(blocks));
        approval.setPositionJson(write(position)); approval.setConfirmedAt(Instant.now());
        approvals.saveAndFlush(approval);
        NarrativeExtraction extraction = new NarrativeExtraction();
        extraction.setApproval(approval); extraction.setBaseCanonRevision(scope.ledger().getRevision());
        extraction.setPromptVersion(PROMPT_VERSION); extraction.setCreatedAt(Instant.now());
        List<InputRecord> prior = branchRecords(branchId).stream()
                .filter(r -> active(r, scope.ledger().getRevision()))
                .filter(r -> position(r.getExtraction().getApproval()).index() < position.index())
                .map(r -> new InputRecord(r.getId(), assertion(r))).toList();
        // Names/IDs only: character descriptions, planning and Lorebook can contain future secrets.
        var entities = characters.findByStory(scope.manuscript().getOutline().getStory()).stream()
                .map(c -> Map.of("id", c.getId(), "name", Objects.toString(c.getName(), ""))).toList();
        extraction.setInputJson(write(new ExtractionInput(null, approval.getId(), scope.manuscript().getOutline().getStory().getId(),
                blocks, prior, entities)));
        extractions.saveAndFlush(extraction);
        return new Approved(approval.getId(), extraction.getId(), null);
    }

    @Transactional
    public void attachOperation(UUID extractionId, UUID operationId) {
        NarrativeExtraction extraction = extractions.findById(extractionId).orElseThrow();
        if (extraction.getOperationId() != null && !extraction.getOperationId().equals(operationId)) throw conflict("NARRATIVE_OPERATION_CONFLICT");
        extraction.setOperationId(operationId);
    }

    @Transactional
    public ExtractionInput input(User user, UUID extractionId, UUID operationId) {
        NarrativeExtraction extraction = extractions.findById(extractionId).orElseThrow(() -> missing());
        Scope scope = scope(user, extraction.getApproval().getLedger().getBranch().getManuscript().getId(), extraction.getApproval().getLedger().getBranchId());
        requireOperation(extraction, operationId);
        reconcile(scope);
        if (stale(scope, extraction)) throw conflict("NARRATIVE_SOURCE_STALE");
        ExtractionInput input = read(extraction.getInputJson(), ExtractionInput.class);
        return new ExtractionInput(extraction.getId(), input.approvalId(), input.storyId(), input.blocks(), input.previousRecords(), input.entities());
    }

    @Transactional
    public boolean hasResult(User user, UUID extractionId, UUID operationId) {
        NarrativeExtraction extraction = extractions.findById(extractionId).orElseThrow(() -> missing());
        scope(user, extraction.getApproval().getLedger().getBranch().getManuscript().getId(), extraction.getApproval().getLedger().getBranchId());
        requireOperation(extraction, operationId);
        return extraction.getCandidatesJson() != null || extraction.getError() != null;
    }

    @Transactional
    public void storeResult(User user, UUID extractionId, UUID operationId, String content, Object usage, String model) {
        NarrativeExtraction extraction = extractions.findById(extractionId).orElseThrow(() -> missing());
        Scope scope = scope(user, extraction.getApproval().getLedger().getBranch().getManuscript().getId(), extraction.getApproval().getLedger().getBranchId());
        requireOperation(extraction, operationId);
        // A completed business result is durable even if the transport's final progress update was lost.
        if (extraction.getCandidatesJson() != null || extraction.getError() != null) return;
        extraction.setUsageJson(write(usage)); extraction.setModel(model);
        List<Candidate> candidates = new ArrayList<>();
        JsonNode root;
        try {
            String value = content.strip();
            if (value.startsWith("```json") && value.endsWith("```")) value = value.substring(7, value.length() - 3).strip();
            root = json.readTree(value);
            if (root == null || !root.path("candidates").isArray() || root.path("candidates").size() > 100) {
                throw new IllegalArgumentException();
            }
        } catch (Exception ex) {
            extraction.setError("NARRATIVE_INVALID_MODEL_OUTPUT");
            return;
        }
        Set<UUID> ids = characterIds(scope);
        int index = 0;
        for (JsonNode item : root.path("candidates")) {
            String id = "c" + (++index);
            Assertion assertion = null;
            String error = null;
            try {
                assertion = json.treeToValue(item, Assertion.class);
                // Clear model-suggested replacements even on candidates whose evidence needs repair.
                if (assertion != null) assertion = new Assertion(assertion.subject(), assertion.characterId(), assertion.statement(),
                        assertion.kind(), assertion.holderCharacterId(), assertion.worldTime(), assertion.uncertainty(), assertion.evidence(), null);
                assertion = validate(assertion, extraction.getApproval(), ids);
            } catch (Exception ex) {
                error = ex instanceof ApiStatusException ? ex.getMessage() : "NARRATIVE_INVALID_ASSERTION";
            }
            candidates.add(new Candidate(id, assertion, error));
        }
        extraction.setCandidatesJson(write(candidates));
        reconcile(scope);
    }

    @Transactional
    public StateView state(User user, UUID manuscriptId, UUID branchId, UUID sceneId, UUID characterId,
                           Kind kind, String status, Long revision) {
        Scope scope = scope(user, manuscriptId, branchId);
        reconcile(scope);
        long at = revision == null ? scope.ledger().getRevision() : revision;
        if (at < 0 || at > scope.ledger().getRevision()) throw invalid("NARRATIVE_INVALID_REVISION");
        var views = branchRecords(branchId).stream().filter(r -> r.getCreatedRevision() <= at)
                .map(r -> view(r, at))
                .filter(r -> sceneId == null || sceneId.equals(r.sceneId()))
                .filter(r -> characterId == null || characterId.equals(r.assertion().characterId()) || characterId.equals(r.assertion().holderCharacterId()))
                .filter(r -> kind == null || r.assertion().kind() == kind)
                .filter(r -> status == null || status.equals(r.status())).toList();
        var batches = extractions.findByApprovalLedgerBranchIdOrderByCreatedAtDesc(branchId).stream()
                .filter(e -> sceneId == null || sceneId.equals(e.getApproval().getSceneId()))
                .map(e -> view(scope, e)).toList();
        return new StateView(at, scope.manuscript().getVersion(), branchId, views, batches);
    }

    @Transactional
    public ExtractionView extraction(User user, UUID manuscriptId, UUID branchId, UUID id) {
        Scope scope = scope(user, manuscriptId, branchId);
        reconcile(scope);
        return view(scope, requireExtraction(scope, id));
    }

    @Transactional(readOnly=true)
    public EvidenceView evidence(User user, UUID manuscriptId, UUID branchId, UUID id) {
        access.requireOwnedManuscript(manuscriptId, user);
        NarrativeApproval approval = approvals.findById(id).orElseThrow(() -> missing());
        if (!approval.getLedger().getBranchId().equals(branchId)
                || !approval.getLedger().getBranch().getManuscript().getId().equals(manuscriptId)) throw missing();
        return new EvidenceView(approval.getId(), approval.getVersion().getId(), approval.getSceneId(), position(approval),
                blockList(approval), approval.getTextHash(), approval.getConfirmedAt());
    }

    @Transactional
    public ReviewResult review(User user, UUID manuscriptId, UUID branchId, UUID extractionId, ReviewRequest request, String key) {
        Scope scope = scope(user, manuscriptId, branchId);
        NarrativeExtraction extraction = requireExtraction(scope, extractionId);
        key = key(key);
        String requestHash = hash(extractionId + ":" + write(request));
        NarrativeCommit replay = commits.findByLedgerBranchIdAndIdempotencyKey(branchId, key).orElse(null);
        if (replay != null) {
            if (!requestHash.equals(replay.getRequestHash())) throw conflict("NARRATIVE_IDEMPOTENCY_CONFLICT");
            return read(replay.getDetailJson(), ReviewResult.class);
        }
        requireCurrent(scope);
        reconcile(scope);
        checkVersions(scope, request.expectedManuscriptVersion(), request.expectedCanonRevision());
        if (stale(scope, extraction)) throw conflict("NARRATIVE_SOURCE_STALE");
        if (extraction.getReviewJson() != null) throw conflict("NARRATIVE_ALREADY_REVIEWED");
        requireOperation(extraction, extraction.getOperationId());
        if (extraction.getCandidatesJson() == null || extraction.getError() != null) throw conflict("NARRATIVE_EXTRACTION_NOT_READY");
        List<Candidate> candidates = candidates(extraction);
        Map<String, Candidate> byId = new LinkedHashMap<>();
        candidates.forEach(c -> byId.put(c.id(), c));
        if (request.decisions() == null || request.additions() == null
                || request.decisions().size() != candidates.size() || request.additions().size() > 100) throw invalid("NARRATIVE_REVIEW_INCOMPLETE");
        Set<String> decided = new HashSet<>();
        List<Assertion> accepted = new ArrayList<>();
        Set<UUID> characterIds = characterIds(scope);
        for (ReviewItem item : request.decisions()) {
            Candidate candidate = byId.get(item.candidateId());
            if (candidate == null || !decided.add(item.candidateId()) || item.decision() == null) throw invalid("NARRATIVE_REVIEW_INCOMPLETE");
            if (item.decision() == Decision.ACCEPT) accepted.add(validate(item.edited() == null ? candidate.assertion() : item.edited(), extraction.getApproval(), characterIds));
        }
        for (Assertion addition : request.additions()) accepted.add(validate(addition, extraction.getApproval(), characterIds));
        List<NarrativeRecord> existing = branchRecords(branchId);
        Set<UUID> replaced = new HashSet<>();
        List<NarrativeRecord> replacements = new ArrayList<>();
        for (Assertion assertion : accepted) {
            if (assertion.supersedesId() == null) continue;
            NarrativeRecord target = existing.stream().filter(r -> r.getId().equals(assertion.supersedesId())).findFirst().orElseThrow(() -> invalid("NARRATIVE_REPLACEMENT_INVALID"));
            if (!active(target, scope.ledger().getRevision()) || !replaced.add(target.getId())) throw invalid("NARRATIVE_REPLACEMENT_INVALID");
            replacements.add(target);
        }
        long next = scope.ledger().getRevision() + 1;
        scope.ledger().setRevision(next);
        replacements.forEach(r -> r.setSupersededRevision(next));
        List<UUID> created = new ArrayList<>();
        for (Assertion assertion : accepted) {
            NarrativeRecord record = new NarrativeRecord();
            record.setExtraction(extraction); record.setAssertionJson(write(assertion));
            record.setDependencyIdsJson(write(read(extraction.getInputJson(), ExtractionInput.class).previousRecords().stream()
                    .map(InputRecord::id).filter(id -> !replaced.contains(id)).toList()));
            record.setCreatedRevision(next); record.setCreatedAt(Instant.now());
            created.add(records.saveAndFlush(record).getId());
        }
        NarrativeCommit commit = new NarrativeCommit();
        commit.setLedger(scope.ledger()); commit.setRevision(next); commit.setKind("REVIEW");
        commit.setIdempotencyKey(key); commit.setRequestHash(requestHash); commit.setAuthorId(user.getId());
        commit.setCreatedAt(Instant.now()); commit.setDetailJson("{}");
        commits.saveAndFlush(commit);
        ReviewResult result = new ReviewResult(commit.getId(), next, List.copyOf(created));
        commit.setDetailJson(write(result));
        extraction.setReviewJson(write(request));
        // Inputs that were explicitly replaced invalidate their existing dependants, not the new author's decision.
        invalidateDependants(scope, existing, replaced, next);
        return result;
    }

    @Transactional
    public void reconcileManuscript(UUID manuscriptId) {
        Manuscript manuscript = manuscripts.findByIdForUpdate(manuscriptId).orElse(null);
        if (manuscript == null) return;
        for (V2ManuscriptBranch branch : branches.findByManuscriptId(manuscriptId)) {
            ledgers.findById(branch.getId()).ifPresent(ledger -> reconcile(new Scope(manuscript, branch, ledger)));
        }
    }

    private Scope scope(User user, UUID manuscriptId, UUID branchId) {
        access.requireOwnedManuscript(manuscriptId, user);
        Manuscript manuscript = manuscripts.findByIdForUpdate(manuscriptId).orElseThrow(() -> missing());
        V2ManuscriptBranch branch = branches.findByManuscriptIdAndId(manuscriptId, branchId).orElseThrow(() -> missing());
        NarrativeLedger ledger = ledgers.findById(branchId).orElseGet(() -> {
            NarrativeLedger created = new NarrativeLedger();
            created.setBranch(branch);
            return ledgers.saveAndFlush(created);
        });
        return new Scope(manuscript, branch, ledger);
    }
    private void requireCurrent(Scope scope) {
        if (!scope.branch().getId().equals(scope.manuscript().getCurrentBranchId()) || !"active".equals(scope.branch().getStatus())) throw conflict("NARRATIVE_BRANCH_CHANGED");
    }
    private void checkVersions(Scope scope, Long manuscriptVersion, Long canonRevision) {
        if (manuscriptVersion == null || canonRevision == null
                || manuscriptVersion != scope.manuscript().getVersion() || canonRevision != scope.ledger().getRevision()) throw conflict("NARRATIVE_VERSION_CHANGED");
    }
    private NarrativeExtraction requireExtraction(Scope scope, UUID id) {
        NarrativeExtraction extraction = extractions.findById(id).orElseThrow(() -> missing());
        if (!extraction.getApproval().getLedger().getBranchId().equals(scope.ledger().getBranchId())) throw missing();
        return extraction;
    }
    private void requireOperation(NarrativeExtraction extraction, UUID operationId) {
        if (!Objects.equals(extraction.getOperationId(), operationId)) throw missing();
        AiOperationRun operation = operations.findById(operationId).orElseThrow(() -> missing());
        if (operation.getStatus() == AiOperationStatus.CANCELLED || Thread.currentThread().isInterrupted()) throw conflict("NARRATIVE_CANCELLED");
    }
    private List<NarrativeRecord> branchRecords(UUID branchId) {
        return records.findByExtractionApprovalLedgerBranchIdOrderByCreatedAtAsc(branchId);
    }
    private void reconcile(Scope scope) {
        List<NarrativeRecord> values = branchRecords(scope.ledger().getBranchId());
        Set<UUID> invalid = new HashSet<>();
        Map<UUID, Boolean> approvalMatches = new HashMap<>();
        for (NarrativeRecord record : values) {
            if (active(record, scope.ledger().getRevision()) && !approvalMatches.computeIfAbsent(record.getExtraction().getApproval().getId(),
                    id -> sourceMatches(scope, record.getExtraction().getApproval()))) invalid.add(record.getId());
        }
        for (NarrativeRecord record : values) if (!active(record, scope.ledger().getRevision())) invalid.add(record.getId());
        long next = scope.ledger().getRevision() + 1;
        List<UUID> changed = invalidateDependants(scope, values, invalid, next);
        if (changed.isEmpty()) return;
        scope.ledger().setRevision(next);
        NarrativeCommit commit = new NarrativeCommit();
        commit.setLedger(scope.ledger()); commit.setRevision(next); commit.setKind("INVALIDATE");
        commit.setCreatedAt(Instant.now()); commit.setDetailJson(write(Map.of("recordIds", changed)));
        commits.save(commit);
    }
    private List<UUID> invalidateDependants(Scope scope, List<NarrativeRecord> values, Set<UUID> invalid, long revision) {
        List<UUID> changed = new ArrayList<>();
        boolean progress;
        do {
            progress = false;
            for (NarrativeRecord record : values) {
                if (!active(record, revision)) continue;
                List<UUID> dependencies = read(record.getDependencyIdsJson(), new TypeReference<>() {});
                boolean affected = invalid.contains(record.getId()) || dependencies.stream().anyMatch(invalid::contains);
                if (affected) {
                    record.setInvalidatedRevision(revision); invalid.add(record.getId()); changed.add(record.getId()); progress = true;
                }
            }
        } while (progress);
        return changed;
    }
    private boolean stale(Scope scope, NarrativeExtraction extraction) {
        if (!sourceMatches(scope, extraction.getApproval())) return true;
        Map<UUID, NarrativeRecord> current = new HashMap<>();
        branchRecords(scope.ledger().getBranchId()).forEach(r -> current.put(r.getId(), r));
        return read(extraction.getInputJson(), ExtractionInput.class).previousRecords().stream()
                .anyMatch(input -> !current.containsKey(input.id()) || !active(current.get(input.id()), scope.ledger().getRevision()));
    }
    private boolean sourceMatches(Scope scope, NarrativeApproval approval) {
        Position current = positions(tree(scope.manuscript().getOutline().getContentJson())).get(approval.getSceneId());
        if (current == null || !current.orderHash().equals(position(approval).orderHash())) return false;
        String source = scope.manuscript().getSectionsJson();
        if (!scope.branch().getId().equals(scope.manuscript().getCurrentBranchId())) {
            source = versions.findByManuscriptIdOrderByCreatedAtDesc(scope.manuscript().getId()).stream()
                    .filter(v -> v.getBranch().getId().equals(scope.branch().getId())).findFirst()
                    .map(v -> v.getSectionsJson()).orElse("{}");
        }
        return approval.getTextHash().equals(textHash(blocks(section(source, approval.getSceneId()))));
    }
    private Assertion validate(Assertion value, NarrativeApproval approval, Set<UUID> ids) {
        if (value == null || !validator.validate(value).isEmpty()) throw invalid("NARRATIVE_INVALID_ASSERTION");
        if ((value.characterId() != null && !ids.contains(value.characterId()))
                || (value.holderCharacterId() != null && !ids.contains(value.holderCharacterId()))) throw invalid("NARRATIVE_UNKNOWN_CHARACTER");
        if (value.kind() == Kind.BELIEF && value.holderCharacterId() == null) throw invalid("NARRATIVE_BELIEF_HOLDER_REQUIRED");
        List<Evidence> evidence = value.evidence().stream().map(e -> resolve(blockList(approval), e)).toList();
        return new Assertion(value.subject(), value.characterId(), value.statement(), value.kind(), value.holderCharacterId(),
                value.worldTime(), value.uncertainty(), evidence, value.supersedesId());
    }
    private Set<UUID> characterIds(Scope scope) {
        Set<UUID> ids = new HashSet<>();
        characters.findByStory(scope.manuscript().getOutline().getStory()).forEach(c -> ids.add(c.getId()));
        return ids;
    }
    private boolean active(NarrativeRecord value, long revision) { return "CONFIRMED".equals(status(value, revision)); }
    private String status(NarrativeRecord value, long revision) {
        if (value.getSupersededRevision() != null && value.getSupersededRevision() <= revision) return "SUPERSEDED";
        if (value.getInvalidatedRevision() != null && value.getInvalidatedRevision() <= revision) return "STALE";
        return "CONFIRMED";
    }
    private RecordView view(NarrativeRecord value, long revision) {
        return new RecordView(value.getId(), value.getExtraction().getId(), value.getExtraction().getApproval().getId(), value.getExtraction().getApproval().getSceneId(),
                position(value.getExtraction().getApproval()), assertion(value), status(value, revision), value.getCreatedRevision(),
                value.getInvalidatedRevision(), value.getSupersededRevision(), value.getCreatedAt());
    }
    private ExtractionView view(Scope scope, NarrativeExtraction value) {
        String status = value.getOperationId() == null ? "QUEUED" : operations.findById(value.getOperationId()).map(r -> r.getStatus().name()).orElse("FAILED");
        if (!"CANCELLED".equals(status)) {
            if (value.getError() != null) status = "INVALID_OUTPUT";
            else if (value.getCandidatesJson() != null) status = "READY";
        }
        return new ExtractionView(value.getId(), value.getApproval().getId(), value.getApproval().getSceneId(), value.getOperationId(), status, stale(scope, value),
                value.getReviewJson() != null, value.getBaseCanonRevision(), value.getPromptVersion(), value.getModel(),
                value.getUsageJson() == null ? null : tree(value.getUsageJson()), candidates(value), value.getError(),
                value.getReviewJson() == null ? null : tree(value.getReviewJson()), value.getCreatedAt());
    }
    private Assertion assertion(NarrativeRecord record) { return read(record.getAssertionJson(), Assertion.class); }
    private Position position(NarrativeApproval approval) { return read(approval.getPositionJson(), Position.class); }
    private List<Block> blockList(NarrativeApproval approval) { return read(approval.getBlocksJson(), new TypeReference<>() {}); }
    private List<Candidate> candidates(NarrativeExtraction value) { return value.getCandidatesJson() == null ? List.of() : read(value.getCandidatesJson(), new TypeReference<>() {}); }
    private String section(String sections, UUID sceneId) { return tree(sections).path(sceneId.toString()).asText(""); }
    private JsonNode tree(String value) { try { return json.readTree(value == null ? "{}" : value); } catch (Exception ex) { throw new IllegalStateException("NARRATIVE_STORED_DATA_INVALID", ex); } }
    String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private <T> T read(String value, Class<T> type) { try { return json.readValue(value, type); } catch (Exception ex) { throw new IllegalStateException("NARRATIVE_STORED_DATA_INVALID", ex); } }
    private <T> T read(String value, TypeReference<T> type) { try { return json.readValue(value, type); } catch (Exception ex) { throw new IllegalStateException("NARRATIVE_STORED_DATA_INVALID", ex); } }
    private String key(String value) { if (value == null || value.isBlank() || value.length() > 128) throw invalid("NARRATIVE_IDEMPOTENCY_REQUIRED"); return value; }
    private static ApiStatusException missing() { return new ApiStatusException(HttpStatus.NOT_FOUND, "NARRATIVE_NOT_FOUND"); }
    private static ApiStatusException conflict(String code) { return new ApiStatusException(HttpStatus.CONFLICT, code); }
    private record Scope(Manuscript manuscript, V2ManuscriptBranch branch, NarrativeLedger ledger) {}
}
