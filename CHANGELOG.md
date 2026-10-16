# Değişiklik günlüğü

[English](CHANGELOG.en.md)

Önemli değişiklikler bu dosyada tutulur. Biçim [Keep a Changelog](https://keepachangelog.com/tr-TR/1.1.0/),
sürüm numaraları [Semantic Versioning](https://semver.org/lang/tr/) kurallarına uyar. Yayımlanmamış
değişiklikler "Yayımlanmadı" başlığında toplanır; sürüm çıkarken başlık sürüm numarasıyla değiştirilir.

## [Yayımlanmadı]

### Eklenenler

- Doğrulamada imzanın neden geçersiz ya da şüpheli olduğu somut olarak gösteriliyor: dosyanın imzadan sonra yeniden
  kaydedilmesi, imzalı içeriğin değişmesi, imza değerinin bozulması, sonradan değişen sayfalar (sayfa numaralarıyla) ve
  eklenen notlar.

### Değişenler

- Kenar çubuğundaki kart bölümü yenilendi: başlıkta simgeli "Değiştir" bağlantısı, karta tıklayınca Ayarlar > Kart'ta ayrıntıları, adın altında sertifika türü (nitelikli
  e-imza, mali mühür) ya da kart modeli, altta "Hazır" ya da PIN/sertifika uyarısı ve bitiş tarihi.

### Düzeltilenler

- Uygulama çok bellek tutuyordu: Java heap'i 2 GB ile sınırlandı ve boşta kullanılmayan bellek sisteme geri veriliyor
  (PDF önizlemesinden kalan sayfa görüntüleri artık birikmiyor).
- Çok sayıda dosya doğrulanırken bellek ve ağ yükü: aynı anda en fazla dört dosya doğrulanır, CRL ve ara sertifika
  indirmeleri birkaç dakika önbellekte tutulur (100 dosyada aynı CRL 100 kez indirilmez). B-LT imzada da aynı önbellek kullanılır.
- Kart ekranına dönerken (ör. imza bittikten sonra kenar çubuğundaki karta tıklanınca) önceki ekran bir an küçülen
  pencerede kart ekranıyla birlikte görünüyordu; geçiş artık anında.

## [0.5.2] - 2026-10-15

### Eklenenler

- Ayarlar > Genel'de görünüm seçimi: sistem, açık ya da koyu.

### Değişenler

- Güncellemeler açılışta da denetlenir (Ayarlar > Genel'den kapatılabilir); yeni sürüm varsa kenar çubuğunda indirme bağlantısı çıkar.
- Alttaki çubuk kaldırıldı; "İmzala" düğmesi kenar çubuğunun altında, kartın üstünde sabit.

## [0.5.1] - 2026-10-15

### Eklenenler

- Zaman damgası ayarları, sunucudan bir deneme damgası alınmadan kaydedilmez; adres ya da parola hatası imza
  sırasında değil ayar penceresinde görünür. Kamu SM adresi, "Kamu SM müşterisi" kimlik doğrulaması olmadan
  kaydedilemez.
- Kart seçim ekranında, kart ya da okuyucu bulunamadığında AKİS kart sürücüsünü indirme bağlantısı.
- Kenar çubuğunda zaman damgası satırı: kullanılan sunucu, eksik ayar ya da kullanılmadığı.
- Zaman damgalı imzalarda PDF'teki imza simgesi yeşil.
- Doğrulamada zaman damgasını veren sağlayıcı ve "Resmi zaman damgası" etiketi (damga geçerli ve yetkili bir
  sağlayıcıdan).
- Kenar çubuğundaki Biçim ve PDF üzerinde menülerinde her seçeneğin simgesi.
- Ayarlar > İmza'da "İmza nedeni" ve "Konum": PDF imzasına yazılır, PDF okuyucunun imza panelinde görünür.
  İmza nedeni varsayılan olarak "E-Signer ile imzalanmıştır"; değiştirilebilir ya da silinebilir.

### Değişenler

- Görünür imzanın yazısı (Dijital olarak imzalanmıştır, ad, zaman) Inter yazı tipiyle çizilir.
- Zaman damgası kimlik doğrulamasında "Yok" seçeneği kaldırıldı: sağlayıcılar kimlik bilgisi olmadan damga
  vermiyor. Varsayılan "Kamu SM müşterisi".
- Dosya listesindeki ve önizlemedeki açıklama yazıları kaldırıldı.
- Dosya listesi daha dar; listenin ve önizlemenin başlıkları aynı yükseklikte.
- Ayarlar penceresi daha geniş ve daha yüksek.

### Düzeltilenler

- Kamu SM zaman damgası sunucusu kullanıcı bilgisi olmadan boş yanıt verdiğinde "geçersiz yanıt" yerine
  "sunucu isteği reddetti, kimlik bilgilerini kontrol edin" hatası gösterilir.
- macOS'ta düğme yazıları dikeyde ortalanır.
- Yalnızca yazılı görünür imzada yazının solunda ve sağında aynı boşluk kalır; kutu, imza zamanının gerçek
  genişliğine ve DSS'in yazı ölçülerine göre hesaplanır.
- Uyarı kutularındaki simge yazının ilk satırıyla aynı hizada.

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

[Yayımlanmadı]: https://github.com/ayetkin/e-signer/compare/v0.5.2...HEAD
[0.5.2]: https://github.com/ayetkin/e-signer/compare/v0.5.1...v0.5.2
[0.5.1]: https://github.com/ayetkin/e-signer/compare/v0.5.0...v0.5.1
[0.5.0]: https://github.com/ayetkin/e-signer/compare/v0.4.1...v0.5.0
[0.4.1]: https://github.com/ayetkin/e-signer/releases/tag/v0.4.1
