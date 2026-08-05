#!/usr/bin/env bash
set -euo pipefail

echo "=========================================="
echo " Deploying K8s AI Operator & Custom Resources"
echo "=========================================="

NAMESPACE=${1:-default}

echo "1. Applying Custom Resource Definitions (CRDs)..."
kubectl apply -f manifests/crd/llmproviders.ai.example.com.yaml
kubectl apply -f manifests/crd/aiagents.ai.example.com.yaml

echo "2. Applying RBAC and ServiceAccount..."
kubectl apply -f manifests/operator/rbac.yaml -n "$NAMESPACE"

echo "3. Applying Sample Custom Resources..."
kubectl apply -f config/samples/01_secret_openai.yaml -n "$NAMESPACE"
kubectl apply -f config/samples/02_llmprovider_openai.yaml -n "$NAMESPACE"
kubectl apply -f config/samples/03_llmprovider_ollama.yaml -n "$NAMESPACE"
kubectl apply -f config/samples/04_aiagent_sample.yaml -n "$NAMESPACE"

echo "4. Checking status of CRDs and Custom Resources..."
kubectl get crds | grep ai.example.com || true
kubectl get llmproviders -n "$NAMESPACE"
kubectl get aiagents -n "$NAMESPACE"

echo "Deployment finished successfully."
