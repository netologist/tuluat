package com.tuluat.crd.session;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("ai.tuluat.com")
@Version("v1alpha1")
public class WorkflowSession extends CustomResource<WorkflowSessionSpec, WorkflowSessionStatus> implements Namespaced {
}
