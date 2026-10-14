#!/bin/sh
# Paketin içindeki Java runtime ile çalıştırır; sistemde Java gerekmez.
DIR="$(cd "$(dirname "$0")" && pwd)"
exec "$DIR/runtime/bin/java" -cp "$DIR/lib/*" MainKt "$@"
