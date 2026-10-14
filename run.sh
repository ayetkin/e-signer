#!/bin/sh
# Runs the app with the bundled Java runtime; no Java needed on the system.
DIR="$(cd "$(dirname "$0")" && pwd)"
exec "$DIR/runtime/bin/java" \
  -Xdock:name="E-Imza" \
  -Dapple.awt.application.name="E-Imza" \
  -Dorg.slf4j.simpleLogger.defaultLogLevel=warn \
  --add-modules jdk.crypto.cryptoki \
  --add-exports jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED \
  -cp "$DIR/lib/*" sh.yetkin.esigner.app.MainKt "$@"
