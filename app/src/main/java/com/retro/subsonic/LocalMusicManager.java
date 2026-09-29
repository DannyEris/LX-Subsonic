package com.retro.subsonic;

import java.io.File;
import java.util.ArrayList;

public class LocalMusicManager {

    public static ArrayList<MainActivity.DisplayEntry> scanFolder(String folderPath) {
        ArrayList<MainActivity.DisplayEntry> result = new ArrayList<MainActivity.DisplayEntry>();
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            return result;
        }
        scanDirRecursively(folder, result);
        return result;
    }

    private static void scanDirRecursively(File dir, ArrayList<MainActivity.DisplayEntry> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scanDirRecursively(f, result);
            } else if (f.isFile()) {
                String name = f.getName().toLowerCase();
                if (name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".wav") || name.endsWith(".m4a") || name.endsWith(".ogg")) {
                    String baseName = f.getName().substring(0, f.getName().lastIndexOf('.'));
                    String title = baseName;
                    String artist = "本地音乐";
                    if (baseName.contains(" - ")) {
                        String[] parts = baseName.split(" - ", 2);
                        artist = parts[0].trim();
                        title = parts[1].trim();
                    }
                    String quality = name.endsWith(".flac") || name.endsWith(".wav") ? "无损" : "320K";
                    MainActivity.DisplayEntry entry = new MainActivity.DisplayEntry(
                            "local_file:" + f.getAbsolutePath(),
                            title,
                            artist,
                            artist + " [" + quality + "]",
                            null,
                            quality,
                            true
                    );
                    result.add(entry);
                }
            }
        }
    }
}
