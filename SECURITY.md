# Security policy

E-Signer handles smart card PINs and creates legally binding signatures, so security reports are
taken seriously.

## Reporting a vulnerability

Please do not open a public issue. Use GitHub's private vulnerability reporting
("Report a vulnerability" under the repository's Security tab) and include:

- what is affected and how it can be exploited,
- the steps or files to reproduce it,
- the version or commit you tested.

You should get an answer within a week. Please keep the details private until a fix is released.

## Design rules

These rules are part of the design; a change that breaks one is treated as a vulnerability.

- A PIN is never retried automatically, so a wrong PIN cannot use up the card's attempts.
- PINs and time-stamp passwords are kept in memory only, never logged or written to disk, and are
  zeroed after use.
- Personal data from certificates (national ID numbers, e-mail addresses) is not logged; national
  ID numbers are shown masked.
- The user sees and confirms what is signed before the card is used.
- Signed output never overwrites an existing file.

## Scope

Only the latest commit on the default branch is supported. Vulnerabilities in the card driver, the
card itself, EU DSS or other dependencies should go to their maintainers; tell us if E-Signer uses
them in an unsafe way.
