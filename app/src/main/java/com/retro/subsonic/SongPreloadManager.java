package com.retro.subsonic;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;

public class SongPreloadManager {
    private static SongPreloadManager instance;
    private final ExecutorService preloadExecutor = Executors.newSingleThreadExecutor();
    private volatile Future<?> activeTask = null;
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
        // 若已下载且为有效文件，无需重复下载
        if (CacheManager.isSongCached(context, songId)) {
            return;
        }
        if (songId.equals(currentPreloadingSongId)) {
            return;
        }

        cancel();
        currentPreloadingSongId = songId;

        activeTask = preloadExecutor.submit(new Runnable() {
            @Override
            public void run() {
                TLSSocketFactory.install();
                File targetFile = CacheManager.getSongFile(context, songId);
                File tmpFile = CacheManager.getTempFile(context, songId);

                if (tmpFile.exists()) {
                    tmpFile.delete();
                }

                try {
                    String result = runDownloadPipeline(streamUrl, 0, tmpFile, songId);
                    if ("OK".equals(result) && CacheManager.isValidAudioFile(tmpFile)) {
                        if (targetFile.exists()) {
                            targetFile.delete();
                        }
                        if (tmpFile.renameTo(targetFile)) {
                            targetFile.setLastModified(System.currentTimeMillis());
                        }
                    } else {
                        if (tmpFile.exists()) {
                            tmpFile.delete();
                        }
                    }
                } catch (Throwable ignored) {
                    if (tmpFile.exists()) {
                        tmpFile.delete();
                    }
                } finally {
                    if (songId.equals(currentPreloadingSongId)) {
                        currentPreloadingSongId = "";
                    }
                }
            }
        });
    }

    // 递归解析网络重定向与 API 返回的 JSON 直链
    private String runDownloadPipeline(String targetUrl, int depth, File tmpFile, String songId) {
        if (depth > 6 || Thread.currentThread().isInterrupted() || !songId.equals(currentPreloadingSongId)) {
            return "CANCELLED";
        }

        HttpURLConnection conn = null;
        InputStream is = null;
        FileOutputStream fos = null;
        try {
            URL url = new URL(targetUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36");

            // 补齐平台防盗链 Referer
            if (targetUrl.contains("163.com") || targetUrl.contains("126.net")) {
                conn.setRequestProperty("Referer", "https://music.163.com/");
            } else if (targetUrl.contains("qq.com") || targetUrl.contains("gtimg.cn")) {
                conn.setRequestProperty("Referer", "https://y.qq.com/");
            } else if (targetUrl.contains("kugou.com")) {
                conn.setRequestProperty("Referer", "http://www.kugou.com/");
            } else if (targetUrl.contains("kuwo.cn")) {
                conn.setRequestProperty("Referer", "http://www.kuwo.cn/");
            } else if (targetUrl.contains("migu.cn")) {
                conn.setRequestProperty("Referer", "https://music.migu.cn/");
            }

            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            if (conn instanceof HttpsURLConnection) {
                ((HttpsURLConnection) conn).setSSLSocketFactory(new TLSSocketFactory());
            }
            conn.connect();

            int code = conn.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location != null && location.length() > 0) {
                    URL redirectUrl = new URL(url, location);
                    return runDownloadPipeline(redirectUrl.toString(), depth + 1, tmpFile, songId);
                }
                return "REDIRECT_EMPTY";
            }

            if (code != 200 && code != 206) {
                return "HTTP_" + code;
            }

            is = conn.getInputStream();
            byte[] previewBuf = new byte[2048];
            int previewRead = is.read(previewBuf);
            if (previewRead <= 0) return "EMPTY_STREAM";

            String previewStr = new String(previewBuf, 0, previewRead, "UTF-8").trim();

            // 核心修复点：穿透 Subsonic/LX API 返回的 JSON 报文，提取真实音频直链进行二次拉取
            if (previewStr.startsWith("{") || previewStr.startsWith("[")) {
                StringBuilder sb = new StringBuilder(previewStr);
                byte[] temp = new byte[4096];
                int l;
                while ((l = is.read(temp)) != -1) {
                    sb.append(new String(temp, 0, l, "UTF-8"));
                }
                String jsonText = sb.toString();
                try {
                    JSONObject root = new JSONObject(jsonText);
                    String directUrl = findAudioUrlInJson(root);
                    if (directUrl != null) {
                        conn.disconnect();
                        return runDownloadPipeline(directUrl, depth + 1, tmpFile, songId);
                    }
                    return "NO_URL_IN_JSON";
                } catch (Exception e) {
                    return "JSON_PARSE_ERROR";
                }
            }

            if (previewStr.startsWith("<?xml") || previewStr.contains("<subsonic-response")) {
                return "XML_ERROR";
            }

            // 确认是真实音频流，写入临时文件
            fos = new FileOutputStream(tmpFile);
            fos.write(previewBuf, 0, previewRead);
            byte[] buf = new byte[16384];
            int r;
            while ((r = is.read(buf)) != -1) {
                if (Thread.currentThread().isInterrupted() || !songId.equals(currentPreloadingSongId)) {
                    return "CANCELLED";
                }
                fos.write(buf, 0, r);
            }
            fos.flush();
            return "OK";

        } catch (Throwable t) {
            return "ERROR_" + t.getMessage();
        } finally {
            try { if (fos != null) fos.close(); } catch (Exception ignored) {}
            try { if (is != null) is.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    private String findAudioUrlInJson(Object json) {
        if (json instanceof JSONObject) {
            JSONObject obj = (JSONObject) json;
            String[] targetKeys = new String[]{"url", "streamUrl", "playUrl", "link", "src", "audioUrl", "musicUrl", "data"};
            for (String k : targetKeys) {
                Object val = obj.opt(k);
                if (val instanceof String) {
                    String strVal = (String) val;
                    if (strVal.startsWith("http://") || strVal.startsWith("https://")) {
                        return strVal;
                    }
                }
            }
            Iterator<?> it = obj.keys();
            while (it.hasNext()) {
                String k = (String) it.next();
                String found = findAudioUrlInJson(obj.opt(k));
                if (found != null) return found;
            }
        } else if (json instanceof JSONArray) {
            JSONArray arr = (JSONArray) json;
            for (int i = 0; i < arr.length(); i++) {
                String found = findAudioUrlInJson(arr.opt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    public synchronized void cancel() {
        currentPreloadingSongId = "";
        if (activeTask != null) {
            activeTask.cancel(true);
            activeTask = null;
        }
    }
}
