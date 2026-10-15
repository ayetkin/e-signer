# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# E-imza masaüstü uygulaması

Türkiye'deki nitelikli e-imza kartlarıyla (ilk hedef: AKİS) PDF ve diğer dosyaları imzalayan,
macOS (arm64 + x86_64) ve Windows (x64) üzerinde çalışan masaüstü uygulaması.
GitHub'da AGPL-3.0 ile açık kaynak (`LICENSE`, üçüncü taraf lisansları `NOTICE`). Aşama aşama ilerliyoruz.
Kişisel/ortama özel notlar git'e girmeyen `CLAUDE.local.md` içinde; bu dosya herkese açık, kişisel bilgi yazma.

## Depo yapısı

- **Kodda Türkçe yok:** kod, yorum, test verisi ve kullanıcı metinleri dahil. Uygulama iki dilli (TR/EN), tüm
  kullanıcı metinleri `app/src/main/resources/i18n/strings_{tr,en}.properties` içinde. `core` hata kodu döndürür.
- `core/`: arayüzden bağımsız imzalama (`sh.yetkin.esigner.core`).
  - `DocumentSigner`: herhangi bir `SignatureTokenConnection` ile imzalar. DSS'in üç adımlı akışı
    (`getDataToSign` → `token.sign` → `signDocument`) ayrı tutuldu, ileride bölünmüş imzalama buraya oturacak.
    Çıktı önce geçici dosyaya yazılır, sonra yerine taşınır. Var olan dosyanın üzerine asla yazmaz.
    PAdES çıktı soneki (`-imzali`/`-signed`) arayüz dilinden gelir. `SigningLevel` (B-B / B-T / B-LT) ve `TSPSource`
    kurucudan gelir; zaman damgası son adımda (kart imzaladıktan sonra) alınır. B-LT zincirleri ve OCSP/CRL'i
    `certificateVerifier(TrustAnchors.bundled(), online = true)` ile çevrimiçi toplayıp gömer.
  - `HttpTimestampSource` (`Timestamping.kt`): RFC 3161 istemcisi (DSS `TSPSource`), her istekte nonce ve yanıt
    eşleşme kontrolü. `TimestampAuth`: `None`, `Basic` (HTTP Basic), `KamuSm`. Parolalar `CharArray`, sahibi çağıran,
    `clear()` ile sıfırlanır, `toString()` parolayı içermez.
  - **Kamu SM kimlik başlığı** (`KamuSmIdentity`): HTTP başlığı `identity` = DER
    `SEQUENCE { müşteriNo INTEGER, salt OCTET STRING, iterasyon INTEGER (100), iv OCTET STRING, şifreliÖzet OCTET STRING }`
    hex'i. Şifreli özet: istekteki `hashedMessage`, AES-256-CBC (PKCS#7), anahtar PBKDF2-HMAC-SHA256(parola, salt, 100),
    salt = IV (16 bayt rastgele). Yanlış parola/bakiye yoksa TSA `rejection` döner → `TIMESTAMP_REJECTED`.
    Kaynak: açık kaynak `KilimcininKorOglu/kamusm-go` (`identity.go`); Kamu SM'nin resmi dokümanı bulunamadı,
    gerçek hesapla henüz denenmedi. Varsayılan adres `http://zd.kamusm.gov.tr`. E-GÜVEN TSA adresi/yetkilendirmesi
    herkese açık değil (satın alınca müşteri panelinde veriliyor); büyük olasılıkla `Basic` ya da `None` yeter.
  - `CardScanner` + `Pkcs11Scanner.java`: PIN'siz kart tespiti. JDK'nın dahili PKCS#11 sarmalayıcısıyla
    (`sun.security.pkcs11.wrapper`) slot/token bilgisi, PIN sayaç bayrakları (`COUNT_LOW`/`FINAL_TRY`/`LOCKED`) ve
    karttaki açık sertifikalar okunur. Derleme ve çalıştırmada `--add-modules jdk.crypto.cryptoki` +
    `--add-exports jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED` gerekir (build.gradle.kts, run.sh).
    Modül, SunPKCS11 ile aynı `PKCS11.getInstance` önbelleğini paylaşır (aynı init bayrakları kullanılmalı).
  - `CardSession`: tek login denemesi. Gerçek slot ID'si ile açılır (`Pkcs11SignatureToken(..., slotId, -1, "")`,
    DSS bu durumda `slot = <id>` yazar). PIN dizisinin sahipliğini alır, `close()`'da sıfırlar.
  - `SigningException(error: SigningError, subject)`: `stopsBatch` (kart hataları + `TIMESTAMP_*`,
    `REVOCATION_UNAVAILABLE`) ise toplu imza durur, dosya hatasıysa devam eder.
    `cardError()` SunPKCS11'in `CKR_*` kodlarını `SigningError`'a çevirir.
  - `CertificateInfo`: TC no yalnızca maskeli tutulur (`12*******01`). `isQualified5070`: sertifikada
    `2.16.792.1.61.0.1.5070.1.1` QC beyanı var (5070'e göre nitelikli sertifika).
  - `SignatureValidator`: DSS varsayılan ETSI politikasıyla PAdES/CAdES doğrulama, çevrimiçi OCSP/CRL/AIA
    (`dss-service`), `.imz` içeriğini çıkarma. `SignatureCheck.isLegallyValid` = ETSI TOTAL_PASSED + zincir
    güven listesine ulaşıyor + imzalayan sertifikası 5070 nitelikli. `TokenExtractionStrategy.EXTRACT_CERTIFICATES_ONLY`
    olmadan tanılama verisinde sertifika içeriği gelmez.
  - `TrustAnchors` + `core/src/main/resources/trust/`: BTK yetkili ESHS kökleri (DER) ve `anchors.properties`
    (dosya=SHA-256, her kökün kaynağı yorumda). Yükleme sırasında özet uyuşmazsa hata. **Kök ekleme kuralı:**
    Kamu SM ulusal deposunda (`sertifikalar.kamusm.gov.tr/depo/SertifikaDeposu.xml`) bulunmalı ya da sağlayıcının
    kendi HTTPS sitesinde yayımladığı özetle eşleşmeli. BTK özet yayımlamıyor. Dışarıda bırakılanlar: TürkTrust H8
    ve ArkSigner RSA S1 (bağımsız teyit yok), TürkTrust H9 (e-mühür), EGMSM (siteye erişilemedi).
- `app/`: Compose Desktop (Material3). `AppState` tüm durum ve aksiyonları tutar; `ui/` saf composable'lar
  (`App(state)` kök). Tüm kart çağrıları tek thread'li `card` dispatcher'ında.
  Sekmeler: İmzala / Doğrula (doğrulama kart gerektirmez, kart ekranında "Kart olmadan imza doğrula").
  İmza akışı: kart seçimi (2 sn'de bir otomatik tarama, tek kart otomatik seçilir) → dosyalar + biçim →
  "İmzala" → onay penceresi (imzalayan, dosya özeti, PIN uyarıları, PIN, "inceledim" onayı) → tek login,
  tüm dosyalar, oturum kapanır → sonuç ekranı. Pencereler in-tree overlay (native dialog değil).
  - `SigningSettings`: imza seviyesi, TSA adresi, kimlik doğrulama tipi ve kullanıcı/müşteri no `java.util.prefs`'te;
    TSA parolası yalnızca bellekte (uygulama kapanınca istenir), her imza turuna kopyası verilir ve tur bitince
    sıfırlanır. Zaman damgalı seviyede TSA eksikse "İmzala" kapalı. PKCS#11 kütüphane yolu ve açılış pencere
    modu (`WindowMode`: ekranı kapla / son boyut / standart) da burada, prefs'te.
  - Ayar penceresi `ui/SettingsDialog.kt`: bölümlü (`SettingsSection`: Genel = dil + pencere, İmza = seviye +
    görünür imza, Zaman damgası, Kart = kütüphane yolu + takılı kartların PIN'siz okunan ayrıntıları). Taslak
    üzerinde çalışır, "Kaydet" ile uygulanır. Kenar çubuğundaki "Ayarlar", kart ekranındaki dişli ve macOS
    Preferences menüsü açar.
  - i18n: `Strings` (ResourceBundle, `{0}` yer tutucu, MessageFormat değil), `str("key")`, dil tercihi
    `java.util.prefs`'te. `StringsTest` iki dilin anahtar eşitliğini ve koddaki her `str("...")` anahtarını kontrol eder.
- Arayüz düzeni: çalışma alanı ve sonuç ekranı `AppShell` (`ui/Sidebar.kt`) içinde. Sol panelde sekmeler,
  kart (Değiştir), imza ayarları (biçim, görünür imza, seviye) ve "Ayarlar"; sağda içerik ve yalnızca "İmzala" düğmeli alt çubuk.
- Simge: `ui/AppIcon.kt` kodla çizilir (uygulama içi logo + pencere simgesi). `IconTest` 1024 px PNG üretir
  (`app/build/icon/`), sonuçlar `app/packaging/` altına kopyalanır; `icon.icns` macOS'ta `sips` + `iconutil`
  ile üretildi. Windows `.ico` henüz yok.
- `tools/screenshots.Dockerfile` + `app/src/test/.../ScreenshotTest.kt`: tüm ekranları örnek verilerle
  `app/build/screenshots/*.png`'ye çizer (TR, EN, koyu tema). Arayüz değişikliğinden sonra görüntülere bakarak
  kontrol et.

## Komutlar

Günlük kullanım için `Makefile` (bellek parametreleri ve mimari seçimi içinde sabit):

```sh
make build        # hedef JDK yoksa indirir, core testleri + paket -> dist/linux_<arch>/
make run          # paketi çalıştırır (yoksa önce build)
make test         # core testleri + app derlemesi
make screenshots  # app testleri (i18n kontrolü + ekran görüntüleri), font imajını da hazırlar
```

Altta çalışan komutlar (tek test gibi özel durumlar için):

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

# Yerel macOS .app (Dock/menüde "java" yerine "E-Signer" ve uygulama simgesi). jpackage, vendor/'daki
# hedef JDK arşivinden vendor/target-jdk/ altına açılıp kullanılır, sisteme bir şey kurulmaz.
tools/package-mac.sh && open dist/mac/E-Signer.app
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
- Zaman damgası testleri ağ gerektirmez: `LocalPki` (test) loopback'te HTTP sunucusu açar; `/tsa` BouncyCastle
  `TimeStampResponseGenerator` ile cevap verir (isteğe bağlı Kamu SM `identity` kontrolü), `/ca.crl` CA'nın CRL'i.
  B-LT imzası çevrimdışı doğrulayıcıda VALID çıkıyor (gömülü CRL yetiyor).
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
- **Slot seçimi:** AKİS'in ilk slot'unun ID'si 0 değil 1, bu yüzden slot ID'si tahmin edilmez. `CardScanner` gerçek
  slot ID'sini okur, `CardSession` o ID ile açılır:
  `Pkcs11SignatureToken(lib, PasswordInputCallback { pin }, slotId, -1, "")`
  (5 parametreli kurucu `KeyStore.PasswordProtection` değil `PasswordInputCallback` alır.)
- DSS CRL ayrıştırıcısı ayrı modül: `dss-crl-parser-stream` olmadan CRL'e düşen doğrulama/B-LT
  `No implementation found for ICRLUtils` ile patlar.
- `CommonCertificateVerifier` paketi DSS 6.1'de `eu.europa.esig.dss.spi.validation`.
- SunPKCS11 ile `SHA256withRSA` sorunsuz çalışıyor (karta özel mekanizma ayarı gerekmedi).
- Kartta tek imza anahtarı/sertifikası var.
- DSS 6.1 PDFBox 2.0.x getirir (3.x değil). Yazılım anahtarı sınıfı `Pkcs12SignatureToken` (`KeyStoreSignatureToken` yok).
- Uygulamanın attığı imzalar şu an PAdES/CAdES B-B (zaman damgası yok): doğrulamada imza zamanı "kanıtlanmıyor"
  görünür, sertifika süresi dolunca OUT_OF_BOUNDS_NO_POE ile belirsize düşer. Çözüm yol haritası 4. adım.
- **Görünmez imza alanı sıfır boyutlu olmamalı:** DSS'in varsayılan görünmez imzası `/Rect [0 0 0 0]` yazar. Bazı
  PDF okuyucular kesirli A4 sayfalarda (`595.27 x 841.89`, yaygın üretici çıktısı)
  bu alanı "Unsigned Signature Field" gösterir; tam sayı boyutlarda (`595 x 842`) sorun yok. Adım adım
  test edilerek bulundu (alt filtre, `/OpenAction`, belge `/ID`, içerik, yazı tipi, dosya boyutu elendi).
  Çözüm `DocumentSigner`: ilk sayfanın üst kenarında 1x1 pt şeffaf PNG görünümlü alan; mevcut annotasyonlarla
  çakışmayan ilk boş nokta seçilir (DSS çakışan alanı reddeder, üst üste imzayı kırar).
- Kamu SM mali mühür: politika `2.16.792.1.2.1.1.5.7.4.1`, 5070 QC beyanı ve `nonRepudiation` yok, kök
  "KamuSM Kurumsal Kök" S1/S2 (güven listesinde). Hukuki dayanak 509 Sıra No'lu VUK Genel Tebliği; doğrulamada
  `isValidFiscalSeal` ile 5070'ten ayrı gösterilir. Kamu SM bazı özetleri DER değil sunulan PEM dosyası üzerinden yayımlıyor.
- Kart tespiti gerçek AKİS kartla doğrulandı: PIN'siz slot/token okuma, slot ID 1, açık imza sertifikası okunabiliyor.
- Headless ekran görüntüsü: `ImageComposeScene` + Skiko linux; imajda `libgl1` ve font (Inter/DejaVu) gerekir.
- Compose 1.7.3'te sürükle-bırak: `Modifier.dragAndDropTarget` + `event.nativeEvent as DropTargetDropEvent`
  (eski `DragData` API'si hata veriyor).
- Doğrulama: PAdES imzalı PDF Adobe Reader'da; `.imz` için
  `openssl cms -verify -inform DER -noverify -in X.imz -out cikti` + `cmp` ile birebir içerik kontrolü geçti.

## Güvenlik kuralları (kesin)

- **PIN asla otomatik olarak yeniden denenmez.** Hata olursa dur ve hatayı göster; yanlış denemeler kartı bloke eder.
- PIN loglanmaz, diske yazılmaz; kullanımdan sonra `CharArray` sıfırlanır.
- TSA parolası yalnızca bellekte tutulur, kalıcı saklanmaz (kullanıcı kararı): uygulama her açılışta yeniden sorar.
  Keychain/Credential Manager veya şifreli dosyaya yazma eklenmez.
- Kişisel veriler (TC kimlik no, e-posta) loglara, test çıktılarına veya commit'lere girmez.
- Kullanıcı ne gördüğünü imzalamalı: imzalanacak dosya imzadan önce kullanıcıya gösterilir/onaylatılır.

## Build ve ortam

- İki derleme yolu: JDK 21 ile `./gradlew` (Gradle wrapper, CI de bunu kullanır: `.github/workflows/ci.yml`) ya da
  `Makefile` ile **Apple container** (`container build`), hostta JDK gerekmeden.
- Dockerfile akışı (root): `gradle:8-jdk21` ile test + derle → hedef platform JDK'sı (`vendor/target-jdk.tar.gz`,
  Mac'te indirilir) üzerinden `jlink` ile runtime üret → `scratch` aşamasıyla `dist/` dışa aktar.
  jlink'teki JDK ile hedef JDK sürümü birebir aynı olmalı (imaj sürümü sabitlenmeli).
- `container build ... --output type=local,dest=dist .` çıktıyı `dist/linux_arm64/` altına koyar.
- Dağıtım paketleri (`.dmg`, `.msi`) her platformda kendi üzerinde `jpackage` ile üretilmeli → GitHub Actions matrisi.

## Yol haritası

1. **Tamamlandı (AKİS kartla doğrulandı):** Proje iskeleti (`core` + `app`), Compose arayüzü: dosya seç/sürükle-bırak, kart ve sertifika
   listesi (okunabilir ad, TC no maskeli), PIN girişi, PAdES/CAdES seçimi, sonuç ekranı.
2. **Yapıldı, gerçek imzalı dosyayla denenmesi gerekiyor:** İmza doğrulama (Doğrula sekmesi), BTK yetkili ESHS
   kökleriyle gömülü güven listesi, 5070 nitelik kontrolü, `.imz` içeriğini açma/kaydetme.
3. **Kısmen yapıldı:** PDF önizleme (`ui/PdfPreview.kt`, PDFBox ile sayfa sayfa) ve görünür imza
   (`core/VisibleStamp.kt`: yazı ve/veya simge, kenar çubuğunda "PDF üzerinde"). Kullanıcı önizlemede sayfaya
   tıklayarak ya da kutuyu sürükleyerek yer seçer (dosya başına `AppState.stampPositions`); seçilmezse son sayfanın
   sağ altında önceki imzalarla çakışmayan ilk boş yer. Yazı kutusu genişliği metinden ölçülür (`VisibleStamp.size`),
   böylece DSS `FILL_BOX` kendi boşluğunu eklemez; simge ile yazı arasındaki boşluk simge PNG'sinin sağına eklenen
   ve üst/alt kenarlarına eklenen saydam alan (`ICON_RATIO` 0.7: simge yazı yüksekliğinin %70'i, `GAP_RATIO`).
   Yükseklik önizlemede kutunun köşesinden çekilerek 16-80 pt (varsayılan 30, `SigningSettings.stampHeight`, tüm
   PDF'ler için tek değer; ayar penceresinde de var). Önizleme sayfaları ekranın piksel genişliğinde çizilir (Retina
   dahil, en fazla 4096 px). Yakınlaştırma: düğmeler, Ctrl/Cmd + kaydırma, trackpad pinch (%50-400).
   **Pinch:** Java'da genel API yok; `MacGestures` JDK'nın macOS'a özel `com.apple.eawt.event` paketini reflection
   ile kullanır (derleme Linux'ta olduğu için). Çalıştırırken `--add-exports java.desktop/com.apple.eawt.event=ALL-UNNAMED`
   gerekir (run.sh, package-mac.sh, app/build.gradle.kts); yoksa sessizce devre dışı kalır.
   Çalışma alanı: içerik 640 dp'den genişse liste ve önizleme yan yana (2:3), değilse alt alta. Çakışan/taşan seçim `STAMP_POSITION_INVALID`.
   Döndürülmüş sayfalarda yer seçimi kapalı. Kalan: `.imz` içeriğini gösterme.
4. **Yapıldı, gerçek TSA ile denenmesi gerekiyor:** Zaman damgası (B-T), B-LT (OCSP/CRL gömülü), Kamu SM kimlik
   başlığı, ayar penceresi. Kalan: B-LTA (arşiv zaman damgası).
5. Seri ve paralel imza.
6. Paketleme ve kod imzalama (Apple notarization, Windows code signing), GitHub Actions.
7. İleride: sunucu tarafı bölünmüş imzalama, çok kullanıcı, ticarileştirme (hukuki uyum ayrıca incelenecek).
