package com.ainovel.app.integration;

import com.google.protobuf.Timestamp;
import fireflychat.billing.v1.BillingBalanceServiceGrpc;
import fireflychat.billing.v1.BillingCheckinServiceGrpc;
import fireflychat.billing.v1.BillingConversionServiceGrpc;
import fireflychat.billing.v1.BillingGrantServiceGrpc;
import fireflychat.billing.v1.BillingQueryServiceGrpc;
import fireflychat.billing.v1.BillingRedeemCodeServiceGrpc;
import fireflychat.billing.v1.BillingUsageServiceGrpc;
import fireflychat.billing.v1.CheckinRequest;
import fireflychat.billing.v1.CheckinResponse;
import fireflychat.billing.v1.ConvertPublicToProjectRequest;
import fireflychat.billing.v1.ConvertPublicToProjectResponse;
import fireflychat.billing.v1.DeductUsageRequest;
import fireflychat.billing.v1.DeductUsageResponse;
import fireflychat.billing.v1.GetCheckinStatusRequest;
import fireflychat.billing.v1.GetCheckinStatusResponse;
import fireflychat.billing.v1.GetProjectBalanceRequest;
import fireflychat.billing.v1.GetPublicBalanceRequest;
import fireflychat.billing.v1.GrantProjectPermanentTokensRequest;
import fireflychat.billing.v1.GrantPublicTokensRequest;
import fireflychat.billing.v1.LedgerEntry;
import fireflychat.billing.v1.ListLedgerEntriesRequest;
import fireflychat.billing.v1.ListLedgerEntriesResponse;
import fireflychat.billing.v1.ProjectBalance;
import fireflychat.billing.v1.RedeemCodeRequest;
import fireflychat.billing.v1.RedeemCodeResponse;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class BillingGrpcClient {

    private final ExternalServiceProperties properties;
    private final GrpcEndpointManager<EndpointClient> endpointManager;

    public BillingGrpcClient(
            ExternalServiceProperties properties,
            GrpcChannelFactory channelFactory
    ) {
        this.properties = properties;
        this.endpointManager = new GrpcEndpointManager<>(
                properties.getPayserviceGrpc(),
                "payservice-grpc",
                channelFactory
        );
    }

    public CheckinResult checkin(long remoteUserId) {
        CheckinResponse response = stubs().checkinStub()
                .withDeadlineAfter(timeoutMs(), TimeUnit.MILLISECONDS)
                .checkin(CheckinRequest.newBuilder()
                        .setRequestId(UUID.randomUUID().toString())
                        .setUserId(remoteUserId)
                        .setProjectKey(properties.getProjectKey())
                        .build());
        long total = balanceFromProject(response.getProjectBalance()) + publicBalance(remoteUserId);
        return new CheckinResult(
                response.getSuccess(),
                firstPositive(response.getCreditsGranted(), response.getTokensGranted()),
                total,
                response.getAlreadyCheckedIn(),
                response.getErrorMessage()
        );
    }

    public RedeemResult redeem(long remoteUserId, String code) {
        RedeemCodeResponse response = stubs().redeemStub()
                .withDeadlineAfter(timeoutMs(), TimeUnit.MILLISECONDS)
                .redeemCode(RedeemCodeRequest.newBuilder()
                        .setRequestId(UUID.randomUUID().toString())
                        .setUserId(remoteUserId)
                        .setProjectKey(properties.getProjectKey())
                        .setCode(code == null ? "" : code)
                        .build());
        long total = balanceFromProject(response.getProjectBalance())
                + firstPositive(response.getPublicPermanentCredits(), response.getPublicPermanentTokens());
        return new RedeemResult(
                response.getSuccess(),
                firstPositive(response.getCreditsGranted(), response.getTokensGranted()),
                total,
                response.getErrorMessage()
        );
    }

    public CheckinStatus checkinStatus(long remoteUserId) {
        GetCheckinStatusResponse response = stubs().checkinStub()
                .withDeadlineAfter(timeoutMs(), TimeUnit.MILLISECONDS)
                .getCheckinStatus(GetCheckinStatusRequest.newBuilder()
                        .setUserId(remoteUserId)
                        .setProjectKey(properties.getProjectKey())
                        .build());
        return new CheckinStatus(
                response.getCheckedInToday(),
                toInstant(response.getLastCheckinDate()),
                firstPositive(response.getCreditsGrantedToday(), response.getTokensGrantedToday())
        );
    }

    public long totalBalance(long remoteUserId) {
        var balanceStub = stubs().balanceStub().withDeadlineAfter(timeoutMs(), TimeUnit.MILLISECONDS);
        var publicBalance = balanceStub.getPublicBalance(GetPublicBalanceRequest.newBuilder()
                        .setUserId(remoteUserId)
                        .build());
        long publicTokens = firstPositive(
                publicBalance.getPublicPermanentCredits(),
                publicBalance.getPublicPermanentTokens()
        );
        var project = balanceStub.getProjectBalance(GetProjectBalanceRequest.newBuilder()
                        .setProjectKey(properties.getProjectKey())
                        .setUserId(remoteUserId)
                        .build())
                .getBalance();
        return publicTokens + balanceFromProject(project);
    }

    public long projectBalance(long remoteUserId) {
        var response = stubs().balanceStub()
                .withDeadlineAfter(timeoutMs(), TimeUnit.MILLISECONDS)
                .getProjectBalance(GetProjectBalanceRequest.newBuilder()
                        .setProjectKey(properties.getProjectKey())
                        .setUserId(remoteUserId)
                        .build());
        return balanceFromProject(response.getBalance());
    }

    public long publicBalance(long remoteUserId) {
        var response = stubs().balanceStub()
                .withDeadlineAfter(timeoutMs(), TimeUnit.MILLISECONDS)
                .getPublicBalance(GetPublicBalanceRequest.newBuilder()
                        .setUserId(remoteUserId)
                        .build());
        return firstPositive(response.getPublicPermanentCredits(), response.getPublicPermanentTokens());
    }

    public ConversionResult convertPublicToProject(long remoteUserId, long amount, String requestId) {
        try {
            ConvertPublicToProjectResponse response = stubs().conversionStub()
                    .withDeadlineAfter(timeoutMs(), TimeUnit.MILLISECONDS)
                    .convertPublicToProject(ConvertPublicToProjectRequest.newBuilder()
                            .setRequestId(requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId)
                            .setProjectKey(properties.getProjectKey())
                            .setUserId(remoteUserId)
                            .setCredits(amount)
                            .setTokens(amount)
                            .build());
            return new ConversionResult(
                    true,
                    amount,
                    amount,
                    firstPositive(response.getPublicPermanentCredits(), response.getPublicPermanentTokens()),
                    null
            );
        } catch (StatusRuntimeException ex) {
            String message;
            if (ex.getStatus() == null) {
                message = ex.getMessage();
            } else if (ex.getStatus().getDescription() == null || ex.getStatus().getDescription().isBlank()) {
                message = ex.getStatus().getCode().name();
            } else {
                message = ex.getStatus().getCode().name() + ": " + ex.getStatus().getDescription();
            }
            return new ConversionResult(false, 0, 0, 0, message == null ? "GRPC_ERROR" : message);
        }
    }

    public boolean grantPublicTokens(long remoteUserId, long amount, String reason) {
        try {
            stubs().grantStub()
                    .withDeadlineAfter(timeoutMs(), TimeUnit.MILLISECONDS)
                    .grantPublicTokens(GrantPublicTokensRequest.newBuilder()
                            .setRequestId(UUID.randomUUID().toString())
                            .setProjectKey(properties.getProjectKey())
                            .setUserId(remoteUserId)
                            .setCredits(amount)
                            .setTokens(amount)
                            .setReason(reason == null ? "" : reason)
                            .build());
            return true;
        } catch (StatusRuntimeException ignored) {
            return false;
        }
    }

    public ProjectGrantResult grantProjectCredits(long remoteUserId, long amount, String reason) {
        var response = stubs().grantStub()
                .withDeadlineAfter(timeoutMs(), TimeUnit.MILLISECONDS)
                .grantProjectPermanentTokens(GrantProjectPermanentTokensRequest.newBuilder()
                        .setRequestId(UUID.randomUUID().toString())
                        .setProjectKey(properties.getProjectKey())
                        .setUserId(remoteUserId)
                        .setCredits(amount)
                        .setTokens(amount)
                        .setReason(reason == null ? "" : reason)
                        .build());
        return new ProjectGrantResult(balanceFromProject(response.getProjectBalance()));
    }

    public UsageDeductionResult deductUsage(long remoteUserId, long inputCredits, long outputCredits, String referenceId) {
        DeductUsageResponse response = stubs().usageStub()
                .withDeadlineAfter(timeoutMs(), TimeUnit.MILLISECONDS)
                .deductUsage(DeductUsageRequest.newBuilder()
                        .setRequestId(UUID.randomUUID().toString())
                        .setProjectKey(properties.getProjectKey())
                        .setUserId(remoteUserId)
                        .setModelKey("ainovel-ai")
                        .setInputCredits(Math.max(0L, inputCredits))
                        .setOutputCredits(Math.max(0L, outputCredits))
                        .setSessionId(referenceId == null ? "" : referenceId)
                        .putMetadata("referenceId", referenceId == null ? "" : referenceId)
                        .putMetadata("source", "AINovel")
                        .build());
        long deducted = firstPositive(response.getDeductedCredits(), response.getDeductedTokens());
        return new UsageDeductionResult(deducted, balanceFromProject(response.getProjectBalance()));
    }

    public LedgerPage listLedgerEntries(long remoteUserId, int page, int size) {
        ListLedgerEntriesResponse response = stubs().queryStub()
                .withDeadlineAfter(timeoutMs(), TimeUnit.MILLISECONDS)
                .listLedgerEntries(ListLedgerEntriesRequest.newBuilder()
                        .setUserId(remoteUserId)
                        .setProjectKey(properties.getProjectKey())
                        .setPage(Math.max(0, page))
                        .setSize(Math.min(200, Math.max(1, size)))
                        .build());
        List<LedgerEntryView> entries = response.getEntriesList().stream()
                .map(this::toLedgerEntryView)
                .toList();
        long total = response.hasPageInfo() ? response.getPageInfo().getTotal() : entries.size();
        return new LedgerPage(entries, total);
    }

    private long balanceFromProject(ProjectBalance balance) {
        if (balance == null) {
            return 0L;
        }
        long credits = balance.getTempCredits() + balance.getPermanentCredits();
        if (credits > 0L) {
            return credits;
        }
        return balance.getTempTokens() + balance.getPermanentTokens();
    }

    private long firstPositive(long preferred, long fallback) {
        return preferred > 0L ? preferred : fallback;
    }

    private Instant toInstant(Timestamp timestamp) {
        if (timestamp == null || (timestamp.getSeconds() == 0 && timestamp.getNanos() == 0)) {
            return null;
        }
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    private LedgerEntryView toLedgerEntryView(LedgerEntry entry) {
        long projectDelta = firstNonZero(
                entry.getCreditDeltaTemp() + entry.getCreditDeltaPermanent(),
                entry.getTokenDeltaTemp() + entry.getTokenDeltaPermanent()
        );
        long projectBalance = firstNonZero(
                entry.getBalanceTempCredits() + entry.getBalancePermanentCredits(),
                entry.getBalanceTemp() + entry.getBalancePermanent()
        );
        String referenceId = entry.getRequestId() == null || entry.getRequestId().isBlank()
                ? String.valueOf(entry.getId())
                : entry.getRequestId();
        return new LedgerEntryView(
                String.valueOf(entry.getId()),
                entry.getType(),
                projectDelta,
                projectBalance,
                entry.getSource(),
                referenceId,
                entry.getType(),
                toInstant(entry.getCreatedAt())
        );
    }

    private long firstNonZero(long preferred, long fallback) {
        return preferred != 0L ? preferred : fallback;
    }

    private EndpointClient stubs() {
        return getOrCreateClient();
    }

    private synchronized EndpointClient getOrCreateClient() {
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), bearerToken());
        return endpointManager.getOrCreate((endpoint, channel) -> new EndpointClient(
                endpoint.host(),
                endpoint.port(),
                channel,
                BillingCheckinServiceGrpc.newBlockingStub(channel)
                        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata)),
                BillingRedeemCodeServiceGrpc.newBlockingStub(channel)
                        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata)),
                BillingBalanceServiceGrpc.newBlockingStub(channel)
                        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata)),
                BillingConversionServiceGrpc.newBlockingStub(channel)
                        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata)),
                BillingGrantServiceGrpc.newBlockingStub(channel)
                        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata)),
                BillingUsageServiceGrpc.newBlockingStub(channel)
                        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata)),
                BillingQueryServiceGrpc.newBlockingStub(channel)
                        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
        ));
    }

    private long timeoutMs() {
        return Math.max(800L, properties.getTimeoutMs());
    }

    private String bearerToken() {
        String raw = properties.getSecurity().getPay().getServiceJwt();
        String token = raw == null ? "" : raw.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            return token;
        }
        return "Bearer " + token;
    }

    @PreDestroy
    public synchronized void shutdown() {
        endpointManager.shutdown();
    }

    public record CheckinResult(
            boolean success,
            long tokensGranted,
            long totalTokens,
            boolean alreadyCheckedIn,
            String errorMessage
    ) {
    }

    public record RedeemResult(
            boolean success,
            long tokensGranted,
            long totalTokens,
            String errorMessage
    ) {
    }

    public record CheckinStatus(
            boolean checkedInToday,
            Instant lastCheckinAt,
            long tokensGrantedToday
    ) {
    }

    public record ConversionResult(
            boolean success,
            long convertedPublicTokens,
            long convertedProjectTokens,
            long publicRemainingTokens,
            String errorMessage
    ) {
    }

    public record ProjectGrantResult(long projectCredits) {
    }

    public record UsageDeductionResult(long chargedCredits, long remainingProjectCredits) {
    }

    public record LedgerEntryView(
            String id,
            String type,
            long delta,
            long balanceAfter,
            String referenceType,
            String referenceId,
            String description,
            Instant createdAt
    ) {
    }

    public record LedgerPage(List<LedgerEntryView> entries, long totalElements) {
    }

    private record EndpointClient(
            String host,
            int port,
            ManagedChannel channel,
            BillingCheckinServiceGrpc.BillingCheckinServiceBlockingStub checkinStub,
            BillingRedeemCodeServiceGrpc.BillingRedeemCodeServiceBlockingStub redeemStub,
            BillingBalanceServiceGrpc.BillingBalanceServiceBlockingStub balanceStub,
            BillingConversionServiceGrpc.BillingConversionServiceBlockingStub conversionStub,
            BillingGrantServiceGrpc.BillingGrantServiceBlockingStub grantStub,
            BillingUsageServiceGrpc.BillingUsageServiceBlockingStub usageStub,
            BillingQueryServiceGrpc.BillingQueryServiceBlockingStub queryStub
    ) implements GrpcEndpointManager.ManagedClient {
        public void close() {
            channel.shutdownNow();
        }
    }
}
