package com.ainovel.app.quality.language;
import com.ainovel.app.aioperation.AiOperationDtos;
import com.ainovel.app.common.CurrentUserResolver;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.*;
import static com.ainovel.app.quality.language.LanguageDtos.*;

@RestController
@ResponseStatus(HttpStatus.OK)
@io.swagger.v3.oas.annotations.tags.Tag(name="语言检查",description="来源绑定、后台诊断与作者逐项修订")
@io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="400",description="参数或幂等键无效"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="404",description="无权访问或记录不存在"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="409",description="分支、正文版本、候选或幂等键冲突，不覆盖正文")
})
@RequestMapping("/v2/manuscripts/{manuscriptId}")
public class LanguageQualityController {
    private final LanguageQualityService quality;
    private final CurrentUserResolver users;
    public LanguageQualityController(LanguageQualityService quality,CurrentUserResolver users) { this.quality=quality; this.users=users; }
    @GetMapping("/quality-runs/language/settings")
    @io.swagger.v3.oas.annotations.Operation(summary="读取作品语言检查开关")
    public Settings settings(@AuthenticationPrincipal UserDetails principal,@PathVariable UUID manuscriptId) { return quality.settings(users.require(principal),manuscriptId); }
    @PutMapping("/quality-runs/language/settings")
    @io.swagger.v3.oas.annotations.Operation(summary="保存作品语言规范和生成后检查开关")
    public Settings settings(@AuthenticationPrincipal UserDetails principal,@PathVariable UUID manuscriptId,@RequestBody SettingsRequest request) { return quality.settings(users.require(principal),manuscriptId,request); }
    @GetMapping("/quality-runs/language")
    @io.swagger.v3.oas.annotations.Operation(summary="读取当前场景的语言报告及来源适用状态")
    public List<ReportDto> reports(@AuthenticationPrincipal UserDetails principal,@PathVariable UUID manuscriptId,@RequestParam UUID sceneId) { return quality.list(users.require(principal),manuscriptId,sceneId); }
    @GetMapping("/quality-runs/language/{reportId}")
    @io.swagger.v3.oas.annotations.Operation(summary="读取完整语言报告、覆盖与处置历史")
    public ReportDto report(@AuthenticationPrincipal UserDetails principal,@PathVariable UUID manuscriptId,@PathVariable UUID reportId) { return quality.get(users.require(principal),manuscriptId,reportId); }
    @PostMapping("/scenes/{sceneId}/quality-runs/language/operations") @ResponseStatus(HttpStatus.ACCEPTED)
    @io.swagger.v3.oas.annotations.Operation(summary="按正文和规范版本幂等登记语言检查")
    public AiOperationDtos.Accepted check(@AuthenticationPrincipal UserDetails principal,@PathVariable UUID manuscriptId,@PathVariable UUID sceneId,@RequestBody CheckRequest request) { return quality.start(users.require(principal),manuscriptId,sceneId,request); }
    @PostMapping("/quality-runs/language/{reportId}/issues/{issueId}/suggestions/operations") @ResponseStatus(HttpStatus.ACCEPTED)
    @io.swagger.v3.oas.annotations.Operation(summary="按问题幂等生成局部候选并复核；信息不足时保存原因，不生成候选或复核")
    public AiOperationDtos.Accepted suggest(@AuthenticationPrincipal UserDetails principal,@PathVariable UUID manuscriptId,@PathVariable UUID reportId,@PathVariable String issueId) { return quality.suggest(users.require(principal),manuscriptId,reportId,issueId); }
    @GetMapping("/quality-runs/language/{reportId}/patches/{patchId}")
    @io.swagger.v3.oas.annotations.Operation(summary="读取候选、完整差异输入和当前可应用状态")
    public PatchDto patch(@AuthenticationPrincipal UserDetails principal,@PathVariable UUID manuscriptId,@PathVariable UUID reportId,@PathVariable UUID patchId) { return quality.patch(users.require(principal),manuscriptId,reportId,patchId); }
    @PostMapping("/quality-runs/language/{reportId}/patches/{patchId}/{action:accept|reject|undo}")
    @io.swagger.v3.oas.annotations.Operation(summary="作者接受、拒绝或撤销候选，正文和处置记录原子保存")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",description="返回候选处置结果、当前分支、正文和新版本；同键重放原结果")
    public DecisionResult decide(@AuthenticationPrincipal UserDetails principal,@PathVariable UUID manuscriptId,@PathVariable UUID reportId,@PathVariable UUID patchId,
            @PathVariable String action,@RequestHeader("Idempotency-Key") String requestKey,@RequestBody DecisionRequest request) { return quality.decide(users.require(principal),manuscriptId,reportId,patchId,action,request,requestKey); }
}
