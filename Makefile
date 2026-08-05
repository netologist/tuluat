.PHONY: all build test crd-gen deploy test-agent clean

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

deploy:
	@echo "Deploying CRDs and Sample Resources to Kubernetes..."
	./scripts/deploy-operator.sh

test-agent:
	@echo "Testing deployed AiAgent Ingress endpoint..."
	./scripts/test-agent.sh

clean:
	@echo "Cleaning target directory..."
	$(MAVEN) clean
