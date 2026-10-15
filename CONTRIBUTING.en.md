# Contributing

[Türkçe](CONTRIBUTING.md)

Thanks for your interest in E-Signer. Issues, ideas and pull requests are welcome.

## Before you start

For anything larger than a small fix, open an issue first so we can agree on the approach.

## Development

- JDK 21. Run `./gradlew :core:test` for the core tests and `./gradlew :app:test` for the UI tests,
  which also render every screen to `app/build/screenshots`. Look at the screenshots after UI changes.
- Tests must not need a card or the network: use `TestKeys` (software keys) and `LocalPki`
  (a local time-stamp server and CRL) in `core/src/test`.
- Code, comments, identifiers and test data are in English. All user-facing text lives in
  `app/src/main/resources/i18n/strings_{en,tr}.properties`, in both languages; `StringsTest`
  checks that the two files have the same keys. `core` returns error codes, never UI text.
- Keep `core` independent of the UI.
- Follow the rules in [SECURITY.en.md](SECURITY.en.md#design-rules): never retry a PIN, never log or store
  PINs, passwords or personal data, and never put real personal data in tests, logs or commits.

- Add every user-visible change under "Unreleased" / "Yayımlanmadı" in [CHANGELOG.en.md](CHANGELOG.en.md)
  and [CHANGELOG.md](CHANGELOG.md).

## Pull requests

- One topic per pull request, with tests for new behavior.
- Describe what changed and how you tested it, including the card and OS if you tested with a card.

## Releasing

1. Raise `appVersion` in `gradle.properties` (e.g. `0.4.2`).
2. In both changelogs, turn the "Unreleased" / "Yayımlanmadı" heading into `[0.4.2] - YYYY-MM-DD`, add an
   empty "Unreleased" heading above it and update the comparison links at the end.
3. Commit, push and wait for CI to pass.
4. Create and push the tag: `git tag v0.4.2 && git push origin v0.4.2`.

The `Release` workflow runs the tests, builds the macOS, Windows and Linux packages and publishes the
GitHub release with the notes taken from the changelogs (`tools/release-notes.sh`). It stops if the tag
does not match `appVersion` or a changelog has no section for that version. To only try the packages
without publishing, run the workflow by hand from the Actions tab; the packages are attached to the run
as artifacts.

## License of contributions

E-Signer is licensed under the AGPL-3.0. By submitting a contribution you agree that it is licensed
under the AGPL-3.0, and you grant the maintainer the right to also license your contribution under
other terms, including commercial licenses. Only submit work you have the right to contribute.
