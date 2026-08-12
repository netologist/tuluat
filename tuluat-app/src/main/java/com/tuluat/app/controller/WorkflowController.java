package com.tuluat.app.controller;

import com.tuluat.app.config.KubernetesResourceResolver;
import com.tuluat.crd.workflow.AiWorkflow;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

	private final KubernetesResourceResolver resolver;

	public WorkflowController(KubernetesResourceResolver resolver) {
		this.resolver = resolver;
	}

	@GetMapping
	public ResponseEntity<List<Map<String, Object>>> listWorkflows(@RequestParam(required = false) String namespace) {
		List<Map<String, Object>> response = resolver.list(AiWorkflow.class, namespace).stream().map(wf -> {
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
		AiWorkflow wf = resolver.get(AiWorkflow.class, namespace, name);
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
