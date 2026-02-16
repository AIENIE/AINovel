package com.ainovel.app.ai;

import com.ainovel.app.ai.dto.*;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/ai")
@Tag(name = "AI", description = "AI 能力网关接口（透传第三方 AiService）")
@SecurityRequirement(name = "bearerAuth")
public class AiController {
    @Autowired
    private AiService aiService;
    @Autowired
    private UserRepository userRepository;

    private User currentUser(UserDetails details) {
        return userRepository.findByUsername(details.getUsername()).orElseThrow();
    }

    @GetMapping("/models")
    @Operation(summary = "获取模型列表", description = "查询第三方 AiService 可用模型，用于前端模型选择。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public List<AiModelDto> models() {
        return aiService.listModels();
    }

    @PostMapping("/chat")
    @Operation(summary = "对话生成", description = "发送多轮消息到 AiService，返回助手回复与 token 统计。")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "生成成功",
                    content = @Content(examples = @ExampleObject(value = "{\"role\":\"assistant\",\"content\":\"你好，我可以帮你完善剧情。\",\"usage\":{\"promptTokens\":20,\"completionTokens\":32,\"cost\":0.0},\"remainingCredits\":1200.0}"))
            ),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "400", description = "请求参数错误")
    })
    public ResponseEntity<AiChatResponse> chat(@AuthenticationPrincipal UserDetails principal, @Valid @RequestBody AiChatRequest request) {
        return ResponseEntity.ok(aiService.chat(currentUser(principal), request));
    }

    @PostMapping("/refine")
    @Operation(summary = "文本润色", description = "根据给定文本与指令调用 AiService 进行润色。")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "润色成功",
                    content = @Content(examples = @ExampleObject(value = "{\"result\":\"润色后的文本内容\",\"usage\":{\"promptTokens\":18,\"completionTokens\":28,\"cost\":0.0},\"remainingCredits\":1180.0}"))
            ),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "400", description = "请求参数错误")
    })
    public ResponseEntity<AiRefineResponse> refine(@AuthenticationPrincipal UserDetails principal, @Valid @RequestBody AiRefineRequest request) {
        return ResponseEntity.ok(aiService.refine(currentUser(principal), request));
    }
}
