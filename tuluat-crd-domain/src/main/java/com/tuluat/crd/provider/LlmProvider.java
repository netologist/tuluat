package com.tuluat.crd.provider;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("ai.tuluat.com")
@Version("v1alpha1")
@Kind("LlmProvider")
@Plural("llmproviders")
@ShortNames("lp")
public class LlmProvider extends CustomResource<LlmProviderSpec, LlmProviderStatus> implements Namespaced {
}
