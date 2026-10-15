#!/bin/sh
# Prints the GitHub release notes for a version: its sections from CHANGELOG.md (Turkish) and
# CHANGELOG.en.md (English), placed in tools/release-notes.md with the install links. Fails when a changelog has no
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

# The template holds the headings and install links; @TR@ and @EN@ lines become the sections.
TR="$tr" EN="$en" awk '$0 == "@TR@" { print ENVIRON["TR"]; next } $0 == "@EN@" { print ENVIRON["EN"]; next } 1' \
    "$ROOT/tools/release-notes.md"
