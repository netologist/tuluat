package com.tuluat.crd.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TlsSpec(
    @JsonProperty("secretName") String secretName,
    @JsonProperty("hosts") List<String> hosts
) {
}
