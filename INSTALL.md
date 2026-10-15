# Installing E-Signer on macOS

[Türkçe](INSTALL.tr.md)

## What you need

- **macOS 11 (Big Sur) or later**, on Apple silicon or Intel. Java is included in the app; you don't
  need to install it.
- **A smart card reader.** Most USB readers work with the driver built into macOS. If yours isn't
  detected, install the driver from the reader's manufacturer.
- **The driver of your e-signature card (PKCS#11).** For AKİS cards this is the AKİS card driver,
  which installs `/usr/local/lib/libakisp11.dylib`. Your certificate provider (Kamu SM, E-GÜVEN,
  E-Tuğra, TürkTrust and others) gives you the driver with the card; Kamu SM also offers it on its
  [driver download page](https://kamusm.bilgem.tubitak.gov.tr/islemler/surucu_yukleme_servisi/).
  If the driver is installed somewhere else, set its path in **Settings > Card**.
- **An internet connection** only for timestamped and long-term signatures and for checking certificate
  revocation when verifying. Standard signatures work offline.

Verifying signatures needs neither a card nor a driver.

## Install

1. Open the [latest release](https://github.com/ayetkin/e-signer/releases/latest) and download the disk
   image for your Mac:
   - `E-Signer-<version>-macos-arm64.dmg` for Apple silicon (M1 and later),
   - `E-Signer-<version>-macos-x64.dmg` for Intel.

   Not sure which one? Apple menu > **About This Mac**: "Chip: Apple M..." is Apple silicon,
   "Processor: Intel" is Intel.
2. Open the disk image and drag **E-Signer** onto **Applications**.
3. Open E-Signer from Applications or Launchpad.

### The first time you open it

E-Signer isn't signed with an Apple Developer ID yet, so macOS stops it the first time with a message
like "Apple could not verify 'E-Signer' is free of malware". To open it anyway:

1. Click **Done** (or **OK**) on the message.
2. Open **System Settings > Privacy & Security**, scroll down to the message about E-Signer and
   click **Open Anyway**, then confirm with your password.

You only need to do this once. Alternatively, run this in Terminal after copying the app:

```sh
xattr -dr com.apple.quarantine /Applications/E-Signer.app
```

Only do this for a disk image you downloaded from this project's releases page.

## Troubleshooting

- **"Card library not found"**: the card driver isn't installed, or it's in a different place. Install
  the driver, or set its path in **Settings > Card**.
- **"No card reader found"**: reconnect the reader, try another USB port, or install the reader's driver.
- **The card is listed but signing fails**: see **Technical details** under the error and include them
  when you [report an issue](https://github.com/ayetkin/e-signer/issues). They never contain your PIN.

## Uninstall

Drag E-Signer from Applications to the Trash. Its settings (language, signature level, time-stamp
server; never your PIN or password) are kept in
`~/Library/Preferences/com.apple.java.util.prefs.plist` under `sh/yetkin/esigner`.

## Building from source

See [Building and running](README.md#building-and-running) in the README.
