package com.safekran.launcher.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Saf Ekran - Merkezi Dosya ve Akış Giriş/Çıkış (I/O) Yardımcısı.
 */
public final class IoUtils {

    private IoUtils() {}

    /**
     * Herhangi bir InputStream nesnesini UTF-8 formatında String'e dönüştürür ve akışı kapatır.
     */
    public static String readStreamToString(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } finally {
            try { reader.close(); } catch (Exception ignored) {}
            try { is.close(); } catch (Exception ignored) {}
        }
        return sb.toString();
    }

    /**
     * Belirtilen dosyayı okur ve metin içeriğini döner.
     */
    public static String readFileToString(File file) throws Exception {
        if (file == null || !file.exists()) return "";
        FileInputStream fis = new FileInputStream(file);
        return readStreamToString(fis);
    }

    /**
     * Belirtilen dosyaya metin içeriğini UTF-8 olarak yazar.
     */
    public static void writeStringToFile(File file, String content) throws Exception {
        if (file == null || content == null) return;
        FileOutputStream fos = new FileOutputStream(file);
        try {
            fos.write(content.getBytes("UTF-8"));
            fos.flush();
        } finally {
            try { fos.close(); } catch (Exception ignored) {}
        }
    }
}
