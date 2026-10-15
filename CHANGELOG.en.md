# Changelog

[Türkçe](CHANGELOG.md)

Notable changes are recorded in this file. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and versions follow [Semantic Versioning](https://semver.org/). Unreleased changes are collected under
"Unreleased"; when a version is released, the heading is replaced with its number.

## [Unreleased]

## [0.5.0] - 2026-10-15

### Added

- Signing with a fiscal seal (mali mühür) card: without a valid e-signature certificate on the card, the
  fiscal seal certificate is used and the card is shown as "Fiscal seal, Tax ID ...". Not tried with a
  real fiscal seal card yet.
- Window (Minimize, Zoom) and Help menus in the macOS menu bar.
- About window with the version, developer, license and a link to the source code.
- Update check against GitHub (Help > Check for Updates). It only runs when you ask.
- Windows (`.msi`, `.zip`) and Linux (`.deb`, `.tar.gz`) packages. These packages are not tested yet.
- A changelog that the release notes are made from.

### Changed

- The card selection screen opens in a small window in the middle of the screen. The workspace opens
  with the chosen window setting.
- Settings moved from the bottom of the sidebar to a gear icon next to the app name at the top.
- The "Sign" button has an icon, and buttons are a little taller.
- The file list is narrower while the preview is open.
- The documents are in Turkish; the English versions are in the `.en.md` files.

### Fixed

- The macOS app menu shows "About E-Signer" instead of "About MainKt".
- Button labels are centered vertically.
- On macOS, Window > Minimize (Cmd+M) froze the app.
- On macOS, the menu bar could show only the app menu right after the start.

## [0.4.1] - 2026-10-15

First published version (macOS only).

### Added

- Signing with AKİS cards over PKCS#11. The card is detected without a PIN; the PIN is asked once per
  signing run and never retried automatically.
- PAdES signatures inside PDFs and CAdES `.imz` files for other files.
- Signature levels: B-B, B-T with a time-stamp (including Kamu SM authentication) and B-LT.
- PDF preview; the place and size of the visible signature are picked in the preview.
- Verifying signatures without a card: bundled roots of the Turkish trust service providers, the Law 5070
  qualified certificate check and fiscal seals.
- Settings window with sections, Turkish and English user interface.
- macOS disk images for Apple silicon and Intel.

[Unreleased]: https://github.com/ayetkin/e-signer/compare/v0.5.0...HEAD
[0.5.0]: https://github.com/ayetkin/e-signer/compare/v0.4.1...v0.5.0
[0.4.1]: https://github.com/ayetkin/e-signer/releases/tag/v0.4.1
