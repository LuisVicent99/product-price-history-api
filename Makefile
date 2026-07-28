IMAGE := product-price-history-api:native

.PHONY: build up perf test verify-native stats clean

build:
	docker build -f src/main/docker/Dockerfile.native -t $(IMAGE) .

up:
	bash scripts/startup-time.sh

perf: up
	docker compose run --rm k6

test:
	./mvnw test

verify-native:
	./mvnw verify -Dnative -Dquarkus.native.container-build=true -Dquarkus.container-image.build=true

stats:
	docker stats --no-stream pricing-app

clean:
	docker compose --profile perf down -v --remove-orphans
	./mvnw clean
