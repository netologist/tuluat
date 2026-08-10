package com.tuluat.app.controller;

import com.tuluat.crd.workflow.AiWorkflow;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

	private final KubernetesClient kubernetesClient;

	@Autowired
	public WorkflowController(KubernetesClient kubernetesClient) {
		this.kubernetesClient = kubernetesClient;
	}

	@GetMapping
	public ResponseEntity<List<Map<String, Object>>> listWorkflows(@RequestParam(required = false) String namespace) {
		String ns = (namespace != null && !namespace.isBlank()) ? namespace : "tuluat-system";
		List<AiWorkflow> items = kubernetesClient.resources(AiWorkflow.class).inNamespace(ns).list().getItems();

		if (items.isEmpty()) {
			items = kubernetesClient.resources(AiWorkflow.class).inNamespace("default").list().getItems();
		}

		List<Map<String, Object>> response = items.stream().map(wf -> {
			Map<String, Object> map = new HashMap<>();
			map.put("name", wf.getMetadata().getName());
			map.put("namespace", wf.getMetadata().getNamespace());
			map.put("description", wf.getSpec() != null ? wf.getSpec().description() : "");
			map.put("initialNode", wf.getSpec() != null ? wf.getSpec().initialNode() : "");
			map.put("nodeCount",
					(wf.getSpec() != null && wf.getSpec().nodes() != null) ? wf.getSpec().nodes().size() : 0);
			map.put("nodes", wf.getSpec() != null ? wf.getSpec().nodes() : List.of());
			map.put("edges", wf.getSpec() != null ? wf.getSpec().edges() : List.of());
			map.put("memoryConfig", wf.getSpec() != null ? wf.getSpec().memoryConfig() : null);
			return map;
		}).collect(Collectors.toList());

		return ResponseEntity.ok(response);
	}

	@GetMapping("/{name}")
	public ResponseEntity<Map<String, Object>> getWorkflow(@PathVariable String name,
			@RequestParam(required = false) String namespace) {
		String ns = (namespace != null && !namespace.isBlank()) ? namespace : "tuluat-system";
		AiWorkflow wf = kubernetesClient.resources(AiWorkflow.class).inNamespace(ns).withName(name).get();

		if (wf == null) {
			wf = kubernetesClient.resources(AiWorkflow.class).inNamespace("default").withName(name).get();
		}

		if (wf == null) {
			return ResponseEntity.notFound().build();
		}

		Map<String, Object> map = new HashMap<>();
		map.put("name", wf.getMetadata().getName());
		map.put("namespace", wf.getMetadata().getNamespace());
		map.put("spec", wf.getSpec());
		map.put("status", wf.getStatus());
		return ResponseEntity.ok(map);
	}
}
