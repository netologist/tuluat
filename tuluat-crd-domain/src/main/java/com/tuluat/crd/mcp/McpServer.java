package com.tuluat.crd.mcp;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

/**
 * McpServer Custom Resource: external Model Context Protocol server
 * registration.
 */
@Group("ai.tuluat.com")
@Version("v1alpha1")
@Kind("McpServer")
@Plural("mcpservers")
@ShortNames("mcp")
public class McpServer extends CustomResource<McpServerSpec, McpServerStatus> implements Namespaced {
}
