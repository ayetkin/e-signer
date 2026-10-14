#!/bin/sh
# Runs the app with the bundled Java runtime; no Java needed on the system.
DIR="$(cd "$(dirname "$0")" && pwd)"
exec "$DIR/runtime/bin/java" \
  -Xdock:name="E-imza" \
  -Dapple.awt.application.name="E-imza" \
  -Dorg.slf4j.simpleLogger.defaultLogLevel=warn \
  -cp "$DIR/lib/*" sh.yetkin.esigner.app.MainKt "$@"
