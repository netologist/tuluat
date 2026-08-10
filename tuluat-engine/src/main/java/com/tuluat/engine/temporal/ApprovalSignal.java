package com.tuluat.engine.temporal;

import java.util.HashMap;
import java.util.Map;

public class ApprovalSignal {

	private boolean approved;
	private String feedback;
	private Map<String, Object> metadata = new HashMap<>();

	public ApprovalSignal() {
	}

	public ApprovalSignal(boolean approved, String feedback, Map<String, Object> metadata) {
		this.approved = approved;
		this.feedback = feedback;
		if (metadata != null) {
			this.metadata = metadata;
		}
	}

	public boolean isApproved() {
		return approved;
	}
	public void setApproved(boolean approved) {
		this.approved = approved;
	}
	public String getFeedback() {
		return feedback;
	}
	public void setFeedback(String feedback) {
		this.feedback = feedback;
	}
	public Map<String, Object> getMetadata() {
		return metadata;
	}
	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}
}
