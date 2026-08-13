#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME="tuluat-cluster"
IMAGE_NAME="tuluat-operator:latest"

echo "=========================================================="
echo " Setting up Kind Kubernetes Cluster: ${CLUSTER_NAME}"
echo "=========================================================="

# Check prerequisites
command -v kind >/dev/null 2>&1 || { echo "Error: 'kind' is not installed. Please install kind first."; exit 1; }
command -v docker >/dev/null 2>&1 || { echo "Error: 'docker' is not installed or running."; exit 1; }
command -v kubectl >/dev/null 2>&1 || { echo "Error: 'kubectl' is not installed."; exit 1; }

# 1. Create Kind cluster configuration with Ingress extraPortMappings
echo "1. Writing Kind Cluster configuration with Ingress support..."
cat <<EOF > kind-config.yaml
apiVersion: kind.x-k8s.io/v1alpha4
kind: Cluster
nodes:
- role: control-plane
  kubeadmConfigPatches:
  - |
    apiVersion: kubeadm.k8s.io/v1beta3
    kind: ClusterConfiguration
    apiServer:
      extraArgs:
        feature-gates: "DynamicResourceAllocation=true"
  extraPortMappings:
  - containerPort: 80
    hostPort: 80
    protocol: TCP
  - containerPort: 443
    hostPort: 443
    protocol: TCP
EOF

# 2. Check if cluster already exists, or create it
if kind get clusters | grep -q "^${CLUSTER_NAME}$"; then
  echo "Cluster '${CLUSTER_NAME}' already exists."
else
  echo "Creating Kind cluster '${CLUSTER_NAME}'..."
  kind create cluster --name "${CLUSTER_NAME}" --config kind-config.yaml
fi

# 3. Install NGINX Ingress Controller for Kind
echo "2. Installing NGINX Ingress Controller..."
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

echo "Waiting for NGINX Ingress Controller pods to be ready..."
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=360s || echo "Ingress controller setup continuing..."

# 4. Build local Docker image
echo "3. Building Operator Docker image: ${IMAGE_NAME}..."
docker build -t "${IMAGE_NAME}" .

# 5. Load Docker image into Kind cluster
echo "4. Loading Docker image into Kind cluster..."
kind load docker-image "${IMAGE_NAME}" --name "${CLUSTER_NAME}"

# 6. Deploy CRDs, RBAC, Operator, and Custom Resources
echo "5. Deploying CRDs, RBAC, and Custom Resources..."
./scripts/deploy-operator.sh tuluat-system

echo "6. Deploying Operator in Kubernetes..."
kubectl apply -f manifests/operator/deployment.yaml -n tuluat-system

echo "=========================================================="
echo " Kind Cluster Setup Completed Successfully!"
echo " Ingress Host Mapping: Add '127.0.0.1 ai-agent.tuluat.com' to /etc/hosts"
echo "=========================================================="
