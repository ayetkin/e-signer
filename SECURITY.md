# Güvenlik politikası

[English](SECURITY.en.md)

E-Signer akıllı kart PIN'leriyle çalışır ve hukuki geçerliliği olan imzalar üretir; bu yüzden güvenlik
bildirimleri ciddiye alınır.

## Güvenlik açığı bildirme

Lütfen herkese açık bir kayıt açmayın. GitHub'ın gizli güvenlik açığı bildirimini kullanın (deponun
Security sekmesinde "Report a vulnerability") ve şunları ekleyin:

- neyin etkilendiği ve nasıl istismar edilebileceği,
- yeniden üretmek için adımlar ya da dosyalar,
- denediğiniz sürüm ya da commit.

Bir hafta içinde yanıt almanız beklenir. Düzeltme yayımlanana kadar ayrıntıları gizli tutun.

## Tasarım kuralları

Bu kurallar tasarımın parçasıdır; birini bozan değişiklik güvenlik açığı sayılır.

- PIN asla otomatik olarak yeniden denenmez; yanlış PIN kartın deneme hakkını tüketemez.
- PIN'ler ve zaman damgası parolaları yalnızca bellekte tutulur, loglanmaz, diske yazılmaz ve
  kullanımdan sonra sıfırlanır.
- Sertifikalardaki kişisel veriler (TC kimlik numarası, e-posta adresi) loglanmaz; TC kimlik
  numaraları maskeli gösterilir.
- Kullanıcı, kart kullanılmadan önce neyin imzalanacağını görür ve onaylar.
- İmzalı çıktı hiçbir zaman var olan bir dosyanın üzerine yazılmaz.

## Kapsam

Yalnızca varsayılan daldaki son commit desteklenir. Kart sürücüsündeki, kartın kendisindeki, EU DSS'teki
ya da diğer bağımlılıklardaki açıkları kendi geliştiricilerine bildirin; E-Signer bunları güvensiz bir
şekilde kullanıyorsa bize haber verin.
