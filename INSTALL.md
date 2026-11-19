# E-Signer kurulumu

[English](INSTALL.en.md)

E-Signer macOS'ta geliştirilip test ediliyor. **Windows ve Linux paketleri henüz test edilmedi:**
otomatik olarak üretiliyorlar ama bu sistemlerde açılış, kart algılama ve imzalama doğrulanmadı.
Denerseniz sonucu [bildirmeniz](https://github.com/ayetkin/e-signer/issues) çok yardımcı olur.

## Gerekenler

- Java uygulamanın içinde gelir, ayrıca kurmanız gerekmez.
- **Akıllı kart okuyucu.** USB okuyucuların çoğu işletim sisteminin kendi sürücüsüyle çalışır.
  Okuyucunuz algılanmazsa üreticisinin sürücüsünü kurun.
- **E-imza kartınızın sürücüsü (PKCS#11).** Sürücüyü sertifikanızı aldığınız sağlayıcı (Kamu SM,
  E-GÜVEN, E-Tuğra, TürkTrust ve diğerleri) kartla birlikte verir; AKİS sürücüsü ayrıca TÜBİTAK BİLGEM'in
  [AKİS destek sayfasında](https://akiskart.bilgem.tubitak.gov.tr/en/support/)
  bulunur. AKİS kartlar için uygulamanın aradığı yerler:

  | Sistem | Sürücü dosyası |
  | --- | --- |
  | macOS | `/usr/local/lib/libakisp11.dylib` |
  | Windows | `C:\Windows\System32\akisp11.dll` (doğrulanmadı) |
  | Linux | `/usr/lib/libakisp11.so` (doğrulanmadı) |

  Sürücü başka bir yerdeyse yolunu **Ayarlar > Kart** bölümünden girin.
- **İnternet bağlantısı** yalnızca zaman damgalı ve uzun süreli imzalar, doğrulamada sertifika iptal
  kontrolü, güncelleme denetimi ve indirme için gerekir. Standart imza çevrimdışı çalışır.

İmza doğrulamak için kart da sürücü de gerekmez.

Paketler [son sürüm](https://github.com/ayetkin/e-signer/releases/latest) sayfasında.

## macOS

macOS 11 (Big Sur) veya sonrası gerekir.

1. Mac'inize uygun disk görüntüsünü indirin:
   - Apple silicon (M1 ve sonrası) için [`E-Signer-macos-arm64.dmg`](https://github.com/ayetkin/e-signer/releases/latest/download/E-Signer-macos-arm64.dmg),
   - Intel için [`E-Signer-macos-x64.dmg`](https://github.com/ayetkin/e-signer/releases/latest/download/E-Signer-macos-x64.dmg).

   Hangisi olduğundan emin değilseniz: Apple menüsü > **Bu Mac Hakkında**. "Çip: Apple M..." yazıyorsa
   Apple silicon, "İşlemci: Intel" yazıyorsa Intel'dir.
2. Disk görüntüsünü açın ve **E-Signer**'ı **Uygulamalar** klasörüne sürükleyin.
3. E-Signer'ı Uygulamalar'dan ya da Launchpad'den açın.

0.7.0 ve sonraki sürümler Apple Developer ID ile imzalı ve Apple tarafından onaylı (notarized). macOS
bunları ilk açılışta da uyarısız açar.

### 0.6.1 ve önceki sürümler

Bu sürümler imzalı değil. macOS bunları ilk açılışta "Apple, 'E-Signer'ın kötü amaçlı yazılım içermediğini
doğrulayamadı" gibi bir uyarıyla durdurur. Yine de açmak için:

1. Uyarıda **Bitti**'ye (ya da **Tamam**'a) tıklayın.
2. **Sistem Ayarları > Gizlilik ve Güvenlik** bölümünü açın, aşağıdaki E-Signer uyarısını bulun,
   **Yine de Aç**'a tıklayın ve parolanızla onaylayın.

Bunu yalnızca bir kez yapmanız gerekir. İsterseniz uygulamayı kopyaladıktan sonra Terminal'de şunu da
çalıştırabilirsiniz:

```sh
xattr -dr com.apple.quarantine /Applications/E-Signer.app
```

Bunu yalnızca bu projenin sürümler sayfasından indirdiğiniz disk görüntüsü için yapın.

## Windows (test edilmedi)

Windows 10 veya 11, 64 bit.

- **Kurulum:** [`E-Signer-windows-x64.msi`](https://github.com/ayetkin/e-signer/releases/latest/download/E-Signer-windows-x64.msi) dosyasını çalıştırın. E-Signer Başlat menüsüne ve
  masaüstüne eklenir.
- **Kurulumsuz:** [`E-Signer-windows-x64.zip`](https://github.com/ayetkin/e-signer/releases/latest/download/E-Signer-windows-x64.zip) dosyasını bir klasöre açın ve
  `E-Signer\E-Signer.exe` dosyasını çalıştırın.

Uygulama henüz kod imzalı değil. Windows SmartScreen "Windows bilgisayarınızı korudu" derse
**Ek bilgi**'ye, ardından **Yine de çalıştır**'a tıklayın.

## Linux (test edilmedi)

64 bit (x86_64) dağıtımlar.

- **Debian ve Ubuntu:** [`E-Signer-linux-x64.deb`](https://github.com/ayetkin/e-signer/releases/latest/download/E-Signer-linux-x64.deb) paketini kurun; uygulama `/opt/e-signer` altına kurulur ve uygulama
  menüsüne eklenir:

  ```sh
  sudo apt install ./E-Signer-linux-x64.deb
  ```
- **Diğer dağıtımlar:** [`E-Signer-linux-x64.tar.gz`](https://github.com/ayetkin/e-signer/releases/latest/download/E-Signer-linux-x64.tar.gz) dosyasını açın ve
  `E-Signer/bin/E-Signer` dosyasını çalıştırın.

Kart okuyucu için PC/SC servisi gerekir (Debian ve Ubuntu'da `sudo apt install pcscd`).

## Güncelleme

E-Signer açılışta GitHub'daki son sürümü denetler (**Ayarlar > Genel**'den kapatılabilir). Yeni sürüm
varsa kenar çubuğunda **Güncelleme** satırı çıkar. macOS'ta **Yardım > Güncellemeleri Denetle** ile elle de
denetleyebilirsiniz; sürüm notları orada açılan pencerenin **Yenilikler** düğmesindedir.

- **macOS ve Windows:** **İndir** paketi İndirilenler klasörüne indirir. Paket, GitHub'ın yayımladığı
  SHA-256 özetiyle karşılaştırılır ve eşleşirse açılır. E-Signer'ı kapatın, sonra:
  - macOS'ta açılan disk görüntüsünden E-Signer'ı **Uygulamalar**'a sürükleyin ve **Değiştir**'i seçin.
  - Windows'ta `.msi` kurulumu eski sürümün üzerine kurar. Zip kullanıyorsanız yeni zip'i açın.
- **Linux:** **İndir** sürümler sayfasını açar. Yeni `.deb` paketini aynı `apt install` komutuyla kurun.

0.6.1 ve önceki sürümlerde **İndir** her sistemde sürümler sayfasını açar.

## Sorun giderme

- **"Kart kütüphanesi bulunamadı"**: kart sürücüsü kurulu değil ya da başka bir yerde. Sürücüyü kurun
  ya da yolunu **Ayarlar > Kart** bölümünden girin.
- **"Kart okuyucu bulunamadı"**: okuyucuyu çıkarıp takın, başka bir USB girişi deneyin ya da okuyucunun
  sürücüsünü kurun.
- **Kart görünüyor ama imza atılamıyor**: hatanın altındaki **Teknik ayrıntılar**'a bakın ve
  [hata kaydı açarken](https://github.com/ayetkin/e-signer/issues) ekleyin. Bu ayrıntılar PIN'inizi
  hiçbir zaman içermez.

## Kaldırma

- **macOS:** E-Signer'ı Uygulamalar'dan Çöp Sepeti'ne sürükleyin.
- **Windows:** Ayarlar > Uygulamalar'dan E-Signer'ı kaldırın ya da zip'ten açtığınız klasörü silin.
- **Linux:** `sudo apt remove e-signer` ya da tar.gz'den açtığınız klasörü silin.

Ayarlar (dil, imza seviyesi, zaman damgası sunucusu; PIN ya da parola asla) `sh/yetkin/esigner` altında
Java tercihlerinde tutulur: macOS'ta `~/Library/Preferences/com.apple.java.util.prefs.plist`, Windows'ta
kayıt defterinde `HKEY_CURRENT_USER\Software\JavaSoft\Prefs`, Linux'ta `~/.java/.userPrefs`.

## Kaynak koddan derleme

README'deki [Derleme ve çalıştırma](README.md#derleme-ve-çalıştırma) bölümüne bakın.
