# Saf Ekran (Pure Screen Launcher)

<p align="center">
  <img src="res/drawable/ic_launcher.png" width="128" height="128" alt="Saf Ekran Logo" />
</p>

<p align="center">
  <b>Android TV, TV Box ve Mobil Cihazlar için Tüy Kadar Hafif (~182 KB), Reklamsız, Saf Siyah (OLED) Başlatıcı</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/S%C3%BCr%C3%BCm-v2.25.0-00E5FF?style=flat-square" alt="Sürüm" />
  <img src="https://img.shields.io/badge/APK%20Boyutu-182.5%20KB-2E7D32?style=flat-square" alt="APK Boyutu" />
  <img src="https://img.shields.io/badge/Android-5.0%2B%20(API%2021%2B)-FF6F00?style=flat-square" alt="Android Sürümü" />
  <img src="https://img.shields.io/badge/Ba%C4%9F%C4%B1ml%C4%B1l%C4%B1k-SIFIR%20(Pure%20SDK)-blueviolet?style=flat-square" alt="Bağımlılık" />
  <img src="https://img.shields.io/badge/Geli%C5%9Ftirici-Vezir-success?style=flat-square" alt="Geliştirici" />
</p>

---

## 🌟 Neden Saf Ekran?

Günümüz Android TV ve mobil başlatıcıları (launcher) gigabaytlarca RAM tüketen, ağır reklamlar, öneri akışları ve arka plan servisleriyle cihazları yavaşlatan hantal yapılara dönüşmüştür. 

**Saf Ekran**, bu kalabalığa bir tepki olarak doğdu:
- **Sıfır Reklam, Sıfır Takipçi, Sıfır Harici Kütüphane:** Gradle veya Kotlin bağımlılığı içermez; saf Android SDK ile sıfırdan inşa edilmiştir.
- **Tüy Kadar Hafif:** Tüm başlatıcı yalnızca **~166 KB** boyutundadır ve anında açılır.
- **Saf Siyah (True OLED #000000):** OLED/AMOLED panellerde pikselleri tamamen kapatarak sıfır enerji harcar, gözü yormaz ve sonsuz kontrast sunar.
- **TV & Mobil Çift Ruhlu:** Hem Android TV kumandaları (D-Pad) hem de dokunmatik ekranlar için kusursuz ergonomiye sahiptir.

---

## ✨ Öne Çıkan Özellikler

### 📺 1. Android TV ve Kumanda (D-Pad) Ergonomisi
- **Canlı Cyan TV Odak Çerçevesi:** Kumanda ile gezinirken hangi kutuda olduğunuzu anında belli eden neon cyan (`#00E5FF`) odak çerçevesi ve yumuşak büyüme animasyonu (`scale`).
- **Hayalet Odak (Ghost Focus):** Boşluk kutucukları (Spacer) normalde saf siyah zeminle görünmezdir; kumanda ile üzerine gelindiğinde kesik çizgili neon çerçeve ile belirir ve kutu düzenlemeye olanak tanır.
- **Akıllı D-Pad Kaydırma:** Odaklanan kutu ekran sınırına yaklaştığında ekran yumuşakça (smooth scroll) otomatik hizalanır.

### 📐 2. 2D Hücre Matrisi & Esnek Kutu Geometrisi
- **🔍 Arayüz Ölçek Slider'ı (%85 – %250):** Akıllı gözlükler, projeksiyonlar, küçük TV'ler veya dev paneller için menü ve buton yazı boyutunu dilediğiniz gibi büyütüp küçültebilirsiniz.
- **Kutu Boyutu & Yükseklik Oranı:** Kutuları ister tam kare, ister TV dikdörtgeni (%75) yapın.
- **Kutular Arası Boşluk (Gutter):** 0 ile 5 dp arasında kutu aralıklarını ayarlayın.
- **Güvenli Alan (Overscan Koruması):** Kenarları kesen eski TV'ler veya kavisli ekranlar için 4 yönlü (Üst, Alt, Sol, Sağ) bağımsız güvenli alan sınırları.

### 📊 3. Gerçek Zamanlı Sistem Durum Kutuları
Harici bir widget yüklemeden sisteminizin durumunu ana ekrandan takip edin:
- **🔋 Pil Durumu:** Şarj yüzdesi, şarj durumu ve Android TV için AC güç algılama.
- **🧠 RAM Kullanımı:** Canlı kullanılan ve toplam bellek göstergesi (%85 üzeri uyarı rengi).
- **💾 Depolama:** Kalan boş dahili depolama alanı (GB).
- **📶 Ağ Durumu:** Wi-Fi sinyal seviyesi (%) ve yerel IP adresi veya Ethernet/Mobil bağlantı göstergesi.
- **🔵 Bluetooth:** Bağlı cihaz adı veya eşleşmiş cihaz sayısı.
- **🕒 Saat & 📅 Takvim:** Tek tıkla alarm veya takvim uygulamasına geçiş.

### 🏠 4. Evrensel Varsayılan Başlatıcı Motoru
- Android 5.0'dan Android 14+'e kadar tüm cihazlarda tek dokunuşla Saf Ekran'ı varsayılan başlatıcı yapma imkanı.
- **Xiaomi HyperOS & MIUI Özel Çözümü:** Xiaomi cihazların standart `RoleManager` akışlarını kısıtlamasına karşılık, kullanıcıyı doğrudan MIUI/HyperOS "Varsayılan Uygulamalar (Home)" ekranına yönlendirerek rehber bildirim gösterir.

### 🎮 5. Kumanda Renk Tuşları & Hızlı Aksiyonlar
TV kumandalarındaki renkli tuşlar (🔴 Kırmızı, 🟢 Yeşil, 🟡 Sarı, 🔵 Mavi) veya ekran üzerindeki özel renk kutuları ile:
- **🟢 Yeşil Tuş:** Gizlenen uygulamaları listeleme / açma.
- **🟡 Sarı Tuş:** Saf Siyah / Koyu Gri / Aydınlık tema geçişi.
- **🔵 Mavi Tuş:** Başlatıcı Ayarlarını açma.

### 🔄 6. Dahili Akıllı Güncelleme Motoru (OTA)
- GitHub Releases API üzerinden doğrudan güncelleme denetimi.
- **Pre-Flight İzin Kontrolü:** İndirme öncesi "Bilinmeyen Kaynaklar" iznini kontrol eder; izin verildikten sonra APK'yı tekrar indirmeden önbellekten kesintisiz kurar.

### 🔒 7. Gizlilik & Yedekleme
- **Uygulama Gizleme:** Ekranda görmek istemediğiniz uygulamaları gizleyebilir, yeşil tuş menüsünden erişebilirsiniz.
- **JSON Yedekleme / Geri Yükleme:** Ekran düzeninizi ve kutu yerleşiminizi tek tıkla JSON formatında yedekleyebilir veya başka bir cihaza aktarabilirsiniz.

---

## 📸 Ekran Düzeni ve Arayüz

```text
┌────────────────────────────────────────────────────────┐
│ [Saat: 14:30]  [23 Kasım Pzt]  [🔴] [🟢] [🟡] [🔵]      │
├────────────────────────────────────────────────────────┤
│ [ Pil: %92 ]   [ RAM: %42 ]   [ 48 GB Boş ] [ Wi-Fi ]  │
├────────────────────────────────────────────────────────┤
│ [ YouTube ]    [ Netflix ]    [ Kodi ]     [ Ayarlar ] │
│ [ Tarayıcı ]   [ Dosyalar ]   [ Spotify ]  [ ... ]     │
└────────────────────────────────────────────────────────┘
```

---

## 📦 Kurulum

1. En güncel APK dosyasını [GitHub Releases](https://github.com/devezir/SafEkran/releases) sayfasından indirin.
2. Cihazınıza yükleyin (gerekirse *Bilinmeyen Kaynaklara İzin Ver* seçeneğini açın).
3. Kumandadan `HOME` tuşuna basıp veya Başlatıcı Ayarları'ndaki **"Saf Ekran'ı Varsayılan Başlatıcı Yap"** butonuna dokunarak varsayılan başlatıcınız olarak belirleyin.

---

## 🛠️ Mimari ve Derleme (Build)

Saf Ekran, Gradle hantallığı olmadan **Python tabanlı özel bir build pipeline (`build.py`)** ile derlenir:

```bash
# Projeyi derle ve resmi release APK'sını üret:
python build.py

# Sürüm numarasını artırarak derle:
python build.py --bump patch "Hata düzeltmeleri"
# veya
python build.py --bump minor "Yeni özellikler"
```

### Proje Dizin Yapısı
```text
Saf Ekran/
├── src/com/safekran/launcher/
│   ├── MainActivity.java        # Ana launcher aktivitesi ve döngü yönetimi
│   ├── data/                    # Veri modelleri, uygulama tarayıcı ve JSON depolama
│   ├── model/                   # Kutu (Tile), Yapılandırma (WorkspaceConfig) modelleri
│   ├── ui/                      # TileView, WorkspaceView özel matris çizimleri
│   └── util/                    # UiUtils, IntentUtils, UpdateManager, ApkProvider
├── res/                         # Görseller, simgeler ve XML kaynakları
├── keystore/                    # Resmi release jks anahtarı
├── releases/                    # Sürüm bazlı arşivlenmiş APK dosyaları
├── build.py                     # SDK/AAPT2/D8/Signer derleme otomasyonu
└── version.json                 # Versiyon geçmişi ve yayın metadata'sı
```

---

## 📜 Lisans

Bu proje açık kaynaklıdır ve MIT lisansı altında korunmaktadır.

Geliştirici: **Vezir**  
GitHub: [@devezir](https://github.com/devezir)
