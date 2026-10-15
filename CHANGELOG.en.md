# Changelog

[Türkçe](CHANGELOG.md)

Notable changes are recorded in this file. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and versions follow [Semantic Versioning](https://semver.org/). Unreleased changes are collected under
"Unreleased"; when a version is released, the heading is replaced with its number.

## [Unreleased]

## [0.5.2] - 2026-10-15

### Added

- An appearance choice in Settings > General: system, light or dark.

### Changed

- Updates are also checked at startup (can be turned off in Settings > General); a new version shows a download link in the sidebar.
- The bar at the bottom is gone; the "Sign" button stays at the bottom of the sidebar, above the card.

## [0.5.1] - 2026-10-15

### Added

- The timestamp settings are saved only after the server gave one test timestamp, so a wrong address or password
  shows in the settings window and not while signing. The Kamu SM address cannot be saved without the "Kamu SM customer"
  authentication.
- A download link for the AKİS card driver on the card selection screen when no card or reader is found.
- A timestamp row in the sidebar: the server in use, a missing setup, or that none is used.
- The signature icon on the PDF is green for timestamped signatures.
- Verification shows the timestamp authority and an "Official timestamp" label (an intact timestamp from an
  authorized provider).
- Icons for each option in the sidebar's Format and On the PDF menus.
- "Reason" and "Location" in Settings > Signature: written into PDF signatures and shown in PDF readers'
  signature panels. The reason is "Signed with E-Signer" by default; it can be changed or removed.

### Changed

- The text of visible signatures (Digitally signed, name, time) uses the Inter font.
- The "None" timestamp authentication is gone: providers give no timestamps without credentials. The default
  is "Kamu SM customer".
- The help texts above the file list and the preview are gone.
- The file list is narrower, and its header is as tall as the preview's.
- The settings window is wider and taller.

### Fixed

- When the Kamu SM timestamp server sends an empty answer (no credentials set up), the error says the server
  refused the request and to check the credentials, instead of "invalid answer".
- Button labels are centered vertically on macOS.
- A text-only visible signature has the same space left and right of the text: the box follows the actual
  signing time and DSS's own text metrics.
- The icon in message boxes lines up with the first line of text.

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

[Unreleased]: https://github.com/ayetkin/e-signer/compare/v0.5.2...HEAD
[0.5.2]: https://github.com/ayetkin/e-signer/compare/v0.5.1...v0.5.2
[0.5.1]: https://github.com/ayetkin/e-signer/compare/v0.5.0...v0.5.1
[0.5.0]: https://github.com/ayetkin/e-signer/compare/v0.4.1...v0.5.0
[0.4.1]: https://github.com/ayetkin/e-signer/releases/tag/v0.4.1
