package com.ainovel.app.user;

import com.ainovel.app.economy.EconomyService;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.OutlineRepository;
import com.ainovel.app.story.repo.StoryRepository;
import com.ainovel.app.user.dto.*;
import com.ainovel.app.world.model.World;
import com.ainovel.app.world.repo.WorldRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

@RestController
@RequestMapping("/v1/user")
@Tag(name = "User", description = "用户个人中心与资产接口")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EconomyService economyService;
    @Autowired
    private StoryRepository storyRepository;
    @Autowired
    private WorldRepository worldRepository;
    @Autowired
    private OutlineRepository outlineRepository;
    @Autowired
    private ManuscriptRepository manuscriptRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private User currentUser(UserDetails details) {
        return userRepository.findByUsername(details.getUsername()).orElseThrow();
    }

    @GetMapping("/profile")
    @Operation(summary = "获取个人资料", description = "返回当前用户基础信息、角色、资产与签到时间。")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "查询成功",
                    content = @Content(examples = @ExampleObject(value = "{\"id\":\"2f2ac8d9-3b9b-45f9-a4a0-6f1f0899a9d1\",\"username\":\"demo\",\"email\":\"demo@example.com\",\"avatar\":null,\"role\":\"user\",\"credits\":1200.0,\"isBanned\":false,\"lastCheckIn\":\"2026-02-16T08:00:00Z\"}"))
            ),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public ResponseEntity<UserProfileResponse> profile(@AuthenticationPrincipal UserDetails principal) {
        User user = currentUser(principal);
        EconomyService.BalanceSnapshot balance = economyService.currentBalance(user);
        var lastCheckInAt = balance.lastCheckInAt();
        return ResponseEntity.ok(toProfile(user, balance, lastCheckInAt));
    }

    @GetMapping("/summary")
    @Operation(summary = "获取创作汇总", description = "统计当前用户的小说数、世界观数、总字数与世界观条目数。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public ResponseEntity<UserSummaryResponse> summary(@AuthenticationPrincipal UserDetails principal) {
        User user = currentUser(principal);
        long novelCount = storyRepository.countByUser(user);
        long worldCount = worldRepository.countByUser(user);
        long totalWords = estimateTotalWords(user);
        long totalEntries = estimateWorldEntries(user);
        return ResponseEntity.ok(new UserSummaryResponse(novelCount, worldCount, totalWords, totalEntries));
    }

    @PostMapping("/check-in")
    @Operation(summary = "每日签到", description = "在本项目本地账本执行每日签到并返回资产变动。")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "签到成功或今日已签到",
                    content = @Content(examples = @ExampleObject(value = "{\"success\":true,\"points\":500.0,\"newTotal\":1500.0}"))
            ),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public ResponseEntity<CreditChangeResponse> checkIn(@AuthenticationPrincipal UserDetails principal) {
        User user = currentUser(principal);
        EconomyService.CreditChangeResult result = economyService.checkIn(user);
        return ResponseEntity.ok(new CreditChangeResponse(
                result.success(),
                result.points(),
                result.totalCredits(),
                result.projectCredits(),
                result.publicCredits(),
                result.totalCredits(),
                result.message()
        ));
    }

    @PostMapping("/redeem")
    @Operation(summary = "兑换码兑换", description = "在本项目本地兑换码账本执行兑换并返回资产变动。")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "兑换成功",
                    content = @Content(examples = @ExampleObject(value = "{\"success\":true,\"points\":1000.0,\"newTotal\":2500.0}"))
            ),
            @ApiResponse(responseCode = "400", description = "兑换码无效或已使用"),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public ResponseEntity<CreditChangeResponse> redeem(@AuthenticationPrincipal UserDetails principal, @Valid @RequestBody RedeemRequest request) {
        User user = currentUser(principal);
        EconomyService.CreditChangeResult result = economyService.redeem(user, request.code());
        return ResponseEntity.ok(new CreditChangeResponse(
                result.success(),
                result.points(),
                result.totalCredits(),
                result.projectCredits(),
                result.publicCredits(),
                result.totalCredits(),
                result.message()
        ));
    }

    @PostMapping("/credits/convert")
    @Operation(summary = "通用积分兑换项目积分", description = "按 1:1 将 payService 通用积分兑换为本项目专属积分。")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "兑换成功",
                    content = @Content(examples = @ExampleObject(value = "{\"orderNo\":\"CVT-6A4C2710D8D845B4B09C\",\"amount\":100.0,\"projectCredits\":1300.0,\"publicCredits\":200.0,\"totalCredits\":1500.0}"))
            ),
            @ApiResponse(responseCode = "400", description = "余额不足或参数错误"),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public ResponseEntity<ConvertCreditsResponse> convertCredits(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody ConvertCreditsRequest request
    ) {
        User user = currentUser(principal);
        EconomyService.ConversionResult result = economyService.convertPublicToProject(user, request.amount(), request.idempotencyKey());
        return ResponseEntity.ok(new ConvertCreditsResponse(
                result.orderNo(),
                result.amount(),
                result.projectBefore(),
                result.projectAfter(),
                result.publicBefore(),
                result.publicAfter(),
                result.totalCredits()
        ));
    }

    @GetMapping("/credits/conversions")
    @Operation(summary = "查询通用积分兑换历史", description = "分页查询当前用户通用积分兑换项目积分的订单历史。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public ResponseEntity<java.util.List<CreditConversionHistoryItemResponse>> conversions(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        User user = currentUser(principal);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        java.util.List<CreditConversionHistoryItemResponse> items = economyService
                .listConversions(user, PageRequest.of(safePage, safeSize))
                .stream()
                .map(it -> new CreditConversionHistoryItemResponse(
                        it.id(),
                        it.orderNo(),
                        it.requestedAmount(),
                        it.convertedAmount(),
                        it.projectBefore(),
                        it.projectAfter(),
                        it.publicBefore(),
                        it.publicAfter(),
                        it.status(),
                        it.remoteMessage(),
                        it.createdAt()
                ))
                .toList();
        return ResponseEntity.ok(items);
    }

    @GetMapping("/credits/ledger")
    @Operation(summary = "查询项目积分流水", description = "分页查询当前用户项目积分流水。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public ResponseEntity<java.util.List<CreditLedgerItemResponse>> ledger(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        User user = currentUser(principal);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        java.util.List<CreditLedgerItemResponse> items = economyService
                .listLedger(user, PageRequest.of(safePage, safeSize))
                .stream()
                .map(it -> new CreditLedgerItemResponse(
                        it.id(),
                        it.type(),
                        it.delta(),
                        it.balanceAfter(),
                        it.referenceType(),
                        it.referenceId(),
                        it.description(),
                        it.createdAt()
                ))
                .toList();
        return ResponseEntity.ok(items);
    }

    @PostMapping("/password")
    @Operation(summary = "修改密码（已下线）", description = "当前系统使用统一登录，密码管理由外部 UserService 负责。")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "501",
                    description = "功能未在本服务提供",
                    content = @Content(examples = @ExampleObject(value = "{\"success\":false,\"message\":\"PASSWORD_MANAGED_BY_SSO\"}"))
            )
    })
    public ResponseEntity<BasicResponse> updatePassword(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(501).body(new BasicResponse(false, "PASSWORD_MANAGED_BY_SSO"));
    }

    private UserProfileResponse toProfile(User user, EconomyService.BalanceSnapshot balance, java.time.Instant lastCheckInAt) {
        String role = user.hasRole("ROLE_ADMIN") ? "admin" : "user";
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getAvatarUrl(),
                role,
                balance.projectCredits(),
                balance.projectCredits(),
                balance.publicCredits(),
                balance.totalCredits(),
                user.isBanned(),
                lastCheckInAt
        );
    }

    private long estimateTotalWords(User user) {
        long total = 0;
        for (Story story : storyRepository.findByUser(user)) {
            for (Outline outline : outlineRepository.findByStory(story)) {
                for (Manuscript manuscript : manuscriptRepository.findByOutline(outline)) {
                    total += estimateWordsFromSections(manuscript.getSectionsJson());
                }
            }
        }
        return total;
    }

    private long estimateWordsFromSections(String sectionsJson) {
        if (sectionsJson == null || sectionsJson.isBlank()) return 0;
        try {
            Map<String, String> sections = objectMapper.readValue(sectionsJson, new TypeReference<>() {});
            long total = 0;
            for (String html : sections.values()) {
                if (html == null) continue;
                String plain = html.replaceAll("<[^>]*>", "");
                total += plain.trim().length();
            }
            return total;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private long estimateWorldEntries(User user) {
        long total = 0;
        for (World world : worldRepository.findByUser(user)) {
            total += countNonEmptyEntries(world.getModulesJson());
        }
        return total;
    }

    private long countNonEmptyEntries(String modulesJson) {
        if (modulesJson == null || modulesJson.isBlank()) return 0;
        try {
            Map<String, Map<String, String>> modules = objectMapper.readValue(modulesJson, new TypeReference<>() {});
            long total = 0;
            for (Map<String, String> fields : modules.values()) {
                if (fields == null) continue;
                for (String v : fields.values()) {
                    if (v != null && !v.isBlank()) total++;
                }
            }
            return total;
        } catch (Exception ignored) {
            return 0;
        }
    }
}
