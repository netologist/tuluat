package com.tuluat.ai.crd.agent;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("ai.tuluat.com")
@Version("v1alpha1")
@Kind("AiAgent")
@Plural("aiagents")
@ShortNames("agent")
public class AiAgent extends CustomResource<AiAgentSpec, AiAgentStatus> implements Namespaced {
}
