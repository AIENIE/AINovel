package com.ainovel.app.ai;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.ai.dto.*;
import com.ainovel.app.economy.EconomyService;
import com.ainovel.app.integration.AiGatewayGrpcClient;
import com.ainovel.app.user.User;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.time.Instant;

@Service
public class AiService {
    @Autowired(required=false) private com.ainovel.app.modelconfig.AiModelConfigurationService modelConfiguration;
    @Autowired(required=false) private AiStoredResponseMetadata storedResponseMetadata;
    @org.springframework.beans.factory.annotation.Autowired
    private AiModelPolicy modelPolicy = new AiModelPolicy();

    private final AiGatewayGrpcClient aiGatewayGrpcClient;
    private final EconomyService economyService;
    private final AiAdmissionGuard admissionGuard;
    private final long reservationCredits;
    private final boolean reservationEnabled;
    private volatile Instant streamingCapabilityCheckedAt = Instant.EPOCH;
    private volatile boolean streamingCapabilityAvailable;

    public AiService(AiGatewayGrpcClient aiGatewayGrpcClient, EconomyService economyService) {
        this.aiGatewayGrpcClient = aiGatewayGrpcClient;
        this.economyService = economyService;
        this.admissionGuard = null;
        this.reservationCredits = 2L;
        this.reservationEnabled = false;
    }

    @Autowired
    public AiService(AiGatewayGrpcClient aiGatewayGrpcClient, EconomyService economyService,
                     AiAdmissionGuard admissionGuard,
                     @Value("${app.ai.admission.reservation-credits:2}") long reservationCredits) {
        this.aiGatewayGrpcClient = aiGatewayGrpcClient;
        this.economyService = economyService;
        this.admissionGuard = admissionGuard;
        this.reservationCredits = Math.max(1L, reservationCredits);
        this.reservationEnabled = true;
    }

    public List<AiModelDto> listModels(User user) {
        resolveGatewayUserId(user);
        if(modelConfiguration!=null)return modelConfiguration.publicModels();
        return List.of(modelPolicy.configuredTextModel());
    }

    public AiChatResponse chat(User user, AiChatRequest request) {
        return chat(user, request, null);
    }

    public AiChatResponse chat(User user, AiChatRequest request, AiUsageContext usageContext) {
        String contextKey=usageContext==null&&com.ainovel.app.modelconfig.AiSelectionContext.current()!=null
                ?com.ainovel.app.modelconfig.AiSelectionContext.callKey(request.featureKey()==null?"generic.chat":request.featureKey()):null;
        String key = usageContext == null ? contextKey!=null?contextKey:com.ainovel.app.aioperation.AiOperationExecutionContext.callKey(
                admissionGuard == null ? "legacy" : admissionGuard.requestHash(com.ainovel.app.modelconfig.AiSelectionContext.current()==null?request.forFeature(null):request)) : usageContext.idempotencyKey();
        return executeChat(user, request, usageContext, key);
    }

    public AiChatResponse chatWithIdempotency(User user, AiChatRequest request, String idempotencyKey) {
        return executeChat(user, request, null, normalizeIdempotencyKey(idempotencyKey));
    }

    private AiChatResponse executeChat(User user, AiChatRequest request, AiUsageContext usageContext,
                                       String idempotencyKey) {
        var selected=com.ainovel.app.modelconfig.AiSelectionContext.selection(request.featureKey()==null?"generic.chat":request.featureKey());
        if(request.nativeTools() && (selected==null || !reservationEnabled))
            throw new AiRequestNotSentException(org.springframework.http.HttpStatus.PRECONDITION_FAILED,"AI_NATIVE_CONFIGURATION_REQUIRED");
        EconomyService.StoredAiRequest stored=selected!=null&&reservationEnabled?economyService.frozenAiRequest(user,idempotencyKey):null;
        if(stored!=null&&stored.requestJson()!=null&&java.util.Objects.equals(stored.identity(),com.ainovel.app.modelconfig.AiSelectionContext.current().identity())) {
            request=modelConfiguration.read(stored.requestJson(),AiChatRequest.class);
        }
        Long remoteUid = resolveGatewayUserId(user);
        if (admissionGuard == null) {
            if(request.nativeTools())AiNativeRequestValidator.validate(request,new com.fasterxml.jackson.databind.ObjectMapper(),20000,100000);
            else validateChatMessages(request.messages());
        }
        final AiAdmissionGuard.Lease lease = admissionGuard == null ? null : admissionGuard.acquire(user, request);
        try (lease) {
            if (!reservationEnabled) {
                return executeLegacyChat(user, request, usageContext, remoteUid);
            }
            String referenceType = usageContext == null ? "AI_USAGE" : usageContext.referenceType();
            String referenceId = usageContext == null ? idempotencyKey : usageContext.referenceId();
            String requestHash = admissionGuard.requestHash(com.ainovel.app.modelconfig.AiSelectionContext.current()==null?request.forFeature(null):request);
            if(selected!=null)requestHash=com.ainovel.app.modelconfig.AiActionPlanner.hash(requestHash+selected.toString());
            long estimated=selected==null?reservationCredits:selected.cost(
                    com.ainovel.app.manuscript.context.SceneDraftContextCompiler.estimateTokens(request.nativeTools()?modelConfiguration.write(request):request.messages().stream().map(AiChatRequest.Message::content).collect(java.util.stream.Collectors.joining("\n"))),
                    request.maxOutputTokens()==null?com.ainovel.app.modelconfig.AiSelectionContext.current().outputBudget(selected.featureKey()):request.maxOutputTokens());
            EconomyService.AiReservationStart reservation = selected==null?economyService.reserveAiUsage(
                    user, reservationCredits, referenceType, referenceId, idempotencyKey, requestHash):
                    economyService.reserveAiUsage(user,estimated,referenceType,referenceId,idempotencyKey,requestHash,selected);
            if (reservation.replay()) {
                String actual=stored!=null&&stored.actualModelKey()!=null?stored.actualModelKey():selected==null?null:selected.modelKey();
                if(selected!=null&&AiProgressContext.current()!=null)AiProgressContext.current().onModelResolved(actual);
                return withSelection(response(user, reservation.content(), reservation.promptTokens(), reservation.completionTokens(),
                        reservation.cacheTokens(), reservation.charged(), reservation.metadata(),reservation.nativeResponseJson()),selected,actual);
            }
            if(selected!=null)economyService.freezeAiRequest(user,idempotencyKey,request);
            try {
                AiGatewayGrpcClient.ChatResult result = invokeGateway(remoteUid, request, idempotencyKey);
                if(request.nativeTools())economyService.recordAiResult(user,idempotencyKey,result.content(),result.promptTokens(),result.completionTokens(),result.cacheTokens(),result.modelKey(),result.metadata(),result.nativeResponseJson());
                else economyService.recordAiResult(user,idempotencyKey,result.content(),result.promptTokens(),result.completionTokens(),result.cacheTokens(),selected==null?null:result.modelKey(),result.metadata());
                if(selected!=null&&(!selected.streaming()||request.nativeTools())&&AiProgressContext.current()!=null)
                    AiProgressContext.current().onUnaryCompleted(result.modelKey(),result.completionTokens(),result.promptTokens(),result.cacheTokens());
                EconomyService.AiChargeResult charge = economyService.settleAiUsage(user, idempotencyKey,
                        result.content(), result.promptTokens(), result.completionTokens(), result.cacheTokens());
                return withSelection(response(user, result.content(), result.promptTokens(), result.completionTokens(),
                        result.cacheTokens(), charge.charged(), result.metadata(),result.nativeResponseJson()),selected,result.modelKey());
            } catch(com.ainovel.app.modelconfig.AiPaymentRequiredException pending) {
                throw pending;
            } catch (AiRequestNotSentException denied) {
                economyService.releaseAiReservation(user, idempotencyKey);
                throw denied;
            } catch (RuntimeException ex) {
                // A transport failure is not proof of non-execution under the legacy gateway contract.
                // Never refund/re-infer an uncertain request; RESULT_READY remains locally recoverable.
                try {
                    economyService.markAiResultUncertain(user, idempotencyKey);
                } catch (RuntimeException persistenceFailure) {
                    ex.addSuppressed(persistenceFailure);
                }
                throw new AiResultUncertainException(ex);
            }
        }
    }

    private AiChatResponse executeLegacyChat(User user, AiChatRequest request, AiUsageContext usageContext, Long remoteUid) {
        AiGatewayGrpcClient.ChatResult result = invokeGateway(remoteUid, request, null);
        EconomyService.AiChargeResult charge = usageContext == null
                ? economyService.chargeAiUsage(user, result.promptTokens(), result.completionTokens(), UUID.randomUUID().toString())
                : economyService.chargeAiUsage(user, result.promptTokens(), result.completionTokens(),
                        usageContext.referenceType(), usageContext.referenceId(), usageContext.idempotencyKey());
        return response(user, result.content(), result.promptTokens(), result.completionTokens(), result.cacheTokens(), charge.charged(), result.metadata());
    }

    private AiGatewayGrpcClient.ChatResult invokeGateway(Long remoteUid, AiChatRequest request, String requestId) {
        var progressListener = AiProgressContext.current();
        var selected=com.ainovel.app.modelconfig.AiSelectionContext.selection(request.featureKey()==null?"generic.chat":request.featureKey());
        if(selected!=null) {
            if(modelConfiguration!=null&&!modelConfiguration.available(selected.modelKey()))throw new AiRequestNotSentException(org.springframework.http.HttpStatus.CONFLICT,"AI_MODEL_UNAVAILABLE");
            if(request.nativeTools())return aiGatewayGrpcClient.complete(requestId==null?UUID.randomUUID().toString():requestId,remoteUid,selected,request,progressListener);
            return aiGatewayGrpcClient.complete(requestId==null?UUID.randomUUID().toString():requestId,remoteUid,selected,request.messages(),selected.streaming()?progressListener:null);
        }
        if (progressListener != null && !supportsRequiredModelStreaming(remoteUid)) {
            throw new BusinessException("当前 AI 模型不支持真实流式输出，请检查 ai-service 模型配置");
        }
        if (progressListener == null) {
            return requestId == null
                    ? aiGatewayGrpcClient.chatCompletions(remoteUid, modelPolicy.modelKey(), request.messages())
                    : aiGatewayGrpcClient.chatCompletions(requestId, remoteUid, modelPolicy.modelKey(), request.messages());
        }
        return requestId == null
                ? aiGatewayGrpcClient.chatCompletionsStream(remoteUid, modelPolicy.modelKey(), request.messages(), progressListener)
                : aiGatewayGrpcClient.chatCompletionsStream(requestId, remoteUid, modelPolicy.modelKey(),
                    request.messages(), progressListener);
    }

    private AiChatResponse response(User user, String content, long promptTokens, long completionTokens,
                                    long cacheTokens, long charged, AiResponseMetadata metadata) {
        return response(user,content,promptTokens,completionTokens,cacheTokens,charged,metadata,null);
    }

    private AiChatResponse response(User user,String content,long promptTokens,long completionTokens,
                                    long cacheTokens,long charged,AiResponseMetadata metadata,String nativeResponseJson) {
        var balance = economyService.currentBalance(user);
        return new AiChatResponse("assistant", content,
                new AiUsageDto((int) promptTokens, (int) completionTokens, (int) cacheTokens,
                        cacheHitRate(promptTokens, cacheTokens), charged), balance.totalCredits(),null,null,metadata,nativeResponseJson);
    }

    private AiChatResponse withSelection(AiChatResponse response,com.ainovel.app.modelconfig.AiModelSelection selected,String actual) {
        return new AiChatResponse(response.role(),response.content(),response.usage(),response.remainingCredits(),selected,actual,response.metadata(),response.nativeResponseJson());
    }

    /** A read-only lookup of owned durable receipts; never sends an inference or rewrites historical results. */
    public AiResponseMetadata storedResultMetadata(User user,String requestId) {
        return storedResponseMetadata == null ? null : storedResponseMetadata.find(user,requestId);
    }

    public AiRefineResponse refine(User user, AiRefineRequest request) {
        return refine(user, request, null);
    }

    public AiRefineResponse refine(User user, AiRefineRequest request, String idempotencyKey) {
        String instruction = request.instruction() == null ? "" : request.instruction();
        AiChatRequest chatRequest = new AiChatRequest(
                List.of(new AiChatRequest.Message("user", "请根据以下指令润色文本。\n\n指令:\n" + instruction + "\n\n文本:\n" + request.text())),
                request.modelId(),
                null,
                request.featureKey()==null?"generic.refine":request.featureKey()
        );
        AiChatResponse resp = idempotencyKey == null
                ? chat(user, chatRequest)
                : chatWithIdempotency(user, chatRequest, idempotencyKey);
        return new AiRefineResponse(resp.content(), resp.usage(), resp.remainingCredits());
    }

    private String normalizeIdempotencyKey(String raw) {
        String key = raw == null ? "" : raw.trim();
        if (key.isBlank() || key.length() > 128) {
            throw new BusinessException("Idempotency-Key 必须为 1-128 个字符");
        }
        return key;
    }

    private Long resolveGatewayUserId(User user) {
        Long remoteUid = user.getRemoteUid();
        if (remoteUid != null && remoteUid > 0) {
            return remoteUid;
        }
        throw new BusinessException("当前账号未绑定统一用户，无法调用 AI 服务");
    }

    private void validateChatMessages(List<AiChatRequest.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new BusinessException("messages 至少需要一条非空 user 消息");
        }
        boolean hasUserMessage = false;
        for (AiChatRequest.Message message : messages) {
            if (message == null) {
                continue;
            }
            String role = message.role() == null ? "" : message.role().trim().toLowerCase(Locale.ROOT);
            String content = message.content() == null ? "" : message.content().trim();
            if ("user".equals(role) && !content.isBlank()) {
                hasUserMessage = true;
                break;
            }
        }
        if (!hasUserMessage) {
            throw new BusinessException("messages 至少需要一条非空 user 消息");
        }
    }

    private double cacheHitRate(long promptTokens, long cacheTokens) {
        if (promptTokens <= 0 || cacheTokens <= 0) {
            return 0d;
        }
        return Math.max(0d, Math.min(1d, cacheTokens / (double) promptTokens));
    }

    private synchronized boolean supportsRequiredModelStreaming(long remoteUid) {
        Instant now = Instant.now();
        if (streamingCapabilityCheckedAt.plusSeconds(60).isAfter(now)) {
            return streamingCapabilityAvailable;
        }
        streamingCapabilityAvailable = aiGatewayGrpcClient.listModels(remoteUid).stream()
                .anyMatch(model -> model.supportsStreaming()
                        && (modelPolicy.modelKey().equalsIgnoreCase(model.id())
                        || modelPolicy.modelKey().equalsIgnoreCase(model.name())
                        || modelPolicy.displayName().equalsIgnoreCase(model.displayName())));
        streamingCapabilityCheckedAt = now;
        return streamingCapabilityAvailable;
    }
}
