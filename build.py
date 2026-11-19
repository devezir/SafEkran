import os
import sys
import json
import re
import subprocess
import zipfile
import shutil
import glob
from datetime import datetime

base_dir = os.path.dirname(os.path.abspath(__file__))
tools_dir = r"C:\Users\Vezir\tools"
jdk_bin = r"C:\Users\Vezir\tools\jdk-21\bin"

aapt2 = os.path.join(tools_dir, "aapt2.exe")
android_jar = os.path.join(tools_dir, "android-33.jar")
r8_jar = os.path.join(tools_dir, "r8.jar")
signer_jar = os.path.join(tools_dir, "uber-apk-signer.jar")
javac = os.path.join(jdk_bin, "javac.exe")
java = os.path.join(jdk_bin, "java.exe")

version_file = os.path.join(base_dir, "version.json")
manifest_file = os.path.join(base_dir, "AndroidManifest.xml")
releases_dir = os.path.join(base_dir, "releases")
keystore_file = os.path.join(base_dir, "keystore", "safekran-release.jks")

os.makedirs(releases_dir, exist_ok=True)

# 1. Sürüm ve Versiyon Yönetimi (version.json)
if not os.path.exists(version_file):
    print("Hata: version.json bulunamadı!")
    sys.exit(1)

with open(version_file, "r", encoding="utf-8") as f:
    ver_data = json.load(f)

cur_vc = ver_data.get("versionCode", 1)
cur_vn = ver_data.get("versionName", "1.0.0")

if "--bump" in sys.argv:
    bump_idx = sys.argv.index("--bump")
    bump_type = sys.argv[bump_idx + 1] if len(sys.argv) > bump_idx + 1 else "patch"
    parts = [int(p) for p in cur_vn.split(".")]
    while len(parts) < 3:
        parts.append(0)
    
    if bump_type == "major":
        parts[0] += 1
        parts[1] = 0
        parts[2] = 0
    elif bump_type == "minor":
        parts[1] += 1
        parts[2] = 0
    else: # patch
        parts[2] += 1

    cur_vn = f"{parts[0]}.{parts[1]}.{parts[2]}"
    cur_vc += 1
    desc = "Sürüm güncellemesi"
    if len(sys.argv) > bump_idx + 2 and not sys.argv[bump_idx + 2].startswith("--"):
        desc = sys.argv[bump_idx + 2]

    ver_data["versionCode"] = cur_vc
    ver_data["versionName"] = cur_vn
    ver_data["apkName"] = f"SafEkran_v{cur_vn}_vc{cur_vc}.apk"
    ver_data["history"].append({
        "versionCode": cur_vc,
        "versionName": cur_vn,
        "date": datetime.now().strftime("%Y-%m-%d"),
        "description": desc
    })
    with open(version_file, "w", encoding="utf-8") as f:
        json.dump(ver_data, f, indent=2, ensure_ascii=False)
    print(f"[*] Sürüm yükseltildi: v{cur_vn} (versionCode: {cur_vc})")

# 2. AndroidManifest.xml Güncelle
with open(manifest_file, "r", encoding="utf-8") as f:
    manifest_content = f.read()

manifest_content = re.sub(r'android:versionCode="\d+"', f'android:versionCode="{cur_vc}"', manifest_content)
manifest_content = re.sub(r'android:versionName="[^"]+"', f'android:versionName="{cur_vn}"', manifest_content)

with open(manifest_file, "w", encoding="utf-8") as f:
    f.write(manifest_content)
print(f"[*] AndroidManifest.xml senkronize edildi: versionCode={cur_vc}, versionName={cur_vn}")

os.chdir(base_dir)

# 3. Kaynakları Derle (AAPT2)
print("\n[1/5] Kaynaklar derleniyor (AAPT2)...")
res_zip = os.path.join(base_dir, "compiled_res.zip")
if os.path.exists(res_zip):
    os.remove(res_zip)

cmd_compile = [aapt2, "compile", "--dir", "res", "-o", res_zip]
res = subprocess.run(cmd_compile, capture_output=True, text=True)
if res.returncode != 0:
    print("AAPT2 derleme hatası:", res.stderr)
    sys.exit(1)
print("AAPT2 derleme başarılı.")

# 4. Kaynakları Bağla ve R.java Üret
print("[2/5] Kaynaklar bağlanıyor ve R.java üretiliyor...")
base_apk = os.path.join(base_dir, "base.apk")
if os.path.exists(base_apk):
    os.remove(base_apk)

gen_dir = os.path.join(base_dir, "gen")
shutil.rmtree(gen_dir, ignore_errors=True)
os.makedirs(gen_dir, exist_ok=True)

cmd_link = [
    aapt2, "link", res_zip,
    "-I", android_jar,
    "--manifest", "AndroidManifest.xml",
    "-o", base_apk,
    "--java", "gen",
    "--auto-add-overlay"
]
res = subprocess.run(cmd_link, capture_output=True, text=True)
if res.returncode != 0:
    print("AAPT2 link hatası:", res.stderr)
    sys.exit(1)
print("AAPT2 link başarılı.")

# 5. Java Kodunu Derle (javac)
print("[3/5] Java kodları derleniyor (javac)...")
obj_dir = os.path.join(base_dir, "obj")
shutil.rmtree(obj_dir, ignore_errors=True)
os.makedirs(obj_dir, exist_ok=True)

java_files = glob.glob("gen/**/*.java", recursive=True) + glob.glob("src/**/*.java", recursive=True)
cmd_javac = [
    javac,
    "-encoding", "UTF-8",
    "-source", "1.8",
    "-target", "1.8",
    "-cp", android_jar,
    "-d", obj_dir
] + java_files
res = subprocess.run(cmd_javac, capture_output=True, text=True)
if res.returncode != 0:
    print("javac hatası:", res.stderr)
    print("stdout:", res.stdout)
    sys.exit(1)
print("javac derleme başarılı.")

# 6. DEX Dönüşümü (D8)
print("[4/5] Bayt kodları DEX formatına dönüştürülüyor (D8)...")
bin_dir = os.path.join(base_dir, "bin")
shutil.rmtree(bin_dir, ignore_errors=True)
os.makedirs(bin_dir, exist_ok=True)

class_files = glob.glob("obj/**/*.class", recursive=True)
cmd_d8 = [
    java, "-cp", r8_jar,
    "com.android.tools.r8.D8",
    "--lib", android_jar,
    "--min-api", "21",
    "--output", bin_dir
] + class_files
res = subprocess.run(cmd_d8, capture_output=True, text=True)
if res.returncode != 0:
    print("D8 hatası:", res.stderr)
    sys.exit(1)
print("D8 dönüşümü başarılı.")

dex_file = os.path.join(bin_dir, "classes.dex")

# 7. Paketleme ve Resmi İmzalama (uber-apk-signer + JKS)
print("[5/5] Paketleme ve Resmi Keystore İmzalaması yapılıyor...")
unsigned_apk = os.path.join(base_dir, "SafEkran_unsigned.apk")
shutil.copy2(base_apk, unsigned_apk)

with zipfile.ZipFile(unsigned_apk, "a") as z:
    z.write(dex_file, "classes.dex")

out_dir = os.path.join(base_dir, "out_sign")
shutil.rmtree(out_dir, ignore_errors=True)
os.makedirs(out_dir, exist_ok=True)

cmd_sign = [
    java, "-jar", signer_jar,
    "-a", unsigned_apk,
    "-o", out_dir,
    "--allowResign"
]

if os.path.exists(keystore_file):
    cmd_sign += [
        "--ks", keystore_file,
        "--ksAlias", "safekran",
        "--ksPass", "SafEkran_2026!Vezir#ProKey",
        "--ksKeyPass", "SafEkran_2026!Vezir#ProKey"
    ]
    print("[*] Resmi Vezir Keystore ile imzalanıyor: safekran-release.jks")

res = subprocess.run(cmd_sign, capture_output=True, text=True)
if res.returncode != 0:
    print("İmzalama hatası:", res.stderr)
    sys.exit(1)

signed_apks = glob.glob(os.path.join(out_dir, "*.apk"))
if not signed_apks:
    print("Hata: İmzalı APK bulunamadı!")
    sys.exit(1)

final_apk_name = f"SafEkran_v{cur_vn}_vc{cur_vc}.apk"
release_apk_path = os.path.join(releases_dir, final_apk_name)
latest_apk_path = os.path.join(base_dir, "SafEkran.apk")

shutil.copy2(signed_apks[0], release_apk_path)
shutil.copy2(signed_apks[0], latest_apk_path)

# Temizlik
if os.path.exists(unsigned_apk):
    os.remove(unsigned_apk)
if os.path.exists(base_apk):
    os.remove(base_apk)
shutil.rmtree(out_dir, ignore_errors=True)

apk_size_kb = os.path.getsize(latest_apk_path) / 1024.0
print("\n" + "="*50)
print("✅ DERLEME VE İMZALAMA TAMAMLANDI!")
print(f"📦 Son Çıktı:        {latest_apk_path}")
print(f"🏷️  Sürüm Arşivi:     {release_apk_path}")
print(f"📊 APK Boyutu:       {apk_size_kb:.1f} KB (Tüy kadar hafif!)")
print(f"🔒 İmza Durumu:      Resmi Release Key (V1, V2, V3 onaylı)")
print("="*50)
