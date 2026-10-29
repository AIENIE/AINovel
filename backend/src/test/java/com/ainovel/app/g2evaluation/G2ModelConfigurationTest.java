package com.ainovel.app.g2evaluation;

import com.ainovel.app.economy.EconomyService;
import com.ainovel.app.g2evaluation.model.*;
import com.ainovel.app.g2evaluation.repo.G2EvaluationSampleRepository;
import com.ainovel.app.manuscript.*;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.modelconfig.*;
import com.ainovel.app.story.model.*;
import com.ainovel.app.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.*;
import java.util.*;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class G2ModelConfigurationTest {
    final G2EvaluationSampleRepository samples=mock(G2EvaluationSampleRepository.class);
    final ManuscriptRepository manuscripts=mock(ManuscriptRepository.class);
    final SceneGenerationService generation=mock(SceneGenerationService.class);
    final EconomyService economy=mock(EconomyService.class);
    final TransactionTemplate tx=mock(TransactionTemplate.class);
    final AiQuoteService quotes=mock(AiQuoteService.class);
    final G2EvaluationGenerationWorker worker=new G2EvaluationGenerationWorker(samples,manuscripts,generation,economy,tx);
    final G2EvaluationSample sample=new G2EvaluationSample();
    final User author=new User();
    final Manuscript manuscript=new Manuscript();
    final AiSelectionContext.Snapshot snapshot=new AiSelectionContext.Snapshot("original-quote",Map.of("scene.fast.draft",AiModelSelection.legacy("original-model","Original")));
    G2ModelConfigurationTest() {
        author.setId(UUID.randomUUID());author.setUsername("author");ReflectionTestUtils.setField(sample,"id",UUID.randomUUID());
        sample.setAuthor(author);sample.setManuscriptId(UUID.randomUUID());sample.setSceneId(UUID.randomUUID());sample.setStatus(G2EvaluationSampleStatus.PENDING);
        var story=new Story();story.setUser(author);var outline=new Outline();outline.setStory(story);manuscript.setOutline(outline);
        when(samples.findByIdForUpdate(sample.getId())).thenReturn(Optional.of(sample));when(manuscripts.findWithStoryById(sample.getManuscriptId())).thenReturn(Optional.of(manuscript));
        when(tx.execute(any())).thenAnswer(i->((TransactionCallback<?>)i.getArgument(0)).doInTransaction(mock(TransactionStatus.class)));
        doAnswer(i->{((Consumer<TransactionStatus>)i.getArgument(0)).accept(mock(TransactionStatus.class));return null;}).when(tx).executeWithoutResult(any());
        ReflectionTestUtils.setField(worker,"quotes",quotes);when(quotes.taskSnapshot(sample.getId())).thenReturn(snapshot);
    }
    @Test void successfulIndependentTaskUsesThePersistedSnapshotForBothDrafts() {
        when(generation.generateEvaluationCandidate(eq(manuscript),any(),any(),any())).thenAnswer(i->{assertThat(AiSelectionContext.current()).isEqualTo(snapshot);return "draft";});
        worker.generate(sample.getId());assertThat(sample.getStatus()).isEqualTo(G2EvaluationSampleStatus.READY);assertThat(AiSelectionContext.current()).isNull();
        verify(generation,times(2)).generateEvaluationCandidate(eq(manuscript),eq(sample.getSceneId()),eq(sample.getId()),any());
    }
    @Test void insufficientSettlementRetainsTheTaskWithoutARefundOrSecondInference() {
        when(generation.generateEvaluationCandidate(eq(manuscript),any(),any(),any())).thenThrow(new AiPaymentRequiredException());
        worker.generate(sample.getId());assertThat(sample.getStatus()).isEqualTo(G2EvaluationSampleStatus.PAYMENT_PENDING);assertThat(sample.getFailureMessage()).isEqualTo("AI_PAYMENT_REQUIRED");
        verifyNoInteractions(economy);assertThat(AiSelectionContext.current()).isNull();
    }
    @Test void uncertainResultRequiresReconciliationAndDoesNotBecomeAnOrdinaryFailure() {
        when(generation.generateEvaluationCandidate(eq(manuscript),any(),any(),any())).thenThrow(new com.ainovel.app.ai.AiResultUncertainException());
        worker.generate(sample.getId());assertThat(sample.getStatus()).isEqualTo(G2EvaluationSampleStatus.RECOVERY_REQUIRED);verifyNoInteractions(economy);
    }
    @Test void confirmedBusinessFailureRetainsTheExistingSampleRefundRule() {
        when(generation.generateEvaluationCandidate(eq(manuscript),any(),any(),any())).thenThrow(new IllegalArgumentException("invalid output"));
        worker.generate(sample.getId());assertThat(sample.getStatus()).isEqualTo(G2EvaluationSampleStatus.FAILED);verify(economy).refundFailedEvaluation(author,sample.getId());
    }
}
