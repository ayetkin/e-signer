# Katkı

[English](CONTRIBUTING.en.md)

E-Signer'a ilginiz için teşekkürler. Hata kayıtları, fikirler ve pull request'ler memnuniyetle karşılanır.

## Başlamadan önce

Küçük bir düzeltmeden büyük işler için önce bir kayıt açın; yaklaşım üzerinde birlikte anlaşalım.

## Geliştirme

- JDK 21. Core testleri için `./gradlew :core:test`, arayüz testleri için `./gradlew :app:test`
  çalıştırın; arayüz testleri tüm ekranları `app/build/screenshots` altına da çizer. Arayüz
  değişikliklerinden sonra ekran görüntülerine bakın.
- Testler kart ya da ağ gerektirmemeli: `core/src/test` içindeki `TestKeys` (yazılım anahtarları) ve
  `LocalPki` (yerel zaman damgası sunucusu ve CRL) kullanılır.
- Kod, yorumlar, tanımlayıcılar ve test verileri İngilizcedir. Kullanıcıya görünen tüm metinler iki
  dilde `app/src/main/resources/i18n/strings_{en,tr}.properties` içindedir; `StringsTest` iki dosyanın
  aynı anahtarlara sahip olduğunu kontrol eder. `core` arayüz metni değil, hata kodu döndürür.
- `core` arayüzden bağımsız kalmalıdır.
- [SECURITY.md](SECURITY.md#tasarım-kuralları) içindeki kurallara uyun: PIN asla yeniden denenmez;
  PIN, parola ya da kişisel veri loglanmaz ve saklanmaz; testlere, loglara ya da commit'lere gerçek
  kişisel veri girmez.
- Kullanıcıya görünen her değişikliği [CHANGELOG.md](CHANGELOG.md) ve [CHANGELOG.en.md](CHANGELOG.en.md)
  içinde "Yayımlanmadı" / "Unreleased" başlığına ekleyin.

## Pull request'ler

- Her pull request tek bir konu içersin ve yeni davranışın testleri olsun.
- Neyin değiştiğini ve nasıl test ettiğinizi yazın; kartla denediyseniz kartı ve işletim sistemini belirtin.

## Sürüm çıkarma

1. `gradle.properties` içinde `appVersion` değerini yükseltin (ör. `0.4.2`).
2. İki değişiklik günlüğünde "Yayımlanmadı" / "Unreleased" başlığını `[0.4.2] - YYYY-AA-GG` yapın,
   üstüne boş bir "Yayımlanmadı" başlığı ekleyin ve sondaki karşılaştırma bağlantılarını güncelleyin.
3. Commit'leyip push'layın, CI'ın geçmesini bekleyin.
4. Etiketi oluşturup push'layın: `git tag v0.4.2 && git push origin v0.4.2`.

`Release` workflow'u testleri çalıştırır; macOS, Windows ve Linux paketlerini üretir; notları değişiklik
günlüklerinden alarak (`tools/release-notes.sh`) GitHub release'ini yayımlar. Etiket `appVersion` ile
eşleşmezse ya da değişiklik günlüğünde o sürümün bölümü yoksa workflow durur. Yayımlamadan yalnızca
paketleri denemek için Actions sekmesinden workflow'u elle çalıştırın; paketler çalışmanın
artifact'ları olarak iner.

## Katkıların lisansı

E-Signer AGPL-3.0 ile lisanslanmıştır. Katkı göndererek katkınızın AGPL-3.0 ile lisanslanmasını kabul
eder ve proje sahibine katkınızı ticari lisanslar dahil başka koşullarla da lisanslama hakkı tanırsınız.
Yalnızca katkıda bulunma hakkınız olan çalışmaları gönderin.
