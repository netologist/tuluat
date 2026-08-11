---
name: k8s-operator-guide
description: Guidelines and best practices for developing Kubernetes AI Operators, JOSDK reconcilers, and CRD lifecycle management. Use when writing operator reconcilers or designing CRDs.
license: Apache-2.0
compatibility: Designed for Java 25 LTS, Spring Boot 4, JOSDK 5.x, and Fabric8 Kubernetes Client 7.x
metadata:
  author: tuluat-team
  version: "1.0.0"
---

# Kubernetes AI Operator Guidelines

This skill provides step-by-step instructions for developing Java-based Kubernetes AI Operators.

## 1. Reconciler Design Principles

When implementing a Java Operator SDK (`Reconciler<T>`) controller:
- **Idempotency**: Reconcile loops must be idempotent. Repeating execution with unchanged inputs should produce identical cluster state.
- **Status Subresources**: Always update status using `UpdateControl.patchStatus(resource)` rather than rewriting spec.
- **Owner References**: Set owner references on all created secondary resources (Deployments, Services, Ingresses) to enable Kubernetes garbage collection.

## 2. Status Phases

- `Ready`: All secondary resources are healthy and operational.
- `Reconciling`: Waiting for dependent resources (e.g. `LlmProvider` or `Secret`).
- `Failed`: Unrecoverable validation or API error.

## 3. Reference Documentation

Refer to [reconciliation guidelines](references/reconciliation.md) for detailed error handling patterns.
