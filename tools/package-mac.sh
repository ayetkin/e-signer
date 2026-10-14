#!/bin/sh
# Wraps the container build output (dist/linux_arm64: lib/ + jlink'ed macOS runtime/) into a native
# macOS app bundle with jpackage, so the Dock and menu bar show "E-Imza" and the app icon instead of "java".
#
# jpackage must run on macOS. It is taken from the target JDK already downloaded for the container
# build (vendor/target-jdk.tar.gz), extracted under vendor/; nothing is installed on the system.
# Usage (from the repository root, after `container build ... --output type=local,dest=dist .`):
#   tools/package-mac.sh            -> dist/mac/E-Imza.app
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="$ROOT/dist/linux_arm64"
JDK_DIR="$ROOT/vendor/target-jdk"
OUT="$ROOT/dist/mac"
VERSION="1.0.0"

test -d "$DIST/lib" || { echo "Run the container build first: $DIST/lib not found" >&2; exit 1; }

if [ ! -d "$JDK_DIR" ]; then
    mkdir -p "$JDK_DIR"
    tar -xzf "$ROOT/vendor/target-jdk.tar.gz" -C "$JDK_DIR"
fi
JPACKAGE="$(find "$JDK_DIR" -path '*/Contents/Home/bin/jpackage' | head -1)"
test -n "$JPACKAGE" || { echo "jpackage not found in vendor/target-jdk.tar.gz" >&2; exit 1; }

rm -rf "$OUT/E-Imza.app"
mkdir -p "$OUT"
"$JPACKAGE" \
    --type app-image \
    --name "E-Imza" \
    --app-version "$VERSION" \
    --vendor "Yetkin" \
    --mac-package-identifier "sh.yetkin.esigner" \
    --icon "$ROOT/app/packaging/icon.icns" \
    --input "$DIST/lib" \
    --main-jar app.jar \
    --main-class sh.yetkin.esigner.app.MainKt \
    --runtime-image "$DIST/runtime" \
    --java-options "-Dorg.slf4j.simpleLogger.defaultLogLevel=warn" \
    --java-options "--add-modules=jdk.crypto.cryptoki" \
    --java-options "--add-exports=jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED" \
    --dest "$OUT"

echo "$OUT/E-Imza.app"
