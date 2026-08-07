package com.tuluat.crd.workflow;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("ai.tuluat.com")
@Version("v1alpha1")
public class AiWorkflow extends CustomResource<AiWorkflowSpec, AiWorkflowStatus> implements Namespaced {
}
