#!/bin/sh
# Prints the GitHub release notes for a version: its section from CHANGELOG.en.md plus the install link.
# Releases are in English only; CHANGELOG.md (Turkish) must still have the section, so neither changelog
# is forgotten. Fails when a changelog has no section for the version.
#
# Usage: tools/release-notes.sh 0.4.2
set -eu

VERSION="$1"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Lines after "## [VERSION]" up to the next "## [" heading, without the link definitions at the end.
section() {
    awk -v v="## [$VERSION]" 'index($0, v) == 1 { on = 1; next } /^## \[/ { on = 0 } on' "$1" | grep -v '^\[.*\]: http' | sed -e '/./,$!d'
}

[ -n "$(section "$ROOT/CHANGELOG.md")" ] || { echo "CHANGELOG.md has no section for $VERSION" >&2; exit 1; }
en="$(section "$ROOT/CHANGELOG.en.md")"
[ -n "$en" ] || { echo "CHANGELOG.en.md has no section for $VERSION" >&2; exit 1; }

cat <<NOTES
$en

Installation: [INSTALL.en.md](https://github.com/ayetkin/e-signer/blob/main/INSTALL.en.md). The Windows and Linux packages are not tested yet.
NOTES
