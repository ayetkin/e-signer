#!/bin/sh
# Runs with the bundled Java runtime; no Java needed on the system.
DIR="$(cd "$(dirname "$0")" && pwd)"
exec "$DIR/runtime/bin/java" -cp "$DIR/lib/*" MainKt "$@"
