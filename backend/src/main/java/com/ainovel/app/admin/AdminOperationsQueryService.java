package com.ainovel.app.admin;

import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.material.repo.MaterialRepository;
import com.ainovel.app.quality.repo.PlotQualityRunRepository;
import com.ainovel.app.quality.repo.SlopQualityRunRepository;
import com.ainovel.app.story.repo.StoryRepository;
import com.ainovel.app.world.repo.WorldRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminOperationsQueryService {
    private final AdminOperationsReadRepository readRepository;
    private final MaterialRepository materialRepository;
    private final StoryRepository storyRepository;
    private final WorldRepository worldRepository;
    private final ManuscriptRepository manuscriptRepository;
    private final SlopQualityRunRepository slopQualityRunRepository;
    private final PlotQualityRunRepository plotQualityRunRepository;

    public AdminOperationsQueryService(
            MaterialRepository materialRepository,
            StoryRepository storyRepository,
            WorldRepository worldRepository,
            ManuscriptRepository manuscriptRepository,
            SlopQualityRunRepository slopQualityRunRepository,
            PlotQualityRunRepository plotQualityRunRepository,
            AdminOperationsReadRepository readRepository
    ) {
        this.readRepository = readRepository;
        this.materialRepository = materialRepository;
        this.storyRepository = storyRepository;
        this.worldRepository = worldRepository;
        this.manuscriptRepository = manuscriptRepository;
        this.slopQualityRunRepository = slopQualityRunRepository;
        this.plotQualityRunRepository = plotQualityRunRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> assetSummary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stories", storyRepository.count());
        result.put("worlds", worldRepository.count());
        result.put("manuscripts", manuscriptRepository.count());
        result.put("materials", materialRepository.count());
        result.put("pendingMaterials", materialRepository.countByStatusIgnoreCase("pending"));
        result.put("highRiskQualityRuns", slopQualityRunRepository.countByOverallRiskScoreGreaterThanEqual(70)
                + plotQualityRunRepository.countByOverallRiskScoreGreaterThanEqual(70));
        return result;
    }

    public record PageResult<T>(List<T> items, int page, int size, long totalElements, long totalPages) { }

    @Transactional(readOnly = true)
    public PageResult<AdminOperationsReadRepository.AssetRow> assets(String kind, int page, int size, String search) {
        validate(page, size, search);
        return page(readRepository.assets(kind, page * size, size, search), page, size);
    }

    @Transactional(readOnly = true)
    public PageResult<AdminOperationsReadRepository.QualityRow> qualityRuns(int page, int size, String search, String filter) {
        validate(page, size, search);
        if (!List.of("all", "open", "high").contains(filter)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid quality filter");
        }
        return page(readRepository.quality(page * size, size, search, filter), page, size);
    }

    private static void validate(int page, int size, String search) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE || search == null || search.length() > 200) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid page, size or search (size: 1-100, search: at most 200 characters)");
        }
    }

    private static <T> PageResult<T> page(AdminOperationsReadRepository.Result<T> result, int page, int size) {
        return new PageResult<>(result.items(), page, size, result.total(), (result.total() + size - 1) / size);
    }
}
