package com.retro.subsonic;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;

public class LocalStreamProxy {
    public interface ProxyListener {
        void onProgress(int percent);
        void onCached(File cachedFile);
        void onError(String reason);
    }

    private Context context;
    private String songId;
    private String originalStreamUrl;
    private ProxyListener listener;
    private ServerSocket serverSocket;
    private int proxyPort = 0;
    private Thread serverThread;
    private Thread downloadThread;
    private volatile boolean isStopped = false;
    private volatile boolean downloadFinished = false;
    private volatile boolean downloadFailed = false;
    private volatile boolean isHeaderReady = false;
    private volatile String failReason = "";
    private volatile long downloadedBytes = 0;
    private volatile long totalBytes = -1;
    private volatile String audioContentType = "audio/mpeg";
    private File tmpFile;
    private File targetFile;

    private int configTimeoutSec = 30;
    private int configRetryCount = 3;

    public LocalStreamProxy(Context context, String songId, String originalStreamUrl, ProxyListener listener) {
        this.context = context;
        this.songId = songId;
        this.originalStreamUrl = originalStreamUrl;
        this.listener = listener;
        this.tmpFile = CacheManager.getTempFile(context, songId);
        this.targetFile = CacheManager.getSongFile(context, songId);
        loadConfig();
    }

    private void loadConfig() {
        try {
            SharedPreferences sp = context.getSharedPreferences("subsonic_cfg", Context.MODE_PRIVATE);
            String timeoutStr = sp.getString("timeout_sec", "30");
            String retryStr = sp.getString("retry_count", "3");
            configTimeoutSec = Math.max(5, Integer.parseInt(timeoutStr.trim()));
            configRetryCount = Math.max(0, Integer.parseInt(retryStr.trim()));
        } catch (Exception ignored) {
            configTimeoutSec = 30;
            configRetryCount = 3;
        }
    }

    public synchronized String start() throws Exception {
        if (tmpFile.exists()) {
            tmpFile.delete();
        }
        serverSocket = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
        proxyPort = serverSocket.getLocalPort();
        startDownloader();
        startServer();
        return "http://127.0.0.1:" + proxyPort + "/stream";
    }

    private void startDownloader() {
        downloadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                TLSSocketFactory.install();
                int totalAttempts = configRetryCount + 1;
                String lastError = "";
                for (int attempt = 1; attempt <= totalAttempts; attempt++) {
                    if (isStopped) return;
                    if (tmpFile.exists()) {
                        tmpFile.delete();
                    }
                    downloadedBytes = 0;
                    isHeaderReady = false;
                    lastError = runDownloadPipeline(originalStreamUrl, 0);
                    if (isStopped) return;
                    if ("OK".equals(lastError) && CacheManager.isValidAudioFile(tmpFile)) {
                        if (targetFile.exists()) {
                            targetFile.delete();
                        }
                        if (tmpFile.renameTo(targetFile)) {
                            targetFile.setLastModified(System.currentTimeMillis());
                            downloadFinished = true;
                            SharedPreferences sp = context.getSharedPreferences("subsonic_cfg", Context.MODE_PRIVATE);
                            int maxMb = 500;
                            try {
                                maxMb = Integer.parseInt(sp.getString("cache_size_mb", "500"));
                            } catch (Exception ignored) {}
                            CacheManager.trimCache(context, maxMb * 1024L * 1024L, songId);
                            if (listener != null) {
                                listener.onProgress(100);
                                listener.onCached(targetFile);
                            }
                            return;
                        }
                    }
                    if (attempt < totalAttempts && !isStopped) {
                        try {
                            Thread.sleep(800);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }
                downloadFailed = true;
                failReason = lastError != null ? lastError : "下载失败";
                if (listener != null && !isStopped) {
                    listener.onError(failReason);
                }
            }
        });
        downloadThread.start();
    }

    private String runDownloadPipeline(String targetUrl, int depth) {
        if (depth > 6 || isStopped) return "重定向过多";
        HttpURLConnection conn = null;
        InputStream is = null;
        FileOutputStream fos = null;
        try {
            URL url = new URL(targetUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; U; Android 4.2.2; zh-cn) AppleWebKit/534.30");
            int timeoutMs = configTimeoutSec * 1000;
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            if (conn instanceof HttpsURLConnection) {
                HttpsURLConnection httpsConn = (HttpsURLConnection) conn;
                httpsConn.setSSLSocketFactory(new TLSSocketFactory());
            }
            conn.connect();
            int code = conn.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location != null && location.length() > 0) {
                    URL redirectUrl = new URL(url, location);
                    return runDownloadPipeline(redirectUrl.toString(), depth + 1);
                }
                return "重定向地址为空 (HTTP " + code + ")";
            }
            if (code != 200 && code != 206) {
                return "HTTP错误 (HTTP " + code + ")";
            }
            totalBytes = conn.getContentLength();
            String cType = conn.getContentType();
            if (cType != null && cType.contains("audio/")) {
                audioContentType = cType;
            }
            is = conn.getInputStream();
            byte[] previewBuf = new byte[2048];
            int previewRead = is.read(previewBuf);
            if (previewRead <= 0) return "流为空";
            String previewStr = new String(previewBuf, 0, previewRead, "UTF-8").trim();
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
                    JSONObject sub = root.optJSONObject("subsonic-response");
                    if (sub != null && "failed".equals(sub.optString("status"))) {
                        JSONObject err = sub.optJSONObject("error");
                        return "Subsonic错误: " + (err != null ? err.optString("message") : "");
                    }
                    String directUrl = findAudioUrlInJson(root);
                    if (directUrl != null) {
                        conn.disconnect();
                        return runDownloadPipeline(directUrl, depth + 1);
                    }
                    return "JSON未包含播放链接";
                } catch (Exception e) {
                    return "JSON解析异常";
                }
            }
            if (previewStr.startsWith("<?xml") || previewStr.contains("<subsonic-response")) {
                Matcher m = Pattern.compile("message=\"([^\"]+)\"").matcher(previewStr);
                if (m.find()) return "Subsonic错误: " + m.group(1);
                return "返回了XML错误";
            }
            fos = new FileOutputStream(tmpFile);
            fos.write(previewBuf, 0, previewRead);
            fos.flush();
            downloadedBytes = previewRead;
            isHeaderReady = true;
            byte[] buf = new byte[16384];
            int r;
            long lastBroadcastTime = 0;
            while ((r = is.read(buf)) != -1) {
                if (isStopped) return "";
                fos.write(buf, 0, r);
                fos.flush();
                downloadedBytes += r;
                if (totalBytes > 0) {
                    long now = System.currentTimeMillis();
                    if (now - lastBroadcastTime > 400) {
                        lastBroadcastTime = now;
                        int percent = (int) ((downloadedBytes * 100) / totalBytes);
                        if (percent > 99) percent = 99; // 未通过文件头校验前上限锁在 99%
                        if (listener != null) listener.onProgress(percent);
                    }
                }
            }
            fos.flush();
            return "OK";
        } catch (Exception e) {
            return "下载异常: " + e.getMessage();
        } finally {
            try { if (fos != null) fos.close(); } catch (Exception ignored) {}
            try { if (is != null) is.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    private void startServer() {
        serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!isStopped) {
                    try {
                        Socket client = serverSocket.accept();
                        handleClient(client);
                    } catch (Exception e) {
                        break;
                    }
                }
            }
        });
        serverThread.start();
    }

    private void handleClient(final Socket client) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                RandomAccessFile raf = null;
                OutputStream os = null;
                try {
                    client.setSoTimeout(configTimeoutSec * 1000);
                    InputStream cis = client.getInputStream();
                    os = client.getOutputStream();
                    byte[] reqBuf = new byte[2048];
                    int reqLen = cis.read(reqBuf);
                    if (reqLen <= 0) return;
                    String reqStr = new String(reqBuf, 0, reqLen);
                    long rangeStart = 0;
                    Matcher m = Pattern.compile("Range:\\s*bytes=(\\d+)-").matcher(reqStr);
                    if (m.find()) {
                        rangeStart = Long.parseLong(m.group(1));
                    }
                    long waitTimeoutMs = configTimeoutSec * 1000L;
                    long waitStart = System.currentTimeMillis();
                    while (!isHeaderReady && !downloadFinished && !downloadFailed && !isStopped) {
                        if (System.currentTimeMillis() - waitStart > waitTimeoutMs) break;
                        Thread.sleep(20);
                    }
                    if (downloadFailed || isStopped) {
                        os.write("HTTP/1.1 500 Internal Error\r\n\r\n".getBytes());
                        os.flush();
                        return;
                    }
                    StringBuilder resp = new StringBuilder();
                    if (rangeStart > 0 && totalBytes > 0) {
                        resp.append("HTTP/1.1 206 Partial Content\r\n");
                        resp.append("Content-Range: bytes ").append(rangeStart).append("-").append(totalBytes - 1).append("/").append(totalBytes).append("\r\n");
                        resp.append("Content-Length: ").append(totalBytes - rangeStart).append("\r\n");
                    } else {
                        resp.append("HTTP/1.1 200 OK\r\n");
                        if (totalBytes > 0) {
                            resp.append("Content-Length: ").append(totalBytes).append("\r\n");
                        }
                    }
                    resp.append("Content-Type: ").append(audioContentType).append("\r\n");
                    resp.append("Accept-Ranges: bytes\r\n");
                    resp.append("Connection: close\r\n\r\n");
                    os.write(resp.toString().getBytes());
                    os.flush();
                    File readTarget = targetFile.exists() ? targetFile : tmpFile;
                    raf = new RandomAccessFile(readTarget, "r");
                    raf.seek(rangeStart);
                    byte[] sendBuf = new byte[8192];
                    long readPos = rangeStart;
                    while (!isStopped) {
                        long available = downloadedBytes - readPos;
                        if (available > 0) {
                            int toRead = (int) Math.min(sendBuf.length, available);
                            int actualRead = raf.read(sendBuf, 0, toRead);
                            if (actualRead > 0) {
                                os.write(sendBuf, 0, actualRead);
                                os.flush();
                                readPos += actualRead;
                            }
                            if (totalBytes > 0 && readPos >= totalBytes) {
                                break;
                            }
                        } else {
                            if (downloadFinished || downloadFailed) {
                                break;
                            }
                            Thread.sleep(20);
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    try { if (raf != null) raf.close(); } catch (Exception ignored) {}
                    try { if (os != null) os.close(); } catch (Exception ignored) {}
                    try { client.close(); } catch (Exception ignored) {}
                }
            }
        }).start();
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

    public synchronized void stop() {
        isStopped = true;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        if (downloadThread != null) downloadThread.interrupt();
        if (serverThread != null) serverThread.interrupt();
        if (!downloadFinished && tmpFile.exists()) {
            tmpFile.delete();
        }
    }
}
