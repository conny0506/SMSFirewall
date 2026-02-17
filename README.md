# SMSFirewall

SMSFirewall, Kotlin ile gelistirilmis bir Android SMS uygulamasidir. Uygulama, cihazda varsayilan SMS uygulamasi olarak calisacak sekilde tasarlanmistir ve gelen mesajlari filtreleyerek spam/istenmeyen icerikleri ayirir.

## Ozellikler

- Varsayilan SMS uygulamasi olarak calisabilme
- Ana mesaj kutusu, spam kutusu ve cop kutusu ekranlari
- Anahtar kelime tabanli mesaj filtreleme
- Guvenilir numara (trusted numbers) yonetimi
- Room veritabani ile yerel veri saklama
- Jetpack Compose tabanli arayuz bilesenleri

## Teknolojiler

- Kotlin
- Android Studio
- Jetpack Compose
- Room
- Android BroadcastReceiver / Service yapilari

## Gereksinimler

- Android Studio (guncel surum)
- JDK 11
- Android SDK (proje `compileSdk = 36`, `targetSdk = 36`, `minSdk = 24`)

## Kurulum ve Calistirma

1. Projeyi Android Studio ile acin.
2. Gradle senkronizasyonunun tamamlanmasini bekleyin.
3. Bir emulator veya fiziksel cihaz secin.
4. Uygulamayi calistirin.
5. Ilk acilista gerekli izinleri verin ve istenirse uygulamayi varsayilan SMS uygulamasi olarak atayin.

## Izinler

`AndroidManifest.xml` icerisinde temel olarak su izinler kullanilir:

- `READ_SMS`
- `SEND_SMS`
- `RECEIVE_SMS`
- `RECEIVE_MMS`
- `WRITE_SMS`
- `POST_NOTIFICATIONS`

## Proje Yapisi (Kisa)

- `app/src/main/java/com/example/smsfirewall`: Uygulama kaynak kodlari
- `app/src/main/res`: UI kaynaklari (tema, drawable, string vb.)
- `app/src/main/AndroidManifest.xml`: Manifest ve component tanimlari
- `app/build.gradle.kts`: Modul bagimliliklari ve Android ayarlari

## Notlar

- Uygulamanin SMS alma/gonderme davranisi Android surumune ve cihaz ureticisine gore farklilik gosterebilir.
- Uretim ortami icin filtreleme mantigini ve anahtar kelime listesini ihtiyaca gore gelistirmeniz onerilir.
