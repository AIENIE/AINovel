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
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class G2EvaluationService {
    private final G2EvaluationExperimentRepository experimentRepository;
    private final G2EvaluationInviteRepository inviteRepository;
    private final G2EvaluationSampleRepository sampleRepository;
    private final G2EvaluationVoteRepository voteRepository;
    private final UserRepository userRepository;
    private final ManuscriptRepository manuscriptRepository;
    private final G2EvaluationGenerationWorker generationWorker;
    private final Executor executor;

    public G2EvaluationService(G2EvaluationExperimentRepository experimentRepository,
                               G2EvaluationInviteRepository inviteRepository,
                               G2EvaluationSampleRepository sampleRepository,
                               G2EvaluationVoteRepository voteRepository,
                               UserRepository userRepository,
                               ManuscriptRepository manuscriptRepository,
                               G2EvaluationGenerationWorker generationWorker,
                               @Qualifier("g2EvaluationExecutor") Executor executor) {
        this.experimentRepository = experimentRepository;
        this.inviteRepository = inviteRepository;
        this.sampleRepository = sampleRepository;
        this.voteRepository = voteRepository;
        this.userRepository = userRepository;
        this.manuscriptRepository = manuscriptRepository;
        this.generationWorker = generationWorker;
        this.executor = executor;
    }

    @Transactional
    public G2EvaluationDtos.ExperimentResponse create(User admin, G2EvaluationDtos.CreateExperimentRequest request) {
        String title = request.title() == null ? "" : request.title().trim();
        if (title.isBlank()) {
            throw new BusinessException("盲测名称不能为空");
        }
        LinkedHashSet<String> reviewerNames = new LinkedHashSet<>();
        for (String raw : request.reviewerUsernames()) {
            String username = raw == null ? "" : raw.trim();
            if (!username.isBlank()) {
                reviewerNames.add(username);
            }
        }
        if (reviewerNames.isEmpty()) {
            throw new BusinessException("至少邀请一名 SSO 评审者");
        }

        G2EvaluationExperiment experiment = new G2EvaluationExperiment();
        experiment.setTitle(title);
        experiment.setStatus(G2EvaluationStatus.DRAFT);
        experiment.setCreatedBy(admin);
        experimentRepository.save(experiment);

        for (String username : reviewerNames) {
            User reviewer = userRepository.findByUsername(username)
                    .orElseThrow(() -> new BusinessException("评审用户不存在：" + username));
            if (reviewer.getRemoteUid() == null || reviewer.getRemoteUid() <= 0) {
                throw new BusinessException("评审用户不是已绑定的 SSO 用户：" + username);
            }
            G2EvaluationInvite invite = new G2EvaluationInvite();
            invite.setExperiment(experiment);
            invite.setReviewer(reviewer);
            invite.setStatus(G2EvaluationInviteStatus.INVITED);
            inviteRepository.save(invite);
        }
        return responseFor(experiment);
    }

    @Transactional(readOnly = true)
    public List<G2EvaluationDtos.ExperimentResponse> listAll() {
        return experimentRepository.findAllByOrderByCreatedAtDesc().stream().map(this::responseFor).toList();
    }

    @Transactional(readOnly = true)
    public List<G2EvaluationDtos.ExperimentResponse> listOpenForAuthors() {
        return experimentRepository.findByStatusInOrderByCreatedAtDesc(List.of(G2EvaluationStatus.COLLECTING)).stream()
                .map(this::responseFor)
                .toList();
    }

    @Transactional
    public G2EvaluationDtos.ExperimentResponse transition(UUID experimentId, G2EvaluationStatus targetStatus) {
        G2EvaluationExperiment experiment = requiredExperiment(experimentId);
        G2EvaluationStatus current = experiment.getStatus();
        if (current == targetStatus) {
            return responseFor(experiment);
        }
        boolean validTransition = (current == G2EvaluationStatus.DRAFT && targetStatus == G2EvaluationStatus.COLLECTING)
                || (current == G2EvaluationStatus.COLLECTING && targetStatus == G2EvaluationStatus.REVIEWING)
                || (current == G2EvaluationStatus.REVIEWING && targetStatus == G2EvaluationStatus.CLOSED);
        if (!validTransition) {
            throw new BusinessException("不允许的盲测状态转换：" + current + " -> " + targetStatus);
        }
        if (targetStatus == G2EvaluationStatus.REVIEWING
                && sampleRepository.countByExperimentAndStatus(experiment, G2EvaluationSampleStatus.READY) == 0) {
            throw new BusinessException("至少需要一个生成成功的样本对后才能开始评审");
        }
        experiment.setStatus(targetStatus);
        return responseFor(experimentRepository.save(experiment));
    }

    @Transactional
    public G2EvaluationDtos.SampleSubmissionResponse submitSample(User author,
                                                                   UUID experimentId,
                                                                   G2EvaluationDtos.SubmitSampleRequest request) {
        G2EvaluationExperiment experiment = requiredExperiment(experimentId);
        if (experiment.getStatus() != G2EvaluationStatus.COLLECTING) {
            throw new BusinessException("当前盲测未处于投稿收集阶段");
        }
        Manuscript manuscript = manuscriptRepository.findWithStoryById(request.manuscriptId())
                .orElseThrow(() -> new BusinessException("稿件不存在"));
        if (manuscript.getOutline() == null || manuscript.getOutline().getStory() == null
                || manuscript.getOutline().getStory().getUser() == null
                || !author.getId().equals(manuscript.getOutline().getStory().getUser().getId())) {
            throw new BusinessException("只能提交自己的稿件场景");
        }
        if (sampleRepository.existsByExperimentAndAuthorAndManuscriptIdAndSceneId(
                experiment, author, request.manuscriptId(), request.sceneId())) {
            throw new BusinessException("该场景已提交到本次盲测");
        }

        G2EvaluationSample sample = new G2EvaluationSample();
        sample.setExperiment(experiment);
        sample.setAuthor(author);
        sample.setManuscriptId(request.manuscriptId());
        sample.setSceneId(request.sceneId());
        sample.setStatus(G2EvaluationSampleStatus.PENDING);
        sampleRepository.save(sample);
        UUID sampleId = sample.getId();
        scheduleGenerationAfterCommit(sampleId);
        return new G2EvaluationDtos.SampleSubmissionResponse(sampleId, sample.getStatus().name());
    }

    @Transactional
    public G2EvaluationDtos.ReviewSampleResponse nextReviewSample(User reviewer, UUID experimentId) {
        G2EvaluationExperiment experiment = requiredExperiment(experimentId);
        if (experiment.getStatus() != G2EvaluationStatus.REVIEWING) {
            throw new BusinessException("当前盲测尚未开放评审");
        }
        acceptInvite(experiment, reviewer);
        return sampleRepository.findNextReviewableSample(experiment, reviewer.getId(), PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(sample -> toReviewSample(sample, reviewer))
                .orElse(null);
    }

    @Transactional
    public G2EvaluationDtos.ExperimentResponse submitVote(User reviewer,
                                                           UUID experimentId,
                                                           G2EvaluationDtos.VoteRequest request) {
        G2EvaluationExperiment experiment = requiredExperiment(experimentId);
        if (experiment.getStatus() != G2EvaluationStatus.REVIEWING) {
            throw new BusinessException("当前盲测尚未开放评审");
        }
        acceptInvite(experiment, reviewer);
        G2EvaluationSample sample = sampleRepository.findByIdAndExperiment(request.sampleId(), experiment)
                .orElseThrow(() -> new BusinessException("盲测样本不存在"));
        if (sample.getStatus() != G2EvaluationSampleStatus.READY) {
            throw new BusinessException("盲测样本尚未生成完成");
        }
        if (sample.getAuthor().getId().equals(reviewer.getId())) {
            throw new BusinessException("投稿作者不能评审自己的样本");
        }
        if (voteRepository.existsBySampleAndReviewer(sample, reviewer)) {
            throw new BusinessException("该样本已完成投票");
        }
        G2EvaluationVote vote = new G2EvaluationVote();
        vote.setExperiment(experiment);
        vote.setSample(sample);
        vote.setReviewer(reviewer);
        vote.setChoice(toStoredChoice(request.choice(), isCraftedOnLeft(sample, reviewer)));
        voteRepository.save(vote);
        return responseFor(experiment);
    }

    private void scheduleGenerationAfterCommit(UUID sampleId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            executor.execute(() -> generationWorker.generate(sampleId));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                executor.execute(() -> generationWorker.generate(sampleId));
            }
        });
    }

    private void acceptInvite(G2EvaluationExperiment experiment, User reviewer) {
        G2EvaluationInvite invite = inviteRepository.findByExperimentAndReviewer(experiment, reviewer)
                .orElseThrow(() -> new BusinessException("你未被邀请参与本次盲测评审"));
        if (invite.getStatus() == G2EvaluationInviteStatus.INVITED) {
            invite.setStatus(G2EvaluationInviteStatus.ACCEPTED);
            invite.setAcceptedAt(Instant.now());
            inviteRepository.save(invite);
        }
    }

    private G2EvaluationDtos.ReviewSampleResponse toReviewSample(G2EvaluationSample sample, User reviewer) {
        boolean craftedOnLeft = isCraftedOnLeft(sample, reviewer);
        return craftedOnLeft
                ? new G2EvaluationDtos.ReviewSampleResponse(sample.getId(), sample.getCraftedText(), sample.getFastText())
                : new G2EvaluationDtos.ReviewSampleResponse(sample.getId(), sample.getFastText(), sample.getCraftedText());
    }

    private boolean isCraftedOnLeft(G2EvaluationSample sample, User reviewer) {
        String key = sample.getExperiment().getId() + ":" + sample.getId() + ":" + reviewer.getId();
        return Math.floorMod(key.hashCode(), 2) == 0;
    }

    private G2EvaluationVoteChoice toStoredChoice(String rawChoice, boolean craftedOnLeft) {
        String choice = rawChoice == null ? "" : rawChoice.trim().toUpperCase(Locale.ROOT);
        return switch (choice) {
            case "NEUTRAL" -> G2EvaluationVoteChoice.NEUTRAL;
            case "LEFT", "A" -> craftedOnLeft ? G2EvaluationVoteChoice.CRAFTED : G2EvaluationVoteChoice.FAST;
            case "RIGHT", "B" -> craftedOnLeft ? G2EvaluationVoteChoice.FAST : G2EvaluationVoteChoice.CRAFTED;
            default -> throw new BusinessException("投票选择必须为 LEFT、RIGHT 或 NEUTRAL");
        };
    }

    private G2EvaluationExperiment requiredExperiment(UUID experimentId) {
        return experimentRepository.findById(experimentId)
                .orElseThrow(() -> new BusinessException("盲测活动不存在"));
    }

    private G2EvaluationDtos.ExperimentResponse responseFor(G2EvaluationExperiment experiment) {
        long readyPairs = sampleRepository.countByExperimentAndStatus(experiment, G2EvaluationSampleStatus.READY);
        long pendingPairs = sampleRepository.countByExperimentAndStatus(experiment, G2EvaluationSampleStatus.PENDING)
                + sampleRepository.countByExperimentAndStatus(experiment, G2EvaluationSampleStatus.RUNNING);
        long validVotes = voteRepository.countByExperiment(experiment);
        long craftedWins = voteRepository.countByExperimentAndChoice(experiment, G2EvaluationVoteChoice.CRAFTED);
        long reviewers = voteRepository.countDistinctReviewers(experiment);
        double craftedWinRate = validVotes == 0 ? 0d : craftedWins * 100d / validVotes;
        boolean gatePassed = validVotes >= experiment.getMinimumVotes()
                && readyPairs >= experiment.getMinimumSamplePairs()
                && reviewers >= experiment.getMinimumReviewers()
                && craftedWinRate >= experiment.getCraftedWinRateTarget();
        return new G2EvaluationDtos.ExperimentResponse(
                experiment.getId(), experiment.getTitle(), experiment.getStatus(),
                (int) inviteRepository.countByExperiment(experiment), readyPairs, pendingPairs,
                validVotes, reviewers, craftedWins, craftedWinRate, gatePassed,
                experiment.getMinimumVotes(), experiment.getMinimumSamplePairs(), experiment.getMinimumReviewers(),
                experiment.getCraftedWinRateTarget(), experiment.getCreatedAt()
        );
    }
}
