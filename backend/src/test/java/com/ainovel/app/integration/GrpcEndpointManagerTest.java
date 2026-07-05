package com.ainovel.app.integration;

import io.grpc.ManagedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrpcEndpointManagerTest {

    @Test
    void getOrCreateShouldReuseExistingClientForSameEndpoint() {
        ExternalServiceProperties.ServiceTarget target = new ExternalServiceProperties.ServiceTarget();
        target.setAddress("static://payservice.seekerhut.com:443");
        GrpcChannelFactory channelFactory = mock(GrpcChannelFactory.class);
        ManagedChannel channel = mock(ManagedChannel.class);
        when(channelFactory.create("payservice.seekerhut.com", 443)).thenReturn(channel);

        GrpcEndpointManager<FakeClient> manager = new GrpcEndpointManager<>(target, "payservice-grpc", channelFactory);

        FakeClient first = manager.getOrCreate((endpoint, createdChannel) -> new FakeClient(endpoint.host(), endpoint.port(), createdChannel));
        FakeClient second = manager.getOrCreate((endpoint, createdChannel) -> new FakeClient(endpoint.host(), endpoint.port(), createdChannel));

        assertSame(first, second);
        verify(channelFactory, times(1)).create("payservice.seekerhut.com", 443);
    }

    @Test
    void getOrCreateShouldReplaceAndCloseClientWhenEndpointChanges() {
        ExternalServiceProperties.ServiceTarget target = new ExternalServiceProperties.ServiceTarget();
        target.setAddress("static://payservice.seekerhut.com:443");
        GrpcChannelFactory channelFactory = mock(GrpcChannelFactory.class);
        ManagedChannel firstChannel = mock(ManagedChannel.class);
        ManagedChannel secondChannel = mock(ManagedChannel.class);
        when(channelFactory.create("payservice.seekerhut.com", 443)).thenReturn(firstChannel);
        when(channelFactory.create("payservice-backup.seekerhut.com", 8443)).thenReturn(secondChannel);

        GrpcEndpointManager<FakeClient> manager = new GrpcEndpointManager<>(target, "payservice-grpc", channelFactory);

        FakeClient first = manager.getOrCreate((endpoint, createdChannel) -> new FakeClient(endpoint.host(), endpoint.port(), createdChannel));
        target.setAddress("static://payservice-backup.seekerhut.com:8443");
        FakeClient second = manager.getOrCreate((endpoint, createdChannel) -> new FakeClient(endpoint.host(), endpoint.port(), createdChannel));

        assertTrue(first.closed);
        assertTrue(!second.closed);
        verify(firstChannel).shutdownNow();
    }

    @Test
    void shutdownShouldCloseCurrentClient() {
        ExternalServiceProperties.ServiceTarget target = new ExternalServiceProperties.ServiceTarget();
        target.setAddress("static://aiservice.seekerhut.com:443");
        GrpcChannelFactory channelFactory = mock(GrpcChannelFactory.class);
        ManagedChannel channel = mock(ManagedChannel.class);
        when(channelFactory.create("aiservice.seekerhut.com", 443)).thenReturn(channel);

        GrpcEndpointManager<FakeClient> manager = new GrpcEndpointManager<>(target, "aiservice-grpc", channelFactory);

        FakeClient client = manager.getOrCreate((endpoint, createdChannel) -> new FakeClient(endpoint.host(), endpoint.port(), createdChannel));
        manager.shutdown();

        assertTrue(client.closed);
        verify(channel).shutdownNow();
    }

    private static final class FakeClient implements GrpcEndpointManager.ManagedClient {
        private final String host;
        private final int port;
        private final ManagedChannel channel;
        private boolean closed;

        private FakeClient(String host, int port, ManagedChannel channel) {
            this.host = host;
            this.port = port;
            this.channel = channel;
        }

        @Override
        public String host() {
            return host;
        }

        @Override
        public int port() {
            return port;
        }

        @Override
        public void close() {
            closed = true;
            channel.shutdownNow();
        }
    }
}
