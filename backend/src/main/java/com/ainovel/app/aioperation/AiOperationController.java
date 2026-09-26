package com.ainovel.app.aioperation;

import com.ainovel.app.common.CurrentUserResolver;
import com.ainovel.app.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/v1/ai-operations")
public class AiOperationController {
    private final AiOperationService service;
    private final CurrentUserResolver users;

    public AiOperationController(AiOperationService service, CurrentUserResolver users) {
        this.service = service;
        this.users = users;
    }

    @GetMapping("/{id}")
    public AiOperationDtos.Progress get(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID id) {
        return service.get(users.require(principal), id);
    }

    @GetMapping("/active")
    public ResponseEntity<AiOperationDtos.Progress> active(@AuthenticationPrincipal UserDetails principal,
                                                           @RequestParam String scopeType,
                                                           @RequestParam UUID scopeId) {
        User user = users.require(principal);
        return ResponseEntity.ofNullable(service.active(user, scopeType, scopeId));
    }

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID id) {
        return service.events(users.require(principal), id);
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "重试可恢复的 AI 任务", description = "重新领取时生成新的执行 token；结果不明或已输出部分结果的任务不自动重发整次生成。")
    @ApiResponses({@ApiResponse(responseCode = "202", description = "任务已进入队列"),
            @ApiResponse(responseCode = "409", description = "当前任务状态不可重试")})
    public ResponseEntity<AiOperationDtos.Accepted> retry(@AuthenticationPrincipal UserDetails principal,
                                                          @PathVariable UUID id) {
        return ResponseEntity.accepted().body(service.retry(users.require(principal), id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消 AI 任务", description = "取消与结果提交由数据库仲裁。返回实际状态；若结果先提交则返回 SUCCEEDED，不声称取消成功。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "返回取消仲裁后的实际任务状态"),
            @ApiResponse(responseCode = "404", description = "任务不存在或不属于当前用户")})
    public ResponseEntity<AiOperationDtos.Progress> cancel(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID id) {
        return ResponseEntity.ok(service.cancel(users.require(principal), id));
    }
}
