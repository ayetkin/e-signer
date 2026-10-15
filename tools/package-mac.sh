#!/bin/sh
# Wraps the app (lib/ + a jlink'ed macOS runtime/) into a native macOS app bundle or disk image with
# jpackage, so the Dock and menu bar show "E-Signer" and the app icon instead of "java".
#
# Locally it packages the container build output (dist/linux_arm64) with jpackage from the target JDK
# already downloaded for that build (vendor/target-jdk.tar.gz, extracted under vendor/; nothing is
# installed on the system). The release workflow sets the variables below instead.
#
# Usage (from the repository root, after `container build ... --output type=local,dest=dist .`):
#   tools/package-mac.sh            -> dist/mac/E-Signer.app
#
# Environment (all optional):
#   DIST      directory with lib/ and runtime/   (default dist/linux_arm64)
#   JPACKAGE  jpackage binary                    (default: from vendor/target-jdk.tar.gz)
#   TYPE      app-image, or dmg for a disk image with the app and an Applications link (default app-image)
#   VERSION   app version                        (default: appVersion in gradle.properties)
#   OUT       output directory                   (default dist/mac)
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="${DIST:-$ROOT/dist/linux_arm64}"
TYPE="${TYPE:-app-image}"
VERSION="${VERSION:-$(sed -n 's/^appVersion=//p' "$ROOT/gradle.properties")}"
OUT="${OUT:-$ROOT/dist/mac}"

test -d "$DIST/lib" || { echo "Run the container build first: $DIST/lib not found" >&2; exit 1; }

if [ -z "${JPACKAGE:-}" ]; then
    JDK_DIR="$ROOT/vendor/target-jdk"
    if [ ! -d "$JDK_DIR" ]; then
        mkdir -p "$JDK_DIR"
        tar -xzf "$ROOT/vendor/target-jdk.tar.gz" -C "$JDK_DIR"
    fi
    JPACKAGE="$(find "$JDK_DIR" -path '*/Contents/Home/bin/jpackage' | head -1)"
    test -n "$JPACKAGE" || { echo "jpackage not found in vendor/target-jdk.tar.gz" >&2; exit 1; }
fi

# jpackage rejects versions starting with 0 on macOS; macOS itself accepts them, so such a version is written
# into Info.plist afterwards.
case "$VERSION" in
    0*) BUNDLER_VERSION="1.0.0" ;;
    *) BUNDLER_VERSION="$VERSION" ;;
esac

rm -rf "$OUT/E-Signer.app"
mkdir -p "$OUT"
"$JPACKAGE" \
    --type app-image \
    --name "E-Signer" \
    --app-version "$BUNDLER_VERSION" \
    --vendor "Ali Yetkin" \
    --copyright "Copyright (C) 2026 Ali Yetkin. Licensed under the AGPL-3.0." \
    --mac-package-identifier "sh.yetkin.esigner" \
    --mac-app-category productivity \
    --icon "$ROOT/app/packaging/icon.icns" \
    --input "$DIST/lib" \
    --main-jar app.jar \
    --main-class sh.yetkin.esigner.app.MainKt \
    --runtime-image "$DIST/runtime" \
    --java-options "-Dorg.slf4j.simpleLogger.defaultLogLevel=warn" \
    --java-options "--add-modules=jdk.crypto.cryptoki" \
    --java-options "--add-exports=jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED" \
    --java-options "--add-exports=java.desktop/com.apple.eawt.event=ALL-UNNAMED" \
    --dest "$OUT"

if [ "$BUNDLER_VERSION" != "$VERSION" ]; then
    PLIST="$OUT/E-Signer.app/Contents/Info.plist"
    /usr/libexec/PlistBuddy -c "Set :CFBundleShortVersionString $VERSION" -c "Set :CFBundleVersion $VERSION" "$PLIST"
    # Editing Info.plist breaks jpackage's ad-hoc signature; without one Apple silicon refuses to start the app.
    codesign --force --deep --sign - "$OUT/E-Signer.app"
fi

# jpackage's own dmg adds the Applications link through Finder scripting, which fails silently on build
# machines; hdiutil with a plain symlink always works.
if [ "$TYPE" = dmg ]; then
    STAGE="$(mktemp -d)"
    mv "$OUT/E-Signer.app" "$STAGE/"
    ln -s /Applications "$STAGE/Applications"
    hdiutil create -quiet -volname "E-Signer" -srcfolder "$STAGE" -fs HFS+ -format UDZO -ov "$OUT/E-Signer-$VERSION.dmg"
    rm -rf "$STAGE"
fi

ls -d "$OUT"/E-Signer*
