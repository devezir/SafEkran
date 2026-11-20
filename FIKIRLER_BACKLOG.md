# Saf Ekran — Fikirler ve Gelecek Geliştirme Havuzu (Backlog)

Bu doküman, Saf Ekran projesinin minimalist, ultra-hafif (~110 KB) ve pil/RAM dostu yapısını korurken gelecekte değerlendirilmek üzere kaydedilen mimari ve işlevsel fikirleri içerir.

---

## 📌 Şimdilik Ertelenen Özel Kutu (Tile) Fikirleri

### 1. 🔍 Hızlı Uygulama Arama Kutusu (`TileType.SEARCH`)
* **Konsept:** Kutu üzerinde büyüteç simgesi ve "Uygulama Ara..." metni.
* **İşlev:** Tıklandığında ekranın üstünde ince bir arama çubuğu ve klavye açılır. Onlarca uygulama arasında 1-2 harf tuşlayarak (örn: "you" -> YouTube) anında uygulamaya geçiş sağlar.
* **Durum:** Kullanıcı talebiyle şimdilik ertelendi.

### 2. 🌤️ Minimalist Hava Durumu Kutusu (`TileType.WEATHER`)
* **Konsept:** Windows Phone Live Tile ruhunu taşıyan, anlık sıcaklık ve hava durumu simgesi (`22°C ☀️ Güneşli`).
* **İşlev:** Tıklandığında sistemin veya cihazdaki hava durumu uygulamasını açar.
* **Durum:** Kullanıcı talebiyle şimdilik ertelendi.

### 3. 🔊 Hızlı Ses / Sessiz Profil Tuşu (`TileType.VOLUME_TOGGLE`)
* **Konsept:** Tek dokunuşla cihaz sesini "Normal / Titreşim / Sessiz" profilleri arasında döngüye sokan veya TV ses seviyesi ayarını açan kutu.
* **Durum:** Kullanıcı talebiyle şimdilik ertelendi.

### 4. ⚡ Güç Menüsü / Kapatma / Uyku Kutusu (`TileType.POWER`)
* **Konsept:** Tek dokunuşla cihazı kapatma, yeniden başlatma veya uyku moduna alma.
* **Kısıt / Analiz:** Standart Android mimarisinde doğrudan cihazı kapatma (`shutdown`) veya yeniden başlatma (`reboot`) işletim sistemi tarafından `android.permission.SHUTDOWN` (yalnızca sistem imzalı uygulamalara verilen izin) veya **Root (su)** yetkisi gerektirir. Root yetkisi olmadan yalnızca Erişilebilirlik Hizmeti (`AccessibilityService.GLOBAL_ACTION_POWER_DIALOG`) üzerinden sistemin kendi güç diyaloğu tetiklenebilir.
* **Durum:** Root gereksinimi ve kullanıcı tercihi doğrultusunda şimdilik ertelendi.

---

## 📌 Şimdilik Ertelenen Başlatıcı (Launcher) Özellikleri

### 1. 🎨 Özel Vurgu Rengi (Accent Color)
* **Konsept:** Windows Phone 8 tarzı kullanıcı tarafından seçilebilen genel vurgu rengi (Zümrüt Yeşili, Kiremit Kırmızısı, Kobalt Mavisi, Kehribar Sarısı).
* **Kapsam:** Odak çerçevesi, Tarih ve Saat gibi kutuların renk detayları bu vurgu rengine bürünür.
* **Durum:** Kullanıcı talebiyle şimdilik ertelendi.

### 2. ✏️ Kutu İsmini Değiştirme (Tile Rename)
* **Konsept:** Kutuya uzun basıldığında "İsmi Düzenle" menüsü ile çok uzun uygulama isimlerini ("AZ Screen Recorder Video Düzenleyici") kullanıcı isteğine göre kısaltma ("Kayıt" veya "Kamera").
* **Durum:** Kullanıcı talebiyle şimdilik ertelendi.

### 3. 📑 Windows Phone Tarzı "A-Z Alfabetik Liste Çekmecesi" (App Drawer)
* **Konsept:** Ana ekranı sola kaydırınca veya kumandadan Sağ tuşa basınca, yüklü tüm uygulamaların A'dan Z'ye harf gruplarıyla listelendiği minimalist dikey liste.
* **Durum:** Kullanıcı talebiyle şimdilik ertelendi.

### 4. 🗂️ Çoklu Çalışma Alanı / Profiller (Sayfalar)
* **Konsept:** "Ana Ekran", "Oyunlar", "Sinema/TV" gibi bağımsız kutu dizilimleri arasında kumandanın Sayfa tuşlarıyla geçiş.
* **Durum:** Kullanıcı talebiyle şimdilik ertelendi.

---

## 📌 Tartışılan Konu: Son Uygulamalar (Recents) Arayüzü
* **Analiz:** Sistem düzeyindeki Recents tuşunu ele geçirmek yerine, launcher içinde son açılan 5 uygulamanın yatay şeritte veya özel bir panelde gösterilmesi mimarisi.
* **Gelişme:** Kullanıcı ile değerlendirme aşamasında.
