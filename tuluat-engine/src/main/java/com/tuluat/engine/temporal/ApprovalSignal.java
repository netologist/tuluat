package com.tuluat.engine.temporal;

import java.util.Map;

public record ApprovalSignal(boolean approved, String feedback, Map<String, Object> metadata) {
}
