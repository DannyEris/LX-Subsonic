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

    // 1. 各平台热搜词拉取
    public static ArrayList<String> fetchHotSearch(String platform) {
        ArrayList<String> list = new ArrayList<String>();
        try {
            if ("wy".equalsIgnoreCase(platform)) {
                String res = httpGet("https://music.163.com/api/search/hot/get");
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray hots = root.getJSONObject("result").getJSONArray("hots");
                    for (int i = 0; i < Math.min(hots.length(), 20); i++) {
                        list.add(hots.getJSONObject(i).getString("first"));
                    }
                }
            } else if ("kg".equalsIgnoreCase(platform)) {
                String res = httpGet("http://mobilecdn.kugou.com/api/v3/search/hot");
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONObject("data").getJSONArray("info");
                    for (int i = 0; i < Math.min(arr.length(), 20); i++) {
                        list.add(arr.getJSONObject(i).getString("keyword"));
                    }
                }
            } else if ("kw".equalsIgnoreCase(platform)) {
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
            list.add("周杰伦"); list.add("陈奕迅"); list.add("林俊杰"); list.add("王菲");
            list.add("邓紫棋"); list.add("刀郎"); list.add("薛之谦"); list.add("电音 DJ");
        }
        return list;
    }

    // 2. 歌单广场：四大平台 (网易云、QQ、酷狗、酷我) 列表获取
    public static ArrayList<PlaylistInfo> fetchPlaylists(String platform, String tag, int sortIdx, int page) {
        ArrayList<PlaylistInfo> list = new ArrayList<PlaylistInfo>();
        try {
            if ("tx".equalsIgnoreCase(platform)) {
                int sortId = (sortIdx == 1) ? 2 : 5; // 2 最新，5 最热
                String url = "https://c.y.qq.com/splcloud/fcgi-bin/fcg_get_diss_by_tag.fcg?sin=" + ((page - 1) * 30) + "&ein=" + (page * 30 - 1) + "&categoryId=10000000&sortId=" + sortId + "&format=json";
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONObject("data").getJSONArray("list");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new PlaylistInfo("tx_" + o.getString("dissid"), o.getString("dissname"), o.optJSONObject("creator") != null ? o.optJSONObject("creator").optString("name", "") : "", o.optString("imgurl", ""), String.valueOf(o.optLong("listennum", 0)), "tx"));
                    }
                }
            } else if ("kg".equalsIgnoreCase(platform)) {
                String sortParam = (sortIdx == 1) ? "&sort=1" : "&sort=2";
                String url = "http://mobilecdn.kugou.com/api/v3/tag/specialList?pagesize=30&page=" + page + sortParam;
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONObject("data").getJSONArray("info");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new PlaylistInfo("kg_" + o.getString("specialid"), o.getString("specialname"), o.optString("nickname", "酷狗官方"), o.optString("imgurl", "").replace("{size}", "400"), o.optString("playcount", ""), "kg"));
                    }
                }
            } else if ("kw".equalsIgnoreCase(platform)) {
                String order = (sortIdx == 1) ? "new" : "hot";
                String url = "http://wapi.kuwo.cn/api/pc/classify/playlist/getRcmPlayList?pn=" + page + "&rn=30&order=" + order;
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONObject("data").getJSONArray("data");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new PlaylistInfo("kw_" + o.getString("id"), o.getString("name"), o.optString("uname", "酷我歌单"), o.optString("img", ""), String.valueOf(o.optLong("listencnt", 0)), "kw"));
                    }
                }
            } else {
                // 默认网易云
                String order = (sortIdx == 1) ? "new" : "hot";
                String cat = (tag == null || tag.length() == 0 || "全部".equals(tag) || "全部歌单".equals(tag)) ? "全部" : tag;
                String encodedTag = URLEncoder.encode(cat, "UTF-8");
                String url = "https://music.163.com/api/playlist/list?cat=" + encodedTag + "&order=" + order + "&limit=30&offset=" + ((page - 1) * 30);
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONArray("playlists");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new PlaylistInfo("wy_" + o.getLong("id"), o.getString("name"), o.optJSONObject("creator") != null ? o.optJSONObject("creator").optString("nickname", "") : "", o.optString("coverImgUrl", ""), String.valueOf(o.optLong("playCount", 0)), "wy"));
                    }
                }
            }
        } catch (Throwable ignored) {}
        return list;
    }

    // 3. 歌单广场：按关键字搜索歌单
    public static ArrayList<PlaylistInfo> searchPlaylists(String platform, String keyword, int page) {
        ArrayList<PlaylistInfo> list = new ArrayList<PlaylistInfo>();
        try {
            String encoded = URLEncoder.encode(keyword, "UTF-8");
            if ("tx".equalsIgnoreCase(platform)) {
                String url = "https://c.y.qq.com/soso/fcgi-bin/client_music_search_songlist?page_no=" + (page - 1) + "&num_per_page=30&format=json&query=" + encoded;
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONObject("data").getJSONArray("list");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new PlaylistInfo("tx_" + o.getString("dissid"), o.getString("dissname"), o.optJSONObject("creator") != null ? o.optJSONObject("creator").optString("name", "") : "", o.optString("imgurl", ""), String.valueOf(o.optLong("listennum", 0)), "tx"));
                    }
                }
            } else if ("kg".equalsIgnoreCase(platform)) {
                String url = "http://mobilecdn.kugou.com/api/v3/search/special?pagesize=30&page=" + page + "&keyword=" + encoded;
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONObject("data").getJSONArray("info");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new PlaylistInfo("kg_" + o.getString("specialid"), o.getString("specialname"), o.optString("nickname", "酷狗用户"), o.optString("imgurl", "").replace("{size}", "400"), o.optString("playcount", ""), "kg"));
                    }
                }
            } else if ("kw".equalsIgnoreCase(platform)) {
                String url = "http://search.kuwo.cn/r.s?all=" + encoded + "&ft=playlist&item=30&pn=" + (page - 1) + "&rformat=json";
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONArray("abslist");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new PlaylistInfo("kw_" + o.getString("playlistid"), o.getString("name"), o.optString("uname", "酷我歌单"), o.optString("pic", ""), String.valueOf(o.optLong("playcnt", 0)), "kw"));
                    }
                }
            } else {
                // 网易云
                String url = "https://music.163.com/api/search/get?s=" + encoded + "&type=1000&limit=30&offset=" + ((page - 1) * 30);
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONObject("result").getJSONArray("playlists");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new PlaylistInfo("wy_" + o.getLong("id"), o.getString("name"), o.optJSONObject("creator") != null ? o.optJSONObject("creator").optString("nickname", "") : "", o.optString("coverImgUrl", ""), String.valueOf(o.optLong("playCount", 0)), "wy"));
                    }
                }
            }
        } catch (Throwable ignored) {}
        return list;
    }

    // 4. 解析各平台歌单内部的歌曲列表
    public static ArrayList<MainActivity.DisplayEntry> fetchPlaylistSongs(String rawPlaylistId) {
        ArrayList<MainActivity.DisplayEntry> songs = new ArrayList<MainActivity.DisplayEntry>();
        try {
            if (rawPlaylistId.startsWith("wy_")) {
                String id = rawPlaylistId.substring(3);
                String url = "https://music.163.com/api/v6/playlist/detail?id=" + id;
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray tracks = root.getJSONObject("playlist").getJSONArray("tracks");
                    for (int i = 0; i < tracks.length(); i++) {
                        JSONObject t = tracks.getJSONObject(i);
                        String songId = "wy:" + t.getLong("id");
                        String title = t.getString("name");
                        String artist = "群星";
                        if (t.has("ar") && t.getJSONArray("ar").length() > 0) {
                            artist = t.getJSONArray("ar").getJSONObject(0).getString("name");
                        }
                        String cover = t.optJSONObject("al") != null ? t.optJSONObject("al").optString("picUrl", null) : null;
                        songs.add(new MainActivity.DisplayEntry(songId, title, artist, artist + " [320K MP3]", cover, "320K MP3", true, 320));
                    }
                }
            } else if (rawPlaylistId.startsWith("kg_")) {
                String id = rawPlaylistId.substring(3);
                String url = "http://mobilecdn.kugou.com/api/v3/special/song?specialid=" + id + "&pagesize=100&page=1";
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONObject("data").getJSONArray("info");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        String filename = o.optString("filename", "");
                        String title = filename;
                        String artist = "酷狗歌手";
                        if (filename.contains(" - ")) {
                            String[] p = filename.split(" - ", 2);
                            artist = p[0].trim();
                            title = p[1].trim();
                        }
                        String hash = o.optString("hash", "");
                        String songId = "kg:" + hash;
                        songs.add(new MainActivity.DisplayEntry(songId, title, artist, artist + " [320K MP3]", null, "320K MP3", true, 320));
                    }
                }
            }
        } catch (Throwable ignored) {}
        return songs;
    }

    // 5. 统一 HTTP GET 网络请求 (集成 TLS 1.2 兼容与防封 UA)
    public static String httpGet(String urlStr) {
        HttpURLConnection conn = null;
        try {
            TLSSocketFactory.install();
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(9000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36");
            conn.setRequestProperty("Referer", "https://y.qq.com/");
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
