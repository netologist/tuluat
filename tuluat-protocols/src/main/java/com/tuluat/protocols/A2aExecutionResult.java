package com.tuluat.protocols;

/**
 * Result of a remote A2A execution / handoff.
 *
 * @param success   whether remote execution succeeded
 * @param result    remote output content
 * @param remoteAgentId id of the executing remote agent
 * @param error     error message when unsuccessful
 */
public record A2aExecutionResult(
    boolean success,
    String result,
    String remoteAgentId,
    String error
) {
    public static A2aExecutionResult ok(String remoteAgentId, String result) {
        return new A2aExecutionResult(true, result, remoteAgentId, null);
    }

    public static A2aExecutionResult failure(String remoteAgentId, String error) {
        return new A2aExecutionResult(false, null, remoteAgentId, error);
    }
}
