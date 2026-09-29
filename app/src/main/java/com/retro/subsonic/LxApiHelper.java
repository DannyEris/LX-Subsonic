package com.retro.subsonic;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import javax.net.ssl.HttpsURLConnection;

public class LxApiHelper {

    public static class PlaylistInfo {
        public String id;
        public String name;
        public String author;
        public String coverImg;
        public String playCount;
        public String source;

        public PlaylistInfo(String id, String name, String author, String coverImg, String playCount, String source) {
            this.id = id;
            this.name = name;
            this.author = author;
            this.coverImg = coverImg;
            this.playCount = playCount;
            this.source = source;
        }
    }

    // 1. 获取指定平台的热门搜索词
    public static ArrayList<String> fetchHotSearch(String platform) {
        ArrayList<String> list = new ArrayList<String>();
        try {
            if ("wy".equalsIgnoreCase(platform)) {
                // 网易云热搜
                String res = httpGet("https://music.163.com/api/search/hot/get");
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray hots = root.getJSONObject("result").getJSONArray("hots");
                    for (int i = 0; i < Math.min(hots.length(), 20); i++) {
                        list.add(hots.getJSONObject(i).getString("first"));
                    }
                }
            } else if ("kg".equalsIgnoreCase(platform)) {
                // 酷狗热搜
                String res = httpGet("http://mobilecdn.kugou.com/api/v3/search/hot");
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONObject("data").getJSONArray("info");
                    for (int i = 0; i < Math.min(arr.length(), 20); i++) {
                        list.add(arr.getJSONObject(i).getString("keyword"));
                    }
                }
            } else if ("kw".equalsIgnoreCase(platform)) {
                // 酷我热搜
                String res = httpGet("http://kuwo.cn/api/www/search/searchKey");
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONArray("data");
                    for (int i = 0; i < Math.min(arr.length(), 20); i++) {
                        String s = arr.getString(i);
                        String[] parts = s.split("\r\n|\\n");
                        list.add(parts[0]);
                    }
                }
            } else {
                // 企鹅/咪咕/聚合 兜底官方高频热词
                String res = httpGet("https://c.y.qq.com/splcloud/fcgi-bin/gethotkey.fcg?g_tk=5381&format=json");
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONObject("data").getJSONArray("hotkey");
                    for (int i = 0; i < Math.min(arr.length(), 20); i++) {
                        list.add(arr.getJSONObject(i).getString("k").trim());
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (list.isEmpty()) {
            // 静态缺省精选词
            list.add("周杰伦"); list.add("陈奕迅"); list.add("林俊杰"); list.add("邓紫棋");
            list.add("薛之谦"); list.add("华语流行"); list.add("欧美经典"); list.add("车载DJ");
        }
        return list;
    }

    // 2. 获取指定平台精选公开歌单
    public static ArrayList<PlaylistInfo> fetchPlaylists(String platform, String tag, int page) {
        ArrayList<PlaylistInfo> list = new ArrayList<PlaylistInfo>();
        try {
            if ("kg".equalsIgnoreCase(platform)) {
                String url = "http://mobilecdn.kugou.com/api/v3/tag/specialList?pagesize=30&page=" + page;
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONObject("data").getJSONArray("info");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new PlaylistInfo("kg_" + o.getString("specialid"), o.getString("specialname"), o.optString("nickname", "酷狗音乐"), o.optString("imgurl", "").replace("{size}", "400"), o.optString("playcount", ""), "kg"));
                    }
                }
            } else {
                // 网易云精品歌单
                String encodedTag = URLEncoder.encode(tag.equals("全部") ? "全部" : tag, "UTF-8");
                String url = "https://music.163.com/api/playlist/list?cat=" + encodedTag + "&limit=30&offset=" + ((page - 1) * 30);
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONArray("playlists");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new PlaylistInfo("wy_" + o.getLong("id"), o.getString("name"), o.optJSONObject("creator") != null ? o.optJSONObject("creator").optString("nickname", "") : "", o.optString("coverImgUrl", ""), o.optString("playCount", ""), "wy"));
                    }
                }
            }
        } catch (Throwable ignored) {}
        return list;
    }

    // 3. 通用 HTTPS GET 请求
    public static String httpGet(String urlStr) {
        HttpURLConnection conn = null;
        try {
            TLSSocketFactory.install();
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            if (conn instanceof HttpsURLConnection) {
                ((HttpsURLConnection) conn).setSSLSocketFactory(new TLSSocketFactory());
            }
            int code = conn.getResponseCode();
            if (code == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                return sb.toString();
            }
        } catch (Throwable ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }
}
