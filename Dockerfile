# syntax=docker/dockerfile:1
#
# Builds the desktop app for a macOS target inside a Linux container (no local Java/Gradle needed).
#
# Once, download the target JDK (Apple Silicon; for Intel use x64 instead of aarch64 and
# --build-arg COMPOSE_TARGET=macos-x64):
#   mkdir -p vendor && curl -fL -o vendor/target-jdk.tar.gz \
#     "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.12.1_1.tar.gz"
# Then:
#   container build -m 8G -c 4 --output type=local,dest=dist .
# (-m/-c are required: without them the builder is recreated with the 2 GB default and Gradle stalls)
#
# Output: dist/linux_arm64/ -> run.sh + lib/ (app) + runtime/ (jlink'ed Java for the target platform)

# 1) Test core and build the app (jars; only Skiko's native part is platform specific)
FROM gradle:8-jdk21 AS build
ARG COMPOSE_TARGET=macos-arm64
WORKDIR /src
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY core ./core
COPY app ./app
# Cache mount keeps downloaded dependencies between builds (otherwise ~500 MB on every source change).
RUN --mount=type=cache,target=/gradle-cache \
    GRADLE_USER_HOME=/gradle-cache gradle --no-daemon -PcomposeTarget=${COMPOSE_TARGET} :core:test :app:installDist

# 2) Target platform runtime via jlink. jlink must be the exact same version as the target JDK.
FROM eclipse-temurin:21.0.12.1_1-jdk AS runtime
COPY vendor/target-jdk.tar.gz /tmp/target-jdk.tar.gz
RUN set -eux; \
    mkdir -p /tmp/target-jdk; \
    tar -xzf /tmp/target-jdk.tar.gz -C /tmp/target-jdk; \
    JMODS="$(find /tmp/target-jdk -type d -name jmods | head -1)"; \
    test -n "$JMODS"; \
    jlink --module-path "$JMODS" \
          --add-modules ALL-MODULE-PATH \
          --strip-debug --no-man-pages --no-header-files --compress=zip-6 \
          --output /out/runtime
COPY --from=build /src/app/build/install/app/lib /out/lib
COPY run.sh /out/run.sh
RUN chmod +x /out/run.sh

# 3) Export only the result
FROM scratch AS export
COPY --from=runtime /out /
