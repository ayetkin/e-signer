# syntax=docker/dockerfile:1
#
# First download the target JDK (Apple Silicon):
#   mkdir -p vendor && curl -fL -o vendor/target-jdk.tar.gz \
#     "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.12.1_1.tar.gz"
#   (for Intel Macs use x64 instead of aarch64 in the file name)
# Then:
#   container build --output type=local,dest=dist .
#
# Output: dist/ -> run.sh + lib/ (app) + runtime/ (trimmed Java for the target platform)

# 1) Build the app (platform independent jars)
FROM gradle:8-jdk21 AS build
WORKDIR /src
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src
RUN gradle --no-daemon installDist

# 2) Build the Java runtime for the target platform (mac) with jlink
#    The target JDK archive is downloaded on the Mac into vendor/ (no download inside the container).
#    jlink must match the target JDK version exactly, hence the pinned image version.
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
COPY --from=build /src/build/install/eimza-test/lib /out/lib
COPY run.sh /out/run.sh
RUN chmod +x /out/run.sh

# 3) Export only the result
FROM scratch AS export
COPY --from=runtime /out /
