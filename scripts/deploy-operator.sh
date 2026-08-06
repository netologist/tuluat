#!/usr/bin/env bash
set -euo pipefail

echo "=========================================="
echo " Deploying K8s AI Operator & Custom Resources"
echo "=========================================="

NAMESPACE=${1:-default}

echo "1. Applying Custom Resource Definitions (CRDs)..."
kubectl apply -f manifests/crd/llmproviders.ai.tuluat.com.yaml
kubectl apply -f manifests/crd/aiagents.ai.tuluat.com.yaml
kubectl apply -f manifests/crd/aiworkflows.ai.tuluat.com.yaml
kubectl apply -f manifests/crd/workflowsessions.ai.tuluat.com.yaml

echo "2. Applying RBAC, ServiceAccount, and Database..."
kubectl apply -f manifests/operator/rbac.yaml -n "$NAMESPACE"
kubectl apply -f config/samples/00_postgres_pgvector.yaml -n "$NAMESPACE"

echo "3. Applying Sample Custom Resources..."
kubectl apply -f config/samples/01_secret_openai.yaml -n "$NAMESPACE"
kubectl apply -f config/samples/01_secret_deepseek.yaml -n "$NAMESPACE"
kubectl apply -f config/samples/02_llmprovider_openai.yaml -n "$NAMESPACE"
kubectl apply -f config/samples/03_llmprovider_deepseek.yaml -n "$NAMESPACE"
kubectl apply -f config/samples/04_aiagent_sample.yaml -n "$NAMESPACE"
kubectl apply -f config/samples/05_aiworkflow_sample.yaml -n "$NAMESPACE"
kubectl apply -f config/samples/06_workflowsession_sample.yaml -n "$NAMESPACE"

echo "4. Checking status of CRDs and Custom Resources..."
kubectl get crds | grep ai.tuluat.com || true
kubectl get llmproviders -n "$NAMESPACE" || true
kubectl get aiagents -n "$NAMESPACE" || true
kubectl get aiworkflows -n "$NAMESPACE" || true
kubectl get workflowsessions -n "$NAMESPACE" || true

echo "Deployment finished successfully."
