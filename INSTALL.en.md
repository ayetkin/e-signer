# Installing E-Signer

[Türkçe](INSTALL.md)

E-Signer is developed and tested on macOS. **The Windows and Linux packages are not tested yet:**
they are built automatically, but starting, card detection and signing are not verified on these
systems. If you try them, please [report the result](https://github.com/ayetkin/e-signer/issues).

## What you need

- Java is included in the app; you don't need to install it.
- **A smart card reader.** Most USB readers work with the driver built into the operating system. If
  yours isn't detected, install the driver from the reader's manufacturer.
- **The driver of your e-signature card (PKCS#11).** Your certificate provider (Kamu SM, E-GÜVEN,
  E-Tuğra, TürkTrust and others) gives you the driver with the card; Kamu SM also offers it on its
  [driver download page](https://kamusm.bilgem.tubitak.gov.tr/islemler/surucu_yukleme_servisi/).
  For AKİS cards, the app looks for it here:

  | System | Driver file |
  | --- | --- |
  | macOS | `/usr/local/lib/libakisp11.dylib` |
  | Windows | `C:\Windows\System32\akisp11.dll` (not verified) |
  | Linux | `/usr/lib/libakisp11.so` (not verified) |

  If the driver is installed somewhere else, set its path in **Settings > Card**.
- **An internet connection** only for timestamped and long-term signatures, for checking certificate
  revocation when verifying, and for the update check. Standard signatures work offline.

Verifying signatures needs neither a card nor a driver.

The packages are on the [latest release](https://github.com/ayetkin/e-signer/releases/latest) page.

## macOS

Requires macOS 11 (Big Sur) or later.

1. Download the disk image for your Mac:
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

## Windows (not tested)

Windows 10 or 11, 64-bit.

- **Installer:** run `E-Signer-<version>-windows-x64.msi`. E-Signer is added to the Start menu and the
  desktop.
- **Without installing:** extract `E-Signer-<version>-windows-x64.zip` to a folder and run
  `E-Signer\E-Signer.exe`.

The app isn't code signed yet. If Windows SmartScreen says "Windows protected your PC", click
**More info**, then **Run anyway**.

## Linux (not tested)

64-bit (x86_64) distributions.

- **Debian and Ubuntu:** install the `.deb` package; the app goes to `/opt/e-signer` and is added to the
  application menu:

  ```sh
  sudo apt install ./E-Signer-<version>-linux-x64.deb
  ```
- **Other distributions:** extract `E-Signer-<version>-linux-x64.tar.gz` and run `E-Signer/bin/E-Signer`.

The card reader needs the PC/SC service (`sudo apt install pcscd` on Debian and Ubuntu).

## Troubleshooting

- **"Card library not found"**: the card driver isn't installed, or it's in a different place. Install
  the driver, or set its path in **Settings > Card**.
- **"No card reader found"**: reconnect the reader, try another USB port, or install the reader's driver.
- **The card is listed but signing fails**: see **Technical details** under the error and include them
  when you [report an issue](https://github.com/ayetkin/e-signer/issues). They never contain your PIN.

## Uninstall

- **macOS:** drag E-Signer from Applications to the Trash.
- **Windows:** remove E-Signer in Settings > Apps, or delete the folder you extracted from the zip.
- **Linux:** `sudo apt remove e-signer`, or delete the folder you extracted from the tar.gz.

The settings (language, signature level, time-stamp server; never your PIN or password) are kept in the
Java preferences under `sh/yetkin/esigner`: `~/Library/Preferences/com.apple.java.util.prefs.plist` on
macOS, `HKEY_CURRENT_USER\Software\JavaSoft\Prefs` in the registry on Windows, `~/.java/.userPrefs` on Linux.

## Building from source

See [Building and running](README.en.md#building-and-running) in the README.
