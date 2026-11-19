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
#
# Signing (optional; without SIGN_IDENTITY the app is ad-hoc signed and macOS asks the user to allow it):
#   SIGN_IDENTITY  codesign identity, e.g. "Developer ID Application" (must match one keychain certificate)
#   NOTARY_PROFILE notarytool keychain profile (xcrun notarytool store-credentials), or instead
#   NOTARY_KEY, NOTARY_KEY_ID, NOTARY_ISSUER  App Store Connect API key file, key ID and issuer ID
# With SIGN_IDENTITY and notary credentials the output is notarized by Apple and the ticket is stapled to it.
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="${DIST:-$ROOT/dist/linux_arm64}"
TYPE="${TYPE:-app-image}"
VERSION="${VERSION:-$(sed -n 's/^appVersion=//p' "$ROOT/gradle.properties")}"
OUT="${OUT:-$ROOT/dist/mac}"
SIGN_IDENTITY="${SIGN_IDENTITY:-}"
NOTARIZE="${NOTARY_KEY:-}${NOTARY_PROFILE:-}"
test -z "$NOTARIZE" || test -n "$SIGN_IDENTITY" || { echo "Notarization needs SIGN_IDENTITY" >&2; exit 1; }

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
    --java-options "-Xms64m" \
    --java-options "-Xmx2g" \
    --java-options "-XX:G1PeriodicGCInterval=10000" \
    --java-options "-XX:-G1PeriodicGCInvokesConcurrent" \
    --java-options "-XX:MinHeapFreeRatio=10" \
    --java-options "-XX:MaxHeapFreeRatio=30" \
    --java-options "--add-modules=jdk.crypto.cryptoki" \
    --java-options "--add-exports=jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED" \
    --java-options "--add-exports=java.desktop/com.apple.eawt.event=ALL-UNNAMED" \
    --dest "$OUT"

if [ "$BUNDLER_VERSION" != "$VERSION" ]; then
    PLIST="$OUT/E-Signer.app/Contents/Info.plist"
    /usr/libexec/PlistBuddy -c "Set :CFBundleShortVersionString $VERSION" -c "Set :CFBundleVersion $VERSION" "$PLIST"
    # Editing Info.plist breaks jpackage's ad-hoc signature; without one Apple silicon refuses to start the app.
    [ -n "$SIGN_IDENTITY" ] || codesign --force --deep --sign - "$OUT/E-Signer.app"
fi

sign() {
    codesign --force --timestamp --options runtime --entitlements "$ROOT/app/packaging/entitlements.plist" \
        --sign "$SIGN_IDENTITY" "$@"
}

notary() {
    if [ -n "${NOTARY_KEY:-}" ]; then
        xcrun notarytool "$@" --key "$NOTARY_KEY" --key-id "$NOTARY_KEY_ID" --issuer "$NOTARY_ISSUER"
    else
        xcrun notarytool "$@" --keychain-profile "$NOTARY_PROFILE"
    fi
}

# Waits for Apple's verdict; on rejection prints Apple's log, which names every offending file.
notarize() {
    result="$(notary submit "$1" --wait)" || true
    echo "$result"
    if echo "$result" | grep -q 'status: Accepted'; then return; fi
    id="$(echo "$result" | sed -n 's/^ *id: //p' | head -1)"
    [ -z "$id" ] || notary log "$id" >&2
    exit 1
}

# Developer ID signing goes inside out: native libraries packed in jars (Skiko), every Mach-O file, the bundled
# runtime, then the app. Notarization rejects any unsigned native code, including code inside jars.
APP="$(cd "$OUT" && pwd)/E-Signer.app"
if [ -n "$SIGN_IDENTITY" ]; then
    for jar in "$APP"/Contents/app/*.jar; do
        unzip -Z1 "$jar" | grep -E '\.(dylib|jnilib)$' | while read -r entry; do
            tmp="$(mktemp -d)"
            unzip -q "$jar" "$entry" -d "$tmp"
            sign "$tmp/$entry"
            (cd "$tmp" && zip -q "$jar" "$entry")
            rm -rf "$tmp"
        done
    done
    find "$APP/Contents" -type f \( -name '*.dylib' -o -perm -u+x \) | while read -r f; do
        if file -b "$f" | grep -q Mach-O; then sign "$f"; fi
    done
    sign "$APP/Contents/runtime"
    sign "$APP"
    codesign --verify --strict --deep "$APP"
fi

# jpackage's own dmg adds the Applications link through Finder scripting, which fails silently on build
# machines; hdiutil with a plain symlink always works.
if [ "$TYPE" = dmg ]; then
    STAGE="$(mktemp -d)"
    mv "$OUT/E-Signer.app" "$STAGE/"
    ln -s /Applications "$STAGE/Applications"
    hdiutil create -quiet -volname "E-Signer" -srcfolder "$STAGE" -fs HFS+ -format UDZO -ov "$OUT/E-Signer-$VERSION.dmg"
    rm -rf "$STAGE"
    [ -z "$SIGN_IDENTITY" ] || codesign --timestamp --sign "$SIGN_IDENTITY" "$OUT/E-Signer-$VERSION.dmg"
fi

# The ticket is stapled to what users download, so Gatekeeper can check it offline: the disk image, or the app.
if [ -n "$NOTARIZE" ]; then
    if [ "$TYPE" = dmg ]; then
        notarize "$OUT/E-Signer-$VERSION.dmg"
        xcrun stapler staple "$OUT/E-Signer-$VERSION.dmg"
    else
        ditto -c -k --keepParent "$APP" "$OUT/E-Signer-notarize.zip"
        notarize "$OUT/E-Signer-notarize.zip"
        rm "$OUT/E-Signer-notarize.zip"
        xcrun stapler staple "$APP"
    fi
fi

ls -d "$OUT"/E-Signer*
