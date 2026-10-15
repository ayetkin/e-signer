# Değişiklik günlüğü

[English](CHANGELOG.en.md)

Önemli değişiklikler bu dosyada tutulur. Biçim [Keep a Changelog](https://keepachangelog.com/tr-TR/1.1.0/),
sürüm numaraları [Semantic Versioning](https://semver.org/lang/tr/) kurallarına uyar. Yayımlanmamış
değişiklikler "Yayımlanmadı" başlığında toplanır; sürüm çıkarken başlık sürüm numarasıyla değiştirilir.

## [Yayımlanmadı]

## [0.5.0] - 2026-10-15

### Eklenenler

- Mali mühür kartıyla imzalama: kartta geçerli bir e-imza sertifikası yoksa mali mühür sertifikası seçilir,
  kart "Mali mühür, Vergi Kimlik No ..." olarak gösterilir. Gerçek mali mühür kartıyla henüz denenmedi.
- macOS menü çubuğunda Pencere (Küçült, Büyüt/Küçült) ve Yardım menüleri.
- Hakkında penceresi: sürüm, geliştirici, lisans ve kaynak kodu bağlantısı.
- GitHub'dan güncelleme denetimi (Yardım > Güncellemeleri Denetle). Yalnızca kullanıcı istediğinde çalışır.
- Windows (`.msi`, `.zip`) ve Linux (`.deb`, `.tar.gz`) paketleri. Bu paketler henüz test edilmedi.
- Release notlarını oluşturan değişiklik günlüğü.

### Değişenler

- Kart seçim ekranı ekranın ortasında küçük bir pencerede açılır. Çalışma alanı seçilen pencere ayarına
  göre açılır.
- Ayarlar kenar çubuğunun altından üstüne, uygulama adının yanına dişli simgesi olarak taşındı.
- "İmzala" düğmesine simge eklendi, düğmeler biraz yükseltildi.
- Önizleme açıkken dosya listesi daha dar.
- Belgeler Türkçe, İngilizceleri `.en.md` uzantılı dosyalarda.

### Düzeltilenler

- macOS uygulama menüsünde "About MainKt" yerine "E-Signer Hakkında" görünür.
- Düğme yazıları dikeyde ortalanır.
- macOS'ta Pencere > Küçült (Cmd+M) uygulamayı kilitliyordu.
- macOS'ta ilk açılışta menü çubuğunda yalnızca uygulama menüsü görünebiliyordu.

## [0.4.1] - 2026-10-15

İlk yayımlanan sürüm (yalnızca macOS).

### Eklenenler

- AKİS kartlarla PKCS#11 üzerinden imza. Kart PIN'siz algılanır, PIN her imza turunda bir kez sorulur
  ve otomatik olarak yeniden denenmez.
- PDF içine PAdES imza ve diğer dosyalar için CAdES `.imz`.
- İmza seviyeleri: B-B, zaman damgalı B-T (Kamu SM kimlik doğrulaması dahil) ve B-LT.
- PDF önizlemesi; görünür imzanın yeri ve boyutu önizlemeden seçilir.
- Kart olmadan imza doğrulama: gömülü ESHS kökleri, 5070 sayılı Kanun'a göre nitelikli sertifika ve
  mali mühür kontrolü.
- Bölümlü ayarlar penceresi, Türkçe ve İngilizce arayüz.
- Apple silicon ve Intel için macOS disk görüntüleri.

[Yayımlanmadı]: https://github.com/ayetkin/e-signer/compare/v0.5.0...HEAD
[0.5.0]: https://github.com/ayetkin/e-signer/compare/v0.4.1...v0.5.0
[0.4.1]: https://github.com/ayetkin/e-signer/releases/tag/v0.4.1
