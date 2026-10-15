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
comma := ,
# Runs a `container build` and stops the builder afterwards, also on failure, keeping the build's exit code.
BUILD_AND_STOP = status=0; $(1) || status=$$?; container builder stop; exit $$status
GRADLE_RUN := container run --rm -m 6G -c 4 -v "$(CURDIR):/src" -w /src -e GRADLE_USER_HOME=/src/.gradle-home

.PHONY: help build run test screenshots screenshots-image clean

help:
	@echo "make build        package the app into $(DIST) (runs core tests)"
	@echo "make run          start the macOS app bundle (builds first if missing)"
	@echo "make test         run core tests and compile the app"
	@echo "make screenshots  app tests: i18n checks + UI screenshots in app/build/screenshots"
	@echo "make clean        remove build outputs"

vendor/target-jdk.tar.gz:
	mkdir -p vendor
	curl -fL -o $@ "$(JDK_URL)"

build: vendor/target-jdk.tar.gz
	rm -rf dist
	$(call BUILD_AND_STOP,container build -m 8G -c 4 --build-arg COMPOSE_TARGET=$(COMPOSE_TARGET) --output type=local$(comma)dest=dist .)

$(DIST)/run.sh:
	$(MAKE) build

# Runs the macOS app bundle so the menu bar and Dock show the app name instead of "java";
# the bundle's own launcher keeps the log output in this terminal.
dist/mac/E-Signer.app: $(DIST)/run.sh
	tools/package-mac.sh

run: dist/mac/E-Signer.app
	dist/mac/E-Signer.app/Contents/MacOS/E-Signer

test:
	mkdir -p .gradle-home
	$(GRADLE_RUN) gradle:8-jdk21 gradle --no-daemon -PcomposeTarget=$(COMPOSE_TARGET) :core:test :app:compileKotlin

screenshots-image:
	$(call BUILD_AND_STOP,container build -m 8G -c 4 -t esigner-screenshots -f tools/screenshots.Dockerfile tools)

screenshots: screenshots-image
	mkdir -p .gradle-home
	$(GRADLE_RUN) esigner-screenshots gradle --no-daemon -PcomposeTarget=linux-$(subst amd64,x64,$(CONTAINER_ARCH)) :app:test

clean:
	rm -rf dist build core/build app/build
