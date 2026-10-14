# syntax=docker/dockerfile:1
#
# Önce hedef JDK'yı indir (Apple Silicon):
#   mkdir -p vendor && curl -fL -o vendor/target-jdk.tar.gz \
#     "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.12.1_1.tar.gz"
#   (Intel Mac için dosya adında aarch64 yerine x64)
# Sonra:
#   container build --output type=local,dest=dist .
#
# Çıktı: dist/  -> run.sh + lib/ (uygulama) + runtime/ (hedef platform için küçültülmüş Java)

# 1) Uygulamayı derle (platformdan bağımsız .jar dosyaları)
FROM gradle:8-jdk21 AS build
WORKDIR /src
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src
RUN gradle --no-daemon installDist

# 2) Hedef platform (mac) için Java runtime'ı jlink ile üret
#    Hedef JDK arşivi Mac'te indirilip vendor/ klasörüne konur (container içinden indirme yok).
#    jlink sürümü ile hedef JDK sürümü birebir aynı olmalı, bu yüzden imaj sürümü sabit.
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

# 3) Sadece çıktıyı dışarı aktar
FROM scratch AS export
COPY --from=runtime /out /
