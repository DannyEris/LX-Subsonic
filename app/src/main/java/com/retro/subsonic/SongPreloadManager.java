package com.retro.subsonic;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

public class SongPreloadManager {
    private static SongPreloadManager instance;
    private final ExecutorService preloadExecutor = Executors.newSingleThreadExecutor();
    private volatile String currentPreloadingSongId = "";

    public static synchronized SongPreloadManager getInstance() {
        if (instance == null) {
            instance = new SongPreloadManager();
        }
        return instance;
    }

    private SongPreloadManager() {}

    public synchronized void preload(final Context context, final String songId, final String streamUrl) {
        if (context == null || songId == null || songId.length() == 0 || streamUrl == null || streamUrl.length() == 0) {
            return;
        }
        // 本地音频文件直接跳过
        if (songId.startsWith("local_file:") || streamUrl.startsWith("file://")) {
            return;
        }
        // 若已存在且为有效文件，无需重复下载
        if (CacheManager.isSongCached(context, songId)) {
            return;
        }
        if (songId.equals(currentPreloadingSongId)) {
            return;
        }

        currentPreloadingSongId = songId;
        preloadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                TLSSocketFactory.install();
                File targetFile = CacheManager.getSongFile(context, songId);
                File tmpFile = CacheManager.getTempFile(context, songId);

                HttpURLConnection conn = null;
                InputStream is = null;
                FileOutputStream fos = null;
                try {
                    URL url = new URL(streamUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36");

                    // 补全防盗链头，防止部分音乐平台直链返回 403 导致预下载失败
                    if (streamUrl.contains("163.com") || streamUrl.contains("126.net")) {
                        conn.setRequestProperty("Referer", "https://music.163.com/");
                    } else if (streamUrl.contains("qq.com") || streamUrl.contains("gtimg.cn")) {
                        conn.setRequestProperty("Referer", "https://y.qq.com/");
                    } else if (streamUrl.contains("kugou.com")) {
                        conn.setRequestProperty("Referer", "http://www.kugou.com/");
                    } else if (streamUrl.contains("kuwo.cn")) {
                        conn.setRequestProperty("Referer", "http://www.kuwo.cn/");
                    } else if (streamUrl.contains("migu.cn")) {
                        conn.setRequestProperty("Referer", "https://music.migu.cn/");
                    }

                    conn.setConnectTimeout(12000);
                    conn.setReadTimeout(12000);
                    if (conn instanceof HttpsURLConnection) {
                        ((HttpsURLConnection) conn).setSSLSocketFactory(new TLSSocketFactory());
                    }
                    conn.connect();
                    int code = conn.getResponseCode();
                    if (code == 200 || code == 206) {
                        is = conn.getInputStream();
                        fos = new FileOutputStream(tmpFile);
                        byte[] buf = new byte[16384];
                        int read;
                        while ((read = is.read(buf)) != -1) {
                            if (!songId.equals(currentPreloadingSongId)) {
                                fos.close();
                                tmpFile.delete();
                                return;
                            }
                            fos.write(buf, 0, read);
                        }
                        fos.flush();
                        fos.close();
                        fos = null;

                        if (CacheManager.isValidAudioFile(tmpFile)) {
                            if (targetFile.exists()) {
                                targetFile.delete();
                            }
                            if (tmpFile.renameTo(targetFile)) {
                                targetFile.setLastModified(System.currentTimeMillis());
                            }
                        }
                    }
                } catch (Throwable ignored) {
                    if (tmpFile.exists()) {
                        tmpFile.delete();
                    }
                } finally {
                    try { if (fos != null) fos.close(); } catch (Throwable ignored) {}
                    try { if (is != null) is.close(); } catch (Throwable ignored) {}
                    if (conn != null) conn.disconnect();
                    if (songId.equals(currentPreloadingSongId)) {
                        currentPreloadingSongId = "";
                    }
                }
            }
        });
    }

    public synchronized void cancel() {
        currentPreloadingSongId = "";
    }
}
