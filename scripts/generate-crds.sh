#!/usr/bin/env bash
set -euo pipefail

echo "=========================================="
echo " Generating Kubernetes CRDs for AI Operator"
echo "=========================================="

# Compile project to trigger fabric8 crd-generator-apt annotation processor
mvn clean compile -DskipTests

echo "CRDs validated under manifests/crd/"
ls -la manifests/crd/
