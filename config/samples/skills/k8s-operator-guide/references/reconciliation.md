# Reconciliation Error Handling & Status Patches

## Error Handling Pattern

```java
try {
    // 1. Reconcile secondary resources
    reconcileDeployment(resource, ownerRef, ns);
    reconcileService(resource, ownerRef, ns);
    
    // 2. Patch Ready status
    resource.setStatus(AiAgentStatus.ready(url, activeSkills, activeTools, model, "Ready", gen));
    return UpdateControl.patchStatus(resource);
} catch (Exception e) {
    resource.setStatus(AiAgentStatus.failed("Failure: " + e.getMessage(), gen));
    return UpdateControl.patchStatus(resource);
}
```
