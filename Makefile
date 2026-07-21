.PHONY: build

build:
	./mvnw -Pnative clean package -DskipTests
