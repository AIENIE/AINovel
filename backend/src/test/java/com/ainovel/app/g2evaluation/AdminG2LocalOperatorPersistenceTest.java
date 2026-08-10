package com.ainovel.app.g2evaluation;

import com.ainovel.app.g2evaluation.dto.G2EvaluationDtos;
import com.ainovel.app.g2evaluation.model.G2EvaluationExperiment;
import com.ainovel.app.g2evaluation.repo.G2EvaluationExperimentRepository;
import com.ainovel.app.g2evaluation.repo.G2EvaluationInviteRepository;
import com.ainovel.app.g2evaluation.repo.G2EvaluationSampleRepository;
import com.ainovel.app.g2evaluation.repo.G2EvaluationVoteRepository;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DataJpaTest
@ActiveProfiles("test")
class AdminG2LocalOperatorPersistenceTest {
    @Autowired private G2EvaluationExperimentRepository experimentRepository;
    @Autowired private G2EvaluationInviteRepository inviteRepository;
    @Autowired private G2EvaluationSampleRepository sampleRepository;
    @Autowired private G2EvaluationVoteRepository voteRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ManuscriptRepository manuscriptRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void createsCampaignForLocalOperatorWithoutCreatingOrResolvingSameNamedUser() {
        User reviewer = new User();
        reviewer.setUsername("reviewer-one");
        reviewer.setEmail("reviewer-one@example.com");
        reviewer.setPasswordHash("n/a");
        reviewer.setRemoteUid(9081L);
        userRepository.saveAndFlush(reviewer);

        G2EvaluationService service = new G2EvaluationService(
                experimentRepository,
                inviteRepository,
                sampleRepository,
                voteRepository,
                userRepository,
                manuscriptRepository,
                mock(G2EvaluationGenerationWorker.class),
                Runnable::run
        );

        G2EvaluationDtos.ExperimentResponse response = service.create(
                "local-operator",
                new G2EvaluationDtos.CreateExperimentRequest("边界盲测", List.of("reviewer-one"))
        );
        entityManager.flush();
        entityManager.clear();

        G2EvaluationExperiment stored = experimentRepository.findById(response.id()).orElseThrow();
        assertNull(stored.getCreatedBy());
        assertEquals("local-operator", stored.getCreatedByAdminSubject());
        assertTrue(userRepository.findByUsername("local-operator").isEmpty());
    }
}
