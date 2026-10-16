#!/bin/sh
# Runs the app with the bundled Java runtime; no Java needed on the system.
DIR="$(cd "$(dirname "$0")" && pwd)"
exec "$DIR/runtime/bin/java" \
  -Xdock:name="E-Signer" \
  -Dapple.awt.application.name="E-Signer" \
  -Dorg.slf4j.simpleLogger.defaultLogLevel=warn \
  -Xmx2g -XX:G1PeriodicGCInterval=10000 -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30 \
  --add-modules jdk.crypto.cryptoki \
  --add-exports jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED \
  --add-exports java.desktop/com.apple.eawt.event=ALL-UNNAMED \
  -cp "$DIR/lib/*" sh.yetkin.esigner.app.MainKt "$@"
