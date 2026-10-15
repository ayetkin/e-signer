# E-Signer

[English](README.en.md)

Türkiye'deki nitelikli elektronik imza kartlarıyla PDF ve diğer dosyaları imzalayan, imzalı
dosyaları doğrulayan masaüstü uygulaması. macOS (Apple silicon ve Intel) için geliştirilip test ediliyor;
Windows ve Linux paketleri de üretiliyor ancak henüz test edilmedi.

![İmzalama ekranı](docs/screenshots/sign.png)

## Özellikler

- **Akıllı kartla imza** (PKCS#11). AKİS kartlarla denendi; mali mühür kartları da destekleniyor. Kart PIN girmeden algılanır; PIN her imza
  turunda bir kez sorulur ve hiçbir zaman otomatik olarak yeniden denenmez.
- PDF'lerin içine **PAdES** imza (önceki imzalar geçerliliğini korur), diğer her dosya için **CAdES** `.imz`.
- **İmza seviyeleri:** B-B, RFC 3161 zaman damgalı B-T (Kamu SM kimlik doğrulaması dahil) ve sertifika
  zincirleri ile OCSP/CRL yanıtlarının gömüldüğü B-LT.
- PDF'lerde **görünür imza**: yerini ve boyutunu önizlemede seçin ya da uygulama boş bir yer bulsun.
- **İmzadan önce inceleme:** PDF önizlemesi ve neyin hangi sertifikayla imzalanacağını gösteren onay penceresi.
- Kart olmadan PAdES ve CAdES imzalarını **doğrulama**: Türkiye'deki nitelikli elektronik sertifika
  hizmet sağlayıcılarının gömülü kök sertifikalarıyla, 5070 sayılı Kanun'a göre nitelikli sertifika ve
  mali mühür kontrolüyle.
- Türkçe ve İngilizce arayüz.

| Onay | Doğrulama |
| --- | --- |
| ![Onay](docs/screenshots/confirm.png) | ![Doğrulama](docs/screenshots/verify.png) |

## İndirme

Paketler [sürümler sayfasında](https://github.com/ayetkin/e-signer/releases):

| Sistem | Paket | Durum |
| --- | --- | --- |
| macOS, Apple silicon | `E-Signer-<sürüm>-macos-arm64.dmg` | Test edildi |
| macOS, Intel | `E-Signer-<sürüm>-macos-x64.dmg` | Test edildi |
| Windows x64 | `.msi` kurulum ya da `.zip` | **Test edilmedi** |
| Linux x64 | `.deb` ya da `.tar.gz` | **Test edilmedi** |

Java uygulamanın içinde gelir, ayrıca kurmanız gerekmez. Gerekenler (kart sürücüsü, okuyucu) ve
uygulamanın ilk açılışı için [INSTALL.md](INSTALL.md) dosyasına bakın. Değişiklikler
[CHANGELOG.md](CHANGELOG.md) dosyasında.

## Durum

Geliştirme sürüyor. İmzalama ve kart algılama macOS'ta gerçek AKİS kartlarla denendi. **Windows ve
Linux'ta hiç test edilmedi:** paketler otomatik olarak üretiliyor ama uygulamanın bu sistemlerde açıldığı,
kartı algıladığı ya da imza attığı doğrulanmadı. Gerçek zaman damgası sunucuları ve mali mühür kartlarıyla
kullanım da daha geniş test gerektiriyor. Bu sistemlerde deneyip sonucu
[bildirirseniz](https://github.com/ayetkin/e-signer/issues) çok yardımcı olur.

> **Sorumluluk reddi.** E-Signer sertifikalı bir ürün değildir. İmzayı nitelikli yapan bu yazılım
> değil, kartınızdaki nitelikli sertifika ve anahtardır. Önemli işlerde kullanmadan önce imzalı
> dosyaları karşı tarafın güvendiği araçlarla kontrol edin. Yazılım hiçbir garanti olmaksızın
> sunulur (bkz. [LICENSE](LICENSE)).
>
> Doğrulama sekmesinin verdiği "geçerli" kararının hukuki bir bağlayıcılığı yoktur. Bu karar resmi bir
> tespit değil, teknik bir ön kontroldür. Türkiye'de eIDAS'taki "nitelikli doğrulama hizmeti" gibi resmi
> bir statü yoktur. Uyuşmazlık çıkarsa mahkeme imzayı bilirkişiye inceletir, uygulamanın ekranı delil
> yerine geçmez. Aynı durum Adobe Reader ve diğer imza araçları için de geçerlidir.

## Derleme ve çalıştırma

Gerekenler: JDK 21 ve kartınızın PKCS#11 sürücüsü (AKİS için macOS'ta `libakisp11.dylib`, Windows'ta
`akisp11.dll`, Linux'ta `libakisp11.so`). Sürücü yolu Ayarlar > Kart'tan değiştirilebilir.

```sh
./gradlew :core:test          # core testleri, kart gerekmez
./gradlew :app:run            # uygulamayı başlatır
```

Testler yazılım anahtarı ve yerel bir zaman damgası sunucusu kullanır; kart da ağ da gerekmez.
`./gradlew :app:test` ayrıca tüm ekranları `app/build/screenshots` altına çizer.

Depoda Linux container içinde (Apple `container`) derleme için bir `Makefile` de var; bu yol `dist/`
altına kendi Java çalışma ortamıyla birlikte bir paket üretir.

## Proje yapısı

- `core/` arayüzden bağımsız imzalama, zaman damgası ve doğrulama (EU DSS 6.1).
- `app/` Compose Multiplatform masaüstü uygulaması.
- `tools/` paketleme ve ekran görüntüsü araçları.

## Güvenlik

Güvenlik açıklarını lütfen gizli olarak bildirin; bkz. [SECURITY.md](SECURITY.md).

## Katkı

Hata kayıtları ve pull request'ler memnuniyetle karşılanır; bkz. [CONTRIBUTING.md](CONTRIBUTING.md).

## Lisans

E-Signer [GNU Affero Genel Kamu Lisansı v3.0](LICENSE) ile lisanslanmıştır. Üçüncü taraf bileşenler
kendi lisanslarını korur; bkz. [NOTICE](NOTICE).
