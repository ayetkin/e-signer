#!/bin/sh
# Prints the GitHub release notes for a version: its sections from CHANGELOG.md (Turkish) and
# CHANGELOG.en.md (English), plus the download and install links. Fails when a changelog has no
# section for the version, so a release cannot go out without notes.
#
# Usage: tools/release-notes.sh 0.4.2
set -eu

VERSION="$1"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Lines after "## [VERSION]" up to the next "## [" heading, without the link definitions at the end.
section() {
    awk -v v="## [$VERSION]" 'index($0, v) == 1 { on = 1; next } /^## \[/ { on = 0 } on' "$1" | grep -v '^\[.*\]: http' | sed -e '/./,$!d'
}

tr="$(section "$ROOT/CHANGELOG.md")"
en="$(section "$ROOT/CHANGELOG.en.md")"
[ -n "$tr" ] || { echo "CHANGELOG.md has no section for $VERSION" >&2; exit 1; }
[ -n "$en" ] || { echo "CHANGELOG.en.md has no section for $VERSION" >&2; exit 1; }

REPO="https://github.com/ayetkin/e-signer"
cat <<NOTES
## Türkçe

$tr

Kurulum: [INSTALL.md]($REPO/blob/main/INSTALL.md). Windows ve Linux paketleri henüz test edilmedi.

## English

$en

Installation: [INSTALL.en.md]($REPO/blob/main/INSTALL.en.md). The Windows and Linux packages are not tested yet.
NOTES
