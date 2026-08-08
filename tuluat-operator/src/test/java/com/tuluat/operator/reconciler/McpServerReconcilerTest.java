package com.tuluat.operator.reconciler;

import com.tuluat.crd.mcp.McpServer;
import com.tuluat.crd.mcp.McpServerSpec;
import com.tuluat.protocols.McpClientRegistry;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class McpServerReconcilerTest {

    private McpClientRegistry registry;
    private McpServerReconciler reconciler;

    @BeforeEach
    void setUp() {
        registry = mock(McpClientRegistry.class);
        reconciler = new McpServerReconciler(registry);
    }

    @Test
    @DisplayName("Should register MCP client and set status to Ready")
    void testReconcileSuccess() {
        var server = new McpServer();
        server.setMetadata(new ObjectMetaBuilder().withName("postgres-mcp").withNamespace("tuluat-system").withGeneration(1L).build());
        server.setSpec(new McpServerSpec(
            "http://postgres-mcp:8080/sse", "SSE", "NONE", null, 30, "PostgreSQL tools"));

        UpdateControl<McpServer> control = reconciler.reconcile(server, null);

        assertNotNull(control);
        assertNotNull(server.getStatus());
        assertEquals("Ready", server.getStatus().phase());
        assertTrue(server.getStatus().message().contains("postgres-mcp"));
        verify(registry).registerFromCr(server);
    }

    @Test
    @DisplayName("Should set Error status when endpoint is missing")
    void testReconcileMissingEndpoint() {
        var server = new McpServer();
        server.setMetadata(new ObjectMetaBuilder().withName("bad-mcp").withNamespace("tuluat-system").withGeneration(1L).build());
        server.setSpec(new McpServerSpec(null, "SSE", "NONE", null, 30, null));

        UpdateControl<McpServer> control = reconciler.reconcile(server, null);

        assertEquals("Error", server.getStatus().phase());
        assertTrue(server.getStatus().message().contains("endpoint"));
    }

    @Test
    @DisplayName("Should unregister client on delete")
    void testCleanupUnregisters() {
        var server = new McpServer();
        server.setMetadata(new ObjectMetaBuilder().withName("github-mcp").withNamespace("tuluat-system").build());

        reconciler.cleanup(server, null);

        verify(registry).unregisterClient("github-mcp");
    }

    @Test
    @DisplayName("Should expose registered clients through registry snapshot")
    void testRegistryIntegration() {
        var server = new McpServer();
        server.setMetadata(new ObjectMetaBuilder().withName("slack-mcp").withNamespace("tuluat-system").build());
        server.setSpec(new McpServerSpec(
            "http://slack-mcp:8080/sse", "SSE", "API_KEY", null, 45, "Slack tools"));

        // Spy-style: use a real in-memory registry to verify end-to-end registration
        var realRegistry = new com.tuluat.protocols.McpClientRegistryImpl();
        var realReconciler = new McpServerReconciler(realRegistry);

        realReconciler.reconcile(server, null);
        Map<String, com.tuluat.protocols.McpClientConnection> clients = realRegistry.getRegisteredClients();

        assertTrue(clients.containsKey("slack-mcp"));
        assertEquals("http://slack-mcp:8080/sse", clients.get("slack-mcp").endpoint());
        assertEquals("SSE", clients.get("slack-mcp").transport());

        realReconciler.cleanup(server, null);
        assertFalse(realRegistry.getRegisteredClients().containsKey("slack-mcp"));
    }
}
