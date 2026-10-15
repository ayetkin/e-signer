# E-Signer'ı macOS'a kurma

[English](INSTALL.md)

## Gerekenler

- **macOS 11 (Big Sur) veya sonrası**, Apple silicon ya da Intel. Java uygulamanın içinde gelir, ayrıca
  kurmanız gerekmez.
- **Akıllı kart okuyucu.** USB okuyucuların çoğu macOS'un kendi sürücüsüyle çalışır. Okuyucunuz
  algılanmazsa üreticisinin sürücüsünü kurun.
- **E-imza kartınızın sürücüsü (PKCS#11).** AKİS kartlar için bu, `/usr/local/lib/libakisp11.dylib`
  dosyasını kuran AKİS kart sürücüsüdür. Sürücüyü sertifikanızı aldığınız sağlayıcı (Kamu SM,
  E-GÜVEN, E-Tuğra, TürkTrust ve diğerleri) kartla birlikte verir; Kamu SM ayrıca
  [sürücü yükleme sayfasında](https://kamusm.bilgem.tubitak.gov.tr/islemler/surucu_yukleme_servisi/)
  sunar. Sürücü başka bir yerdeyse yolunu **Ayarlar > Kart** bölümünden girin.
- **İnternet bağlantısı** yalnızca zaman damgalı ve uzun süreli imzalar ile doğrulamada sertifika iptal
  kontrolü için gerekir. Standart imza çevrimdışı çalışır.

İmza doğrulamak için kart da sürücü de gerekmez.

## Kurulum

1. [Son sürüm](https://github.com/ayetkin/e-signer/releases/latest) sayfasını açın ve Mac'inize uygun
   disk görüntüsünü indirin:
   - Apple silicon (M1 ve sonrası) için `E-Signer-<sürüm>-macos-arm64.dmg`,
   - Intel için `E-Signer-<sürüm>-macos-x64.dmg`.

   Hangisi olduğundan emin değilseniz: Apple menüsü > **Bu Mac Hakkında**. "Çip: Apple M..." yazıyorsa
   Apple silicon, "İşlemci: Intel" yazıyorsa Intel'dir.
2. Disk görüntüsünü açın ve **E-Signer**'ı **Uygulamalar** klasörüne sürükleyin.
3. E-Signer'ı Uygulamalar'dan ya da Launchpad'den açın.

### İlk açılışta

E-Signer henüz Apple Developer ID ile imzalı değil. Bu yüzden macOS ilk açılışta "Apple, 'E-Signer'ın
kötü amaçlı yazılım içermediğini doğrulayamadı" gibi bir uyarıyla uygulamayı durdurur. Yine de açmak için:

1. Uyarıda **Bitti**'ye (ya da **Tamam**'a) tıklayın.
2. **Sistem Ayarları > Gizlilik ve Güvenlik** bölümünü açın, aşağıdaki E-Signer uyarısını bulun,
   **Yine de Aç**'a tıklayın ve parolanızla onaylayın.

Bunu yalnızca bir kez yapmanız gerekir. İsterseniz uygulamayı kopyaladıktan sonra Terminal'de şunu da
çalıştırabilirsiniz:

```sh
xattr -dr com.apple.quarantine /Applications/E-Signer.app
```

Bunu yalnızca bu projenin sürümler sayfasından indirdiğiniz disk görüntüsü için yapın.

## Sorun giderme

- **"Kart kütüphanesi bulunamadı"**: kart sürücüsü kurulu değil ya da başka bir yerde. Sürücüyü kurun
  ya da yolunu **Ayarlar > Kart** bölümünden girin.
- **"Kart okuyucu bulunamadı"**: okuyucuyu çıkarıp takın, başka bir USB girişi deneyin ya da okuyucunun
  sürücüsünü kurun.
- **Kart görünüyor ama imza atılamıyor**: hatanın altındaki **Teknik ayrıntılar**'a bakın ve
  [hata kaydı açarken](https://github.com/ayetkin/e-signer/issues) ekleyin. Bu ayrıntılar PIN'inizi
  hiçbir zaman içermez.

## Kaldırma

E-Signer'ı Uygulamalar'dan Çöp Sepeti'ne sürükleyin. Ayarları (dil, imza seviyesi, zaman damgası
sunucusu; PIN ya da parola asla) `~/Library/Preferences/com.apple.java.util.prefs.plist` dosyasında
`sh/yetkin/esigner` altında tutulur.

## Kaynak koddan derleme

README'deki [Derleme ve çalıştırma](README.tr.md#derleme-ve-çalıştırma) bölümüne bakın.
