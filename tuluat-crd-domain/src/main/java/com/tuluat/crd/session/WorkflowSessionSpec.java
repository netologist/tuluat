package com.tuluat.crd.session;

import java.util.HashMap;
import java.util.Map;

public class WorkflowSessionSpec {
	private String workflowRef;
	private String input;
	private Map<String, Object> parameters = new HashMap<>();

	public String getWorkflowRef() {
		return workflowRef;
	}
	public void setWorkflowRef(String workflowRef) {
		this.workflowRef = workflowRef;
	}
	public String getInput() {
		return input;
	}
	public void setInput(String input) {
		this.input = input;
	}
	public Map<String, Object> getParameters() {
		return parameters;
	}
	public void setParameters(Map<String, Object> parameters) {
		this.parameters = parameters;
	}
}
