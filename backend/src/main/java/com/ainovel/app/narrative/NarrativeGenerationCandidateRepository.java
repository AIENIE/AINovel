package com.ainovel.app.narrative;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface NarrativeGenerationCandidateRepository extends JpaRepository<NarrativeGenerationCandidate,UUID> {
    List<NarrativeGenerationCandidate> findTop20ByManuscriptIdAndBranchIdOrderByCreatedAtDesc(UUID manuscriptId,UUID branchId);
    Optional<NarrativeGenerationCandidate> findByOperationIdAndGenerationSequence(UUID operationId,Integer sequence);
    @org.springframework.data.jpa.repository.Query("select c.id as id,c.operationId as operationId,c.branchId as branchId,c.generationSequence as generationSequence,c.manuscriptVersion as manuscriptVersion,c.minHan as minHan,c.maxHan as maxHan,c.actualHan as actualHan,c.status as status,c.qualityStatus as qualityStatus,c.sourceKind as sourceKind,c.createdAt as createdAt from NarrativeGenerationCandidate c where c.manuscriptId=:manuscriptId and c.branchId=:branchId and c.sceneId=:sceneId and c.status<>'PREPARING' order by c.createdAt desc,c.generationSequence desc")
    List<SceneCandidateSummary> findSummaries(UUID manuscriptId,UUID branchId,UUID sceneId);
}
