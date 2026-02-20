package com.ainovel.app.integration;

import com.google.protobuf.Timestamp;
import fireflychat.billing.v1.BillingBalanceServiceGrpc;
import fireflychat.billing.v1.BillingCheckinServiceGrpc;
import fireflychat.billing.v1.BillingConversionServiceGrpc;
import fireflychat.billing.v1.BillingGrantServiceGrpc;
import fireflychat.billing.v1.BillingRedeemCodeServiceGrpc;
import fireflychat.billing.v1.CheckinRequest;
import fireflychat.billing.v1.CheckinResponse;
import fireflychat.billing.v1.ConvertPublicToProjectRequest;
import fireflychat.billing.v1.ConvertPublicToProjectResponse;
import fireflychat.billing.v1.GetCheckinStatusRequest;
import fireflychat.billing.v1.GetCheckinStatusResponse;
import fireflychat.billing.v1.GetProjectBalanceRequest;
import fireflychat.billing.v1.GetPublicBalanceRequest;
import fireflychat.billing.v1.GrantPublicTokensRequest;
import fireflychat.billing.v1.GrantPublicTokensResponse;
import fireflychat.billing.v1.ProjectBalance;
import fireflychat.billing.v1.RedeemCodeRequest;
import fireflychat.billing.v1.RedeemCodeResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class BillingGrpcClient {

    private final ExternalServiceProperties properties;
    private final ConsulServiceResolver resolver;

    private volatile EndpointClient client;

    public BillingGrpcClient(ExternalServiceProperties properties, ConsulServiceResolver resolver) {
        this.properties = properties;
        this.resolver = resolver;
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
                response.getTokensGranted(),
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
        long total = balanceFromProject(response.getProjectBalance()) + response.getPublicPermanentTokens();
        return new RedeemResult(
                response.getSuccess(),
                response.getTokensGranted(),
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
                response.getTokensGrantedToday()
        );
    }

    public long totalBalance(long remoteUserId) {
        var balanceStub = stubs().balanceStub().withDeadlineAfter(timeoutMs(), TimeUnit.MILLISECONDS);
        long publicTokens = balanceStub.getPublicBalance(GetPublicBalanceRequest.newBuilder()
                        .setUserId(remoteUserId)
                        .build())
                .getPublicPermanentTokens();
        var project = balanceStub.getProjectBalance(GetProjectBalanceRequest.newBuilder()
                        .setProjectKey(properties.getProjectKey())
                        .setUserId(remoteUserId)
                        .build())
                .getBalance();
        return publicTokens + balanceFromProject(project);
    }

    public long publicBalance(long remoteUserId) {
        return stubs().balanceStub()
                .withDeadlineAfter(timeoutMs(), TimeUnit.MILLISECONDS)
                .getPublicBalance(GetPublicBalanceRequest.newBuilder()
                        .setUserId(remoteUserId)
                        .build())
                .getPublicPermanentTokens();
    }

    public ConversionResult convertPublicToProject(long remoteUserId, long amount, String requestId) {
        ConvertPublicToProjectResponse response = stubs().conversionStub()
                .withDeadlineAfter(timeoutMs(), TimeUnit.MILLISECONDS)
                .convertPublicToProject(ConvertPublicToProjectRequest.newBuilder()
                        .setRequestId(requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId)
                        .setUserId(remoteUserId)
                        .setProjectKey(properties.getProjectKey())
                        .setPublicTokens(amount)
                        .build());
        return new ConversionResult(
                response.getSuccess(),
                response.getConvertedPublicTokens(),
                response.getConvertedProjectTokens(),
                response.getPublicPermanentTokens(),
                response.getErrorMessage()
        );
    }

    public boolean grantPublicTokens(long remoteUserId, long amount, String reason) {
        GrantPublicTokensResponse response = stubs().grantStub()
                .withDeadlineAfter(timeoutMs(), TimeUnit.MILLISECONDS)
                .grantPublicTokens(GrantPublicTokensRequest.newBuilder()
                        .setRequestId(UUID.randomUUID().toString())
                        .setUserId(remoteUserId)
                        .setTokens(amount)
                        .setReason(reason == null ? "" : reason)
                        .build());
        return response.getSuccess();
    }

    private long balanceFromProject(ProjectBalance balance) {
        if (balance == null) {
            return 0L;
        }
        return balance.getTempTokens() + balance.getPermanentTokens();
    }

    private Instant toInstant(Timestamp timestamp) {
        if (timestamp == null || (timestamp.getSeconds() == 0 && timestamp.getNanos() == 0)) {
            return null;
        }
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    private EndpointClient stubs() {
        return getOrCreateClient();
    }

    private synchronized EndpointClient getOrCreateClient() {
        ExternalServiceProperties.ServiceTarget target = properties.getPayserviceGrpc();
        ConsulServiceResolver.Endpoint endpoint = resolver
                .resolveOrFallback(target.getServiceName(), target.getFallback())
                .orElseThrow(() -> new IllegalStateException("No endpoint for payservice-grpc"));
        EndpointClient existing = client;
        if (existing != null && existing.sameEndpoint(endpoint.host(), endpoint.port())) {
            return existing;
        }

        ManagedChannel channel = ManagedChannelBuilder.forAddress(endpoint.host(), endpoint.port())
                .usePlaintext()
                .build();
        EndpointClient next = new EndpointClient(
                endpoint.host(),
                endpoint.port(),
                channel,
                BillingCheckinServiceGrpc.newBlockingStub(channel),
                BillingRedeemCodeServiceGrpc.newBlockingStub(channel),
                BillingBalanceServiceGrpc.newBlockingStub(channel),
                BillingConversionServiceGrpc.newBlockingStub(channel),
                BillingGrantServiceGrpc.newBlockingStub(channel)
        );
        if (existing != null) {
            existing.close();
        }
        client = next;
        return next;
    }

    private long timeoutMs() {
        return Math.max(800L, properties.getTimeoutMs());
    }

    @PreDestroy
    public synchronized void shutdown() {
        if (client != null) {
            client.close();
            client = null;
        }
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

    private record EndpointClient(
            String host,
            int port,
            ManagedChannel channel,
            BillingCheckinServiceGrpc.BillingCheckinServiceBlockingStub checkinStub,
            BillingRedeemCodeServiceGrpc.BillingRedeemCodeServiceBlockingStub redeemStub,
            BillingBalanceServiceGrpc.BillingBalanceServiceBlockingStub balanceStub,
            BillingConversionServiceGrpc.BillingConversionServiceBlockingStub conversionStub,
            BillingGrantServiceGrpc.BillingGrantServiceBlockingStub grantStub
    ) {
        boolean sameEndpoint(String targetHost, int targetPort) {
            return host.equals(targetHost) && port == targetPort;
        }

        void close() {
            channel.shutdownNow();
        }
    }
}
