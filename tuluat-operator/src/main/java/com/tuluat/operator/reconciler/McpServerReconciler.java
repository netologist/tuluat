package com.tuluat.operator.reconciler;

import com.tuluat.crd.mcp.McpServer;
import com.tuluat.crd.mcp.McpServerStatus;
import com.tuluat.protocols.McpClientRegistry;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JOSDK Reconciler for McpServer Custom Resources: keeps the
 * {@link McpClientRegistry} in sync with declared MCP servers (ADR 003 / 007).
 * On reconcile the client connection is (re)registered; on delete it is removed.
 */
@Component
@ControllerConfiguration(name = "mcpserver-reconciler")
public class McpServerReconciler implements Reconciler<McpServer>, Cleaner<McpServer> {
    private static final Logger log = LoggerFactory.getLogger(McpServerReconciler.class);

    private final McpClientRegistry clientRegistry;

    @Autowired
    public McpServerReconciler(McpClientRegistry clientRegistry) {
        this.clientRegistry = clientRegistry;
    }

    @Override
    public UpdateControl<McpServer> reconcile(McpServer resource, Context<McpServer> context) {
        String name = resource.getMetadata().getName();
        String ns = resource.getMetadata().getNamespace();
        log.info("Reconciling McpServer resource: {}/{}", ns, name);

        try {
            var spec = resource.getSpec();
            if (spec == null || spec.endpoint() == null || spec.endpoint().isBlank()) {
                resource.setStatus(McpServerStatus.error("Spec.endpoint cannot be null", resource.getMetadata().getGeneration()));
                return UpdateControl.patchStatus(resource);
            }

            clientRegistry.registerFromCr(resource);

            String msg = String.format("MCP Server [%s] registered (endpoint: %s, transport: %s)",
                name, spec.endpoint(), spec.transport());
            resource.setStatus(McpServerStatus.ready(msg, resource.getMetadata().getGeneration()));
            log.info("McpServer successfully reconciled: {}", msg);
            return UpdateControl.patchStatus(resource);
        } catch (Exception e) {
            log.error("Error reconciling McpServer {}", name, e);
            resource.setStatus(McpServerStatus.error("Reconciliation error: " + e.getMessage(), resource.getMetadata().getGeneration()));
            return UpdateControl.patchStatus(resource);
        }
    }

    @Override
    public DeleteControl cleanup(McpServer resource, Context<McpServer> context) {
        String name = resource.getMetadata().getName();
        clientRegistry.unregisterClient(name);
        log.info("McpServer [{}] deleted; client unregistered", name);
        return DeleteControl.defaultDelete();
    }
}
