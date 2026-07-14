package com.ainovel.app.g2evaluation;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.g2evaluation.dto.G2EvaluationDtos;
import com.ainovel.app.g2evaluation.model.G2EvaluationExperiment;
import com.ainovel.app.g2evaluation.model.G2EvaluationInvite;
import com.ainovel.app.g2evaluation.model.G2EvaluationInviteStatus;
import com.ainovel.app.g2evaluation.model.G2EvaluationSample;
import com.ainovel.app.g2evaluation.model.G2EvaluationSampleStatus;
import com.ainovel.app.g2evaluation.model.G2EvaluationStatus;
import com.ainovel.app.g2evaluation.model.G2EvaluationVote;
import com.ainovel.app.g2evaluation.model.G2EvaluationVoteChoice;
import com.ainovel.app.g2evaluation.repo.G2EvaluationExperimentRepository;
import com.ainovel.app.g2evaluation.repo.G2EvaluationInviteRepository;
import com.ainovel.app.g2evaluation.repo.G2EvaluationSampleRepository;
import com.ainovel.app.g2evaluation.repo.G2EvaluationVoteRepository;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class G2EvaluationServiceTest {
    @Test
    void neutralVoteCountsTowardsGateDenominatorButNotCraftedWins() {
        Fixtures fixtures = new Fixtures();
        fixtures.readySample.setAuthor(fixtures.author);
        when(fixtures.voteRepository.countByExperiment(fixtures.experiment)).thenReturn(100L);
        when(fixtures.voteRepository.countByExperimentAndChoice(fixtures.experiment, G2EvaluationVoteChoice.CRAFTED)).thenReturn(55L);
        when(fixtures.voteRepository.countDistinctReviewers(fixtures.experiment)).thenReturn(10L);
        when(fixtures.sampleRepository.countByExperimentAndStatus(fixtures.experiment, G2EvaluationSampleStatus.READY)).thenReturn(20L);

        G2EvaluationDtos.ExperimentResponse response = fixtures.service.submitVote(
                fixtures.reviewer,
                fixtures.experimentId,
                new G2EvaluationDtos.VoteRequest(fixtures.sampleId, "NEUTRAL")
        );

        ArgumentCaptor<G2EvaluationVote> captor = ArgumentCaptor.forClass(G2EvaluationVote.class);
        verify(fixtures.voteRepository).save(captor.capture());
        assertEquals(G2EvaluationVoteChoice.NEUTRAL, captor.getValue().getChoice());
        assertEquals(55d, response.craftedWinRate());
        assertTrue(response.gatePassed());
    }

    @Test
    void authorCannotReviewOwnSample() {
        Fixtures fixtures = new Fixtures();
        fixtures.readySample.setAuthor(fixtures.reviewer);

        BusinessException error = assertThrows(BusinessException.class, () -> fixtures.service.submitVote(
                fixtures.reviewer,
                fixtures.experimentId,
                new G2EvaluationDtos.VoteRequest(fixtures.sampleId, "LEFT")
        ));

        assertTrue(error.getMessage().contains("不能评审自己的样本"));
    }

    @Test
    void gateRemainsClosedWhenOnlyNeutralVotesArePresent() {
        Fixtures fixtures = new Fixtures();
        when(fixtures.voteRepository.countByExperiment(fixtures.experiment)).thenReturn(100L);
        when(fixtures.voteRepository.countByExperimentAndChoice(fixtures.experiment, G2EvaluationVoteChoice.CRAFTED)).thenReturn(0L);
        when(fixtures.voteRepository.countDistinctReviewers(fixtures.experiment)).thenReturn(10L);
        when(fixtures.sampleRepository.countByExperimentAndStatus(fixtures.experiment, G2EvaluationSampleStatus.READY)).thenReturn(20L);

        G2EvaluationDtos.ExperimentResponse response = fixtures.service.submitVote(
                fixtures.reviewer,
                fixtures.experimentId,
                new G2EvaluationDtos.VoteRequest(fixtures.sampleId, "NEUTRAL")
        );

        assertEquals(0d, response.craftedWinRate());
        assertFalse(response.gatePassed());
    }

    private static class Fixtures {
        private final UUID experimentId = UUID.randomUUID();
        private final UUID sampleId = UUID.randomUUID();
        private final G2EvaluationExperimentRepository experimentRepository = mock(G2EvaluationExperimentRepository.class);
        private final G2EvaluationInviteRepository inviteRepository = mock(G2EvaluationInviteRepository.class);
        private final G2EvaluationSampleRepository sampleRepository = mock(G2EvaluationSampleRepository.class);
        private final G2EvaluationVoteRepository voteRepository = mock(G2EvaluationVoteRepository.class);
        private final UserRepository userRepository = mock(UserRepository.class);
        private final ManuscriptRepository manuscriptRepository = mock(ManuscriptRepository.class);
        private final G2EvaluationGenerationWorker worker = mock(G2EvaluationGenerationWorker.class);
        private final User author = user("author");
        private final User reviewer = user("reviewer");
        private final G2EvaluationExperiment experiment = new G2EvaluationExperiment();
        private final G2EvaluationSample readySample = new G2EvaluationSample();
        private final G2EvaluationService service;

        private Fixtures() {
            experiment.setTitle("G2 第一轮");
            experiment.setStatus(G2EvaluationStatus.REVIEWING);
            experiment.setCreatedBy(user("admin"));
            readySample.setExperiment(experiment);
            readySample.setAuthor(author);
            readySample.setStatus(G2EvaluationSampleStatus.READY);
            G2EvaluationInvite invite = new G2EvaluationInvite();
            invite.setExperiment(experiment);
            invite.setReviewer(reviewer);
            invite.setStatus(G2EvaluationInviteStatus.ACCEPTED);
            when(experimentRepository.findById(experimentId)).thenReturn(Optional.of(experiment));
            when(inviteRepository.findByExperimentAndReviewer(experiment, reviewer)).thenReturn(Optional.of(invite));
            when(sampleRepository.findByIdAndExperiment(sampleId, experiment)).thenReturn(Optional.of(readySample));
            when(sampleRepository.countByExperimentAndStatus(experiment, G2EvaluationSampleStatus.PENDING)).thenReturn(0L);
            when(sampleRepository.countByExperimentAndStatus(experiment, G2EvaluationSampleStatus.RUNNING)).thenReturn(0L);
            when(voteRepository.existsBySampleAndReviewer(readySample, reviewer)).thenReturn(false);
            service = new G2EvaluationService(
                    experimentRepository, inviteRepository, sampleRepository, voteRepository,
                    userRepository, manuscriptRepository, worker, Runnable::run
            );
        }

        private static User user(String username) {
            User user = new User();
            user.setId(UUID.randomUUID());
            user.setUsername(username);
            user.setEmail(username + "@example.com");
            user.setPasswordHash("x");
            user.setRemoteUid(Math.abs((long) username.hashCode()) + 1L);
            return user;
        }
    }
}
