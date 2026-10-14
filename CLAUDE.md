# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# E-imza masaüstü uygulaması

Türkiye'deki nitelikli e-imza kartlarıyla (ilk hedef: AKİS) PDF ve diğer dosyaları imzalayan,
macOS (arm64 + x86_64) ve Windows (x64) üzerinde çalışan masaüstü uygulaması.
Önce şirket içi kullanım, ileride ticari ürün. Aşama aşama ilerliyoruz.

## Depo yapısı

- **Kodda Türkçe yok:** kod, yorum, test verisi ve kullanıcı metinleri dahil. Uygulama iki dilli (TR/EN), tüm
  kullanıcı metinleri `app/src/main/resources/i18n/strings_{tr,en}.properties` içinde. `core` hata kodu döndürür.
- `core/`: arayüzden bağımsız imzalama (`sh.yetkin.esigner.core`).
  - `DocumentSigner`: herhangi bir `SignatureTokenConnection` ile imzalar. DSS'in üç adımlı akışı
    (`getDataToSign` → `token.sign` → `signDocument`) ayrı tutuldu, ileride bölünmüş imzalama buraya oturacak.
    Çıktı önce geçici dosyaya yazılır, sonra yerine taşınır. Var olan dosyanın üzerine asla yazmaz.
    PAdES çıktı soneki (`-imzali`/`-signed`) arayüz dilinden gelir.
  - `CardScanner` + `Pkcs11Scanner.java`: PIN'siz kart tespiti. JDK'nın dahili PKCS#11 sarmalayıcısıyla
    (`sun.security.pkcs11.wrapper`) slot/token bilgisi, PIN sayaç bayrakları (`COUNT_LOW`/`FINAL_TRY`/`LOCKED`) ve
    karttaki açık sertifikalar okunur. Derleme ve çalıştırmada `--add-modules jdk.crypto.cryptoki` +
    `--add-exports jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED` gerekir (build.gradle.kts, run.sh).
    Modül, SunPKCS11 ile aynı `PKCS11.getInstance` önbelleğini paylaşır (aynı init bayrakları kullanılmalı).
  - `CardSession`: tek login denemesi. Gerçek slot ID'si ile açılır (`Pkcs11SignatureToken(..., slotId, -1, "")`,
    DSS bu durumda `slot = <id>` yazar). PIN dizisinin sahipliğini alır, `close()`'da sıfırlar.
  - `SigningException(error: SigningError, subject)`: `isCardError` ise toplu imza durur, dosya hatasıysa devam eder.
    `cardError()` SunPKCS11'in `CKR_*` kodlarını `SigningError`'a çevirir.
  - `CertificateInfo`: TC no yalnızca maskeli tutulur (`12*******01`).
- `app/`: Compose Desktop (Material3). `AppState` tüm durum ve aksiyonları tutar; `ui/` saf composable'lar
  (`App(state)` kök). Tüm kart çağrıları tek thread'li `card` dispatcher'ında.
  Akış: kart seçimi (2 sn'de bir otomatik tarama, tek kart otomatik seçilir) → dosyalar + biçim →
  "İmzala" → onay penceresi (imzalayan, dosya özeti, PIN uyarıları, PIN, "inceledim" onayı) → tek login,
  tüm dosyalar, oturum kapanır → sonuç ekranı. Pencereler in-tree overlay (native dialog değil).
  - i18n: `Strings` (ResourceBundle, `{0}` yer tutucu, MessageFormat değil), `str("key")`, dil tercihi
    `java.util.prefs`'te. `StringsTest` iki dilin anahtar eşitliğini ve koddaki her `str("...")` anahtarını kontrol eder.
- `tools/screenshots.Dockerfile` + `app/src/test/.../ScreenshotTest.kt`: tüm ekranları örnek verilerle
  `app/build/screenshots/*.png`'ye çizer (TR, EN, koyu tema). Arayüz değişikliğinden sonra görüntülere bakarak
  kontrol et (ekran kaydı izni yok, Mac'teki pencere görülemiyor).
- `eimza-test/`: ilk CLI prototipi (ayrı Gradle projesi, root build'e dahil değil). Sadece referans.

## Komutlar

```sh
# Bir kez: hedef platform JDK'sı (URL Dockerfile başında; Intel Mac için x64 + --build-arg COMPOSE_TARGET=macos-x64)
mkdir -p vendor && curl -fL -o vendor/target-jdk.tar.gz "<temurin21 GitHub release URL>"

# Testler + app derlemesi (hızlı döngü, Gradle cache .gradle-home/ altında kalır)
container run --rm -m 6G -c 4 -v "$PWD:/src" -w /src -e GRADLE_USER_HOME=/src/.gradle-home \
  gradle:8-jdk21 gradle --no-daemon -PcomposeTarget=macos-arm64 :core:test :app:compileKotlin
# Tek test: ... gradle --no-daemon :core:test --tests 'sh.yetkin.esigner.core.DocumentSignerTest'

# Arayüz ekran görüntüleri (bir kez imaj: container build -m 8G -c 4 -t esigner-screenshots -f tools/screenshots.Dockerfile tools)
container run --rm -m 6G -c 4 -v "$PWD:/src" -w /src -e GRADLE_USER_HOME=/src/.gradle-home \
  esigner-screenshots gradle --no-daemon -PcomposeTarget=linux-arm64 :app:test

# Çalıştırılabilir paket (core testleri build içinde koşar) ve çalıştırma
container build -m 8G -c 4 --output type=local,dest=dist .
dist/linux_arm64/run.sh
```

- **Bellek kuralı (min 4 GB):** Apple container varsayılanları `container.memory = 1gb`, `build.memory = 2048mb`
  (`container system property list`); bu sürümde (1.5) kalıcı değiştirme yolu yok
  (`property set` yok, `~/.config/container/config.toml` ve app root'taki `config.toml` okunmuyor). Gradle + Kotlin derleyicisi
  bu bellekle kilitleniyor (container `kill`'e bile yanıt vermiyor). Bu yüzden:
  - `container run` her zaman `-m 6G -c 4` ile.
  - `container build` her zaman `-m 8G -c 4` ile. Bu parametreler verilmezse build, builder'ı varsayılan
    2 GB ile yeniden oluşturur (`container builder start -m` ile önceden açmak yetmez).
  - Kilitlenen container `kill`'e yanıt vermezse host'taki `container-runtime-linux ... --uuid <id>` süreci öldürülür.
- Skiko (Compose) native kısmı platforma özel: `-PcomposeTarget=macos-arm64|macos-x64|windows-x64|...`,
  verilmezse container'ın kendi platformu (linux) seçilir ve Mac'te çalışmaz.
- Testler kart gerektirmez: `TestKeys` kendinden imzalı yazılım anahtarı üretir (`Pkcs12SignatureToken`),
  sahte TC no kullanır. PDF kontrolü PDFBox 2.x (`PDDocument.load`), CAdES kontrolü BouncyCastle ile.

## Kesinleşmiş kararlar

- **Dil / platform:** Kotlin (JVM), JDK 21 (LTS). JDK 26 kullanma: Kotlin 2.0.21 derleyicisi onu desteklemiyor.
- **İmza motoru:** EU DSS (`eu.europa.ec.joinup.sd-dss`, sürüm 6.1). pyHanko ve Go alternatifleri değerlendirildi, `.imz` (CAdES) desteği nedeniyle DSS seçildi.
- **Arayüz:** Compose Multiplatform (Desktop).
- **Formatlar:**
  - PAdES (PDF içi imza): mevcut imzaları bozmadan ekler (artımlı güncelleme).
  - CAdES enveloping (`.imz`): her dosya türü; `dosya.ext` → `dosya.ext.imz`.
- **Mimari:** `core` (imzalama/doğrulama, arayüzden bağımsız) + `app` (Compose). İleride sunucu tarafı
  bölünmüş imzalamaya (sunucu hash hazırlar, istemci kartla imzalar) geçilecek; `core` buna uygun tasarlanmalı.

## Doğrulanmış teknik bulgular

- PKCS#11 kütüphanesi: macOS `/usr/local/lib/libakisp11.dylib` (universal binary: arm64 + x86_64).
  Windows'ta muhtemelen `C:\Windows\System32\akisp11.dll`, henüz doğrulanmadı.
- **Slot seçimi:** AKİS'in ilk slot'unun ID'si 0 değil 1. Slot'u ID ile değil sırayla seç:
  `Pkcs11SignatureToken(lib, PasswordInputCallback { pin }, -1, slotIndex, "")`
  (5 parametreli kurucu `KeyStore.PasswordProtection` değil `PasswordInputCallback` alır.)
- `CommonCertificateVerifier` paketi DSS 6.1'de `eu.europa.esig.dss.spi.validation`.
- SunPKCS11 ile `SHA256withRSA` sorunsuz çalışıyor (karta özel mekanizma ayarı gerekmedi).
- Kartta tek imza anahtarı/sertifikası var.
- DSS 6.1 PDFBox 2.0.x getirir (3.x değil). Yazılım anahtarı sınıfı `Pkcs12SignatureToken` (`KeyStoreSignatureToken` yok).
- Kart tespiti gerçek AKİS kartla doğrulandı: PIN'siz slot/token okuma, slot ID 1, açık imza sertifikası okunabiliyor.
- Headless ekran görüntüsü: `ImageComposeScene` + Skiko linux; imajda `libgl1` ve font (Inter/DejaVu) gerekir.
- Compose 1.7.3'te sürükle-bırak: `Modifier.dragAndDropTarget` + `event.nativeEvent as DropTargetDropEvent`
  (eski `DragData` API'si hata veriyor).
- Doğrulama: PAdES imzalı PDF Adobe Reader'da; `.imz` için
  `openssl cms -verify -inform DER -noverify -in X.imz -out cikti` + `cmp` ile birebir içerik kontrolü geçti.

## Güvenlik kuralları (kesin)

- **PIN asla otomatik olarak yeniden denenmez.** Hata olursa dur ve hatayı göster; yanlış denemeler kartı bloke eder.
- PIN loglanmaz, diske yazılmaz; kullanımdan sonra `CharArray` sıfırlanır.
- Kişisel veriler (TC kimlik no, e-posta) loglara, test çıktılarına veya commit'lere girmez.
- Kullanıcı ne gördüğünü imzalamalı: imzalanacak dosya imzadan önce kullanıcıya gösterilir/onaylatılır.

## Build ve ortam

- Kullanıcı Mac'e geliştirme aracı kurmak istemiyor (brew ile Java/Gradle kurma). Lokal build'ler
  **Apple container** (`container build`) ile alınır.
- Dockerfile akışı (root): `gradle:8-jdk21` ile test + derle → hedef platform JDK'sı (`vendor/target-jdk.tar.gz`,
  Mac'te indirilir) üzerinden `jlink` ile runtime üret → `scratch` aşamasıyla `dist/` dışa aktar.
  jlink'teki JDK ile hedef JDK sürümü birebir aynı olmalı (imaj sürümü sabitlenmeli).
- `container build ... --output type=local,dest=dist .` çıktıyı `dist/linux_arm64/` altına koyar.
- Apple container'ın ağı zaman zaman dışarı çıkamıyor (ping gateway'e gider, internete gitmez), bir süre
  sonra kendiliğinden düzeldi. Build ağ hatasıyla düşerse önce
  `container run --rm alpine ping -c2 1.1.1.1` ile kontrol et.
- Kullanıcının ağında `api.adoptium.net` TLS hatası veriyor; JDK'ları GitHub release'lerinden indir.
- Dağıtım paketleri (`.dmg`, `.msi`) her platformda kendi üzerinde `jpackage` ile üretilmeli → GitHub Actions matrisi.

## Yol haritası

1. **Tamamlandı (AKİS kartla doğrulandı):** Proje iskeleti (`core` + `app`), Compose arayüzü: dosya seç/sürükle-bırak, kart ve sertifika
   listesi (okunabilir ad, TC no maskeli), PIN girişi, PAdES/CAdES seçimi, sonuç ekranı.
2. İmza doğrulama ekranı; Kamu SM kök/ara sertifikaları uygulamaya gömülü güven listesi.
3. Görünür imza ve sayfadaki konum seçimi; `.imz` dosyasını açıp içeriği gösterme.
4. Zaman damgası (Kamu SM; standart dışı kimlik doğrulama başlığı gerektirebilir), PAdES/CAdES B-LT/LTA.
5. Seri ve paralel imza.
6. Paketleme ve kod imzalama (Apple notarization, Windows code signing), GitHub Actions.
7. İleride: sunucu tarafı bölünmüş imzalama, çok kullanıcı, ticarileştirme (hukuki uyum ayrıca incelenecek).
