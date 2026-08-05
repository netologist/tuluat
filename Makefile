.PHONY: all build test crd-gen docker-build kind-cluster deploy test-agent clean

MAVEN := ./mvnw
ifeq ($(wildcard ./mvnw),)
	MAVEN := mvn
endif

all: build test

build:
	@echo "Building application with Java 26 preview features..."
	$(MAVEN) clean package -DskipTests

test:
	@echo "Running unit and integration tests..."
	$(MAVEN) test

crd-gen:
	@echo "Generating CRD manifests..."
	./scripts/generate-crds.sh

docker-build:
	@echo "Building Docker image for K8s AI Operator..."
	docker build -t k8s-crd-ai-operator:latest .

kind-cluster:
	@echo "Setting up local Kind Kubernetes cluster and deploying Operator..."
	./scripts/create-kind-cluster.sh

deploy:
	@echo "Deploying CRDs and Sample Resources to Kubernetes..."
	./scripts/deploy-operator.sh

test-agent:
	@echo "Testing deployed AiAgent Ingress endpoint..."
	./scripts/test-agent.sh

clean:
	@echo "Cleaning target directory..."
	$(MAVEN) clean
