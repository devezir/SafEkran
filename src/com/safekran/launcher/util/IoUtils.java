package com.safekran.launcher.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;

/**
 * Saf Ekran - Merkezi Dosya Giriş/Çıkış (I/O) Yardımcısı.
 */
public final class IoUtils {

    private IoUtils() {}

    public static String readFileToString(File file) throws Exception {
        if (file == null || !file.exists()) return "";
        FileInputStream fis = new FileInputStream(file);
        BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        fis.close();
        return sb.toString();
    }

    public static void writeStringToFile(File file, String content) throws Exception {
        if (file == null || content == null) return;
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(content.getBytes("UTF-8"));
        fos.flush();
        fos.close();
    }
}
