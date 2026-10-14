# Builds run in Apple container; nothing but `container` is needed on the Mac.
# Memory flags are required: with the defaults (1 GB run, 2 GB build) Gradle stalls.

ARCH := $(shell uname -m)
ifeq ($(ARCH),arm64)
  JDK_ARCH := aarch64
  COMPOSE_TARGET := macos-arm64
  CONTAINER_ARCH := arm64
else
  JDK_ARCH := x64
  COMPOSE_TARGET := macos-x64
  CONTAINER_ARCH := amd64
endif

JDK_URL := https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_$(JDK_ARCH)_mac_hotspot_21.0.12.1_1.tar.gz
DIST := dist/linux_$(CONTAINER_ARCH)
GRADLE_RUN := container run --rm -m 6G -c 4 -v "$(CURDIR):/src" -w /src -e GRADLE_USER_HOME=/src/.gradle-home

.PHONY: help build run test screenshots screenshots-image clean

help:
	@echo "make build        package the app into $(DIST) (runs core tests)"
	@echo "make run          start the packaged app (builds first if missing)"
	@echo "make test         run core tests and compile the app"
	@echo "make screenshots  app tests: i18n checks + UI screenshots in app/build/screenshots"
	@echo "make clean        remove build outputs"

vendor/target-jdk.tar.gz:
	mkdir -p vendor
	curl -fL -o $@ "$(JDK_URL)"

build: vendor/target-jdk.tar.gz
	rm -rf dist
	container build -m 8G -c 4 --build-arg COMPOSE_TARGET=$(COMPOSE_TARGET) --output type=local,dest=dist .

$(DIST)/run.sh:
	$(MAKE) build

run: $(DIST)/run.sh
	$(DIST)/run.sh

test:
	mkdir -p .gradle-home
	$(GRADLE_RUN) gradle:8-jdk21 gradle --no-daemon -PcomposeTarget=$(COMPOSE_TARGET) :core:test :app:compileKotlin

screenshots-image:
	container build -m 8G -c 4 -t esigner-screenshots -f tools/screenshots.Dockerfile tools

screenshots: screenshots-image
	mkdir -p .gradle-home
	$(GRADLE_RUN) esigner-screenshots gradle --no-daemon -PcomposeTarget=linux-$(subst amd64,x64,$(CONTAINER_ARCH)) :app:test

clean:
	rm -rf dist build core/build app/build
