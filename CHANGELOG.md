# Değişiklik günlüğü

[English](CHANGELOG.en.md)

Önemli değişiklikler bu dosyada tutulur. Biçim [Keep a Changelog](https://keepachangelog.com/tr-TR/1.1.0/),
sürüm numaraları [Semantic Versioning](https://semver.org/lang/tr/) kurallarına uyar. Yayımlanmamış
değişiklikler "Yayımlanmadı" başlığında toplanır; sürüm çıkarken başlık sürüm numarasıyla değiştirilir.

## [Yayımlanmadı]

## [0.7.2] - 2026-11-19

### Değişenler

- Güncelleme satırı ayrı bir güncelleme penceresi açıyor: sürüm geçişi, ilerleme çubuğu (indirilen MB ve yüzde),
  indirmeyi durdurma, SHA-256 doğrulaması sonrası "Kurulumu başlat" ve "Klasörde göster". İndirme pencere
  kapansa da sürüyor ve kenar çubuğunda yüzdesi görünüyor. Paket artık indirilince kendiliğinden açılmıyor.

## [0.7.1] - 2026-11-19

### Değişenler

- Paket adlarında artık sürüm yok (`E-Signer-macos-arm64.dmg`, `E-Signer-windows-x64.msi` ...). README ve
  INSTALL'daki indirme bağlantıları her zaman son sürümü indiriyor.

## [0.7.0] - 2026-11-19

### Değişenler

- macOS paketleri Apple Developer ID ile imzalı ve Apple tarafından onaylı (notarized). Uygulama ilk açılışta
  Sistem Ayarları > Gizlilik ve Güvenlik'ten izin istemeden açılıyor.
- Yeni sürüm macOS ve Windows'ta uygulamanın içinden indiriliyor: "İndir" paketi (`.dmg` / `.msi`) İndirilenler
  klasörüne indirir, GitHub'ın yayımladığı SHA-256 özetiyle karşılaştırır ve açar. Linux'ta sürümler sayfası açılıyor.

## [0.6.1] - 2026-11-19

### Değişenler

- İmzalı PDF'in adı arayüz dilinden bağımsız olarak her zaman `-signed` ile bitiyor (`rapor-signed.pdf`);
  Türkçe arayüzdeki `-imzali` soneki kaldırıldı.
- Kart ekranındaki sürücü indirme bağlantısı TÜBİTAK BİLGEM'in AKİS destek sayfasını açıyor
  (https://akiskart.bilgem.tubitak.gov.tr/en/support/).

### Düzeltilenler

- Çok uzun PDF sayfaları (tek sayfalık tarayıcı çıktısı gibi) önizlemede en fazla bir A4 sayfası kadar piksel
  ile çiziliyor. Önceden böyle bir sayfa yüzlerce MB bellek isteyip uygulamayı kapatabiliyordu.
- Uzun dönemli (B-LT) imzada PDF'teki önceki bir imza geçersizse ya da imzacısının sertifikasının süresi dolmuş
  veya iptal edilmişse hata kart imzalamadan ve zaman damgası alınmadan, zaman damgalı seviyeyi öneren ayrı bir
  mesajla veriliyor. Önceden dosya başına bir kart imzası ve ücretli zaman damgası harcanıyor, "dosya bozuk ya da
  desteklenmiyor" hatası çıkıyordu.
- Doğrulamada imzadan önce alınmış içerik zaman damgası artık imza zamanının kanıtı olarak gösterilmiyor.
  Önceden yalnızca içerik damgası olan bir imza "resmi zaman damgası" etiketi alıyordu.
- 5120 pt'den geniş PDF sayfaları (plotter çizimi, afiş) önizlemede uygulamayı kapatmıyor.
- Uzun PDF'lerde hızlı kaydırınca ekrandan çıkan sayfalar çizilmiyor, görünen sayfa daha çabuk geliyor.
- Hatalı zaman damgası adresi (`http:zd.kamusm.gov.tr` gibi) ayar penceresini "test ediliyor" durumunda
  bırakmıyor, "sunucuya ulaşılamadı" hatası veriyor. Büyük harfli `HTTPS://` adresler de kabul ediliyor.
- Çıktı klasörüne yazılamıyorsa (salt okunur disk ya da paylaşım) hata kart imzalamadan ve zaman damgası
  alınmadan veriliyor. Önceden her dosya için kart imzası ve ücretli zaman damgası harcanıyordu.
- Kart kütüphanesi olarak seçilen dosya yüklenemiyorsa "kart işlemi başarısız" yerine "kart kütüphanesi
  yüklenemedi" hatası gösteriliyor.
- Ayarlarda "Parolayı unut" kaydediliyor. Önceden zaman damgası sunucusu tanımlıyken kayıt bir test zaman
  damgası istiyor ve hiçbir ayar kaydedilmiyordu.
- Uzun dönemli (B-LT) imzada imza sertifikası iptal edilmişse toplu imza ilk dosyada
  duruyor ve iptal hatası gösteriliyor. Önceden her dosya "desteklenmiyor" diye geçiyor, her biri için kart
  imzası ve zaman damgası harcanıyordu.
- Kart modülü yanıt vermeyi bıraktığında (örneğin kart uygulama açıkken çıkarılınca) pencereyi kapatmak
  uygulamayı dondurmuyor: imza sürmüyorsa hemen, sürüyorsa en fazla 10 saniye bekleyip kapanıyor.

## [0.6.0] - 2026-10-16

### Eklenenler

- Doğrulamada imzanın neden geçersiz ya da şüpheli olduğu somut olarak gösteriliyor: dosyanın imzadan sonra yeniden
  kaydedilmesi, imzalı içeriğin değişmesi, imza değerinin bozulması, sonradan değişen sayfalar (sayfa numaralarıyla) ve
  eklenen notlar.

### Değişenler

- Kenar çubuğundaki kart bölümü yenilendi: başlıkta simgeli "Değiştir" bağlantısı, karta tıklayınca Ayarlar > Kart'ta ayrıntıları, adın altında sertifika türü (nitelikli
  e-imza, mali mühür) ya da kart modeli, altta "Hazır" ya da PIN/sertifika uyarısı ve bitiş tarihi.
- Ayarlar > Kart ve Hakkında pencereleri kenar çubuğuyla aynı dile getirildi: takılı her kart avatar, sertifika türü ve
  durum satırıyla bir karo, altında "Kart" ve "Sertifika" sütunları; okuyucu/kart yoksa uyarı karosu, "Yeniden tara"
  başlıkta. Hakkında'da geliştirici, lisans, kaynak kodu ve güncelleme simgeli satırlar halinde.

### Düzeltilenler

- Uygulama çok bellek tutuyordu: Java heap'i 64 MB ile başlayıp 2 GB ile sınırlandı, boşta kalınca tam bir çöp toplama
  ile kullanılmayan bellek sisteme geri veriliyor (PDF önizlemesinden kalan sayfa görüntüleri artık birikmiyor) ve
  önizlemede yakınlaştırılmış sayfalar en fazla 2560 piksel genişliğinde çizilip ötesinde büyütülüyor.
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

[Yayımlanmadı]: https://github.com/ayetkin/e-signer/compare/v0.7.2...HEAD
[0.7.2]: https://github.com/ayetkin/e-signer/compare/v0.7.1...v0.7.2
[0.7.1]: https://github.com/ayetkin/e-signer/compare/v0.7.0...v0.7.1
[0.7.0]: https://github.com/ayetkin/e-signer/compare/v0.6.1...v0.7.0
[0.6.1]: https://github.com/ayetkin/e-signer/compare/v0.6.0...v0.6.1
[0.6.0]: https://github.com/ayetkin/e-signer/compare/v0.5.2...v0.6.0
[0.5.2]: https://github.com/ayetkin/e-signer/compare/v0.5.1...v0.5.2
[0.5.1]: https://github.com/ayetkin/e-signer/compare/v0.5.0...v0.5.1
[0.5.0]: https://github.com/ayetkin/e-signer/compare/v0.4.1...v0.5.0
[0.4.1]: https://github.com/ayetkin/e-signer/releases/tag/v0.4.1
