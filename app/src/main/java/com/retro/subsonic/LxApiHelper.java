package com.retro.subsonic;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
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

    public static final String[] PLATFORM_NAMES = new String[]{"聚合搜索", "QQ音乐", "网易云", "酷狗音乐", "酷我音乐", "咪咕音乐"};
    public static final String[] PLATFORM_CODES = new String[]{"all", "tx", "wy", "kg", "kw", "mg"};

    public static final String[] PLAZA_PLATFORM_NAMES = new String[]{"QQ音乐", "网易云", "酷狗音乐", "酷我音乐", "咪咕音乐"};
    public static final String[] PLAZA_PLATFORM_CODES = new String[]{"tx", "wy", "kg", "kw", "mg"};

    // 1. 各平台热门搜索词
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
                // QQ音乐接口增加强制 UTF-8 声明，彻底杜绝乱码
                String res = httpGet("https://c.y.qq.com/splcloud/fcgi-bin/gethotkey.fcg?g_tk=5381&format=json&inCharset=utf8&outCharset=utf-8&utf8=1");
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
            list.add("周杰伦"); list.add("林俊杰"); list.add("陈奕迅"); list.add("邓紫棋");
            list.add("薛之谦"); list.add("告白气球"); list.add("七里香"); list.add("流行DJ");
        }
        return list;
    }

    // 2. 歌单广场分类定义
    public static Map<String, String[]> getPresetCategories(String platformCode) {
        Map<String, String[]> cat = new LinkedHashMap<String, String[]>();
        if ("wy".equals(platformCode)) {
            cat.put("热门", new String[]{"全部", "华语", "欧美", "流行", "摇滚", "民谣", "电子", "说唱"});
            cat.put("语种", new String[]{"华语", "欧美", "日语", "韩语", "粤语"});
            cat.put("风格", new String[]{"流行", "摇滚", "民谣", "电子", "舞曲", "说唱", "轻音乐", "爵士", "古典", "古风", "ACG"});
            cat.put("场景", new String[]{"清晨", "夜晚", "学习", "工作", "午休", "驾车", "运动", "旅行", "散步"});
            cat.put("情感", new String[]{"怀旧", "清新", "浪漫", "伤感", "治愈", "放松", "孤独", "感动"});
            cat.put("主题", new String[]{"影视原声", "ACG", "儿童", "校园", "经典", "翻唱", "吉他", "钢琴"});
        } else if ("tx".equals(platformCode)) {
            cat.put("热门", new String[]{"全部", "官方歌单", "经典", "网络", "伤感", "情歌"});
            cat.put("语种", new String[]{"国语", "粤语", "英语", "韩语", "日语", "闽南语"});
            cat.put("风格", new String[]{"流行", "摇滚", "民谣", "电子", "中国风", "轻音乐", "R&B"});
            cat.put("场景", new String[]{"睡前", "夜店", "学习", "运动", "驾车", "工作", "咖啡馆"});
            cat.put("主题", new String[]{"K歌金曲", "经典老歌", "影视原声", "ACG", "游戏", "DJ热歌"});
        } else if ("kg".equals(platformCode)) {
            cat.put("热门", new String[]{"全部", "最热", "最新", "推荐", "飙升"});
            cat.put("主题", new String[]{"KTV", "经典", "DJ", "网络热歌", "广场舞", "背景音乐"});
            cat.put("语种", new String[]{"华语", "欧美", "粤语", "日韩", "闽南"});
            cat.put("风格", new String[]{"流行", "电子", "摇滚", "民谣", "古风", "说唱"});
            cat.put("心情", new String[]{"伤感", "治愈", "甜蜜", "欢快", "安静", "励志"});
        } else if ("kw".equals(platformCode)) {
            cat.put("热门", new String[]{"全部", "经典专区", "DJ专区", "影视专区", "车载专区"});
            cat.put("主题", new String[]{"流行", "民谣", "网络", "摇滚", "BGM", "伴奏"});
            cat.put("心情", new String[]{"伤感", "治愈", "励志", "开心", "思念", "怀旧"});
            cat.put("场景", new String[]{"开车", "工作", "睡眠", "散步", "学习", "运动"});
        } else {
            cat.put("热门", new String[]{"全部", "华语经典", "热门流行", "精选"});
            cat.put("语种", new String[]{"华语", "欧美", "日韩", "粤语"});
            cat.put("风格", new String[]{"流行", "摇滚", "民谣", "电子", "古风"});
            cat.put("主题", new String[]{"影视原声", "网络歌曲", "K歌", "动漫"});
        }
        return cat;
    }

    // 3. 歌单广场获取（带UTF-8编码及分类支持）
    public static ArrayList<PlaylistInfo> fetchPlaylists(String platform, String tag, int page) {
        ArrayList<PlaylistInfo> list = new ArrayList<PlaylistInfo>();
        try {
            if ("tx".equalsIgnoreCase(platform)) {
                String catParam = (tag == null || tag.length() == 0 || "全部".equals(tag)) ? "10000000" : URLEncoder.encode(tag, "UTF-8");
                String url = "https://c.y.qq.com/splcloud/fcgi-bin/fcg_get_diss_by_tag.fcg?sin=" + ((page - 1) * 30) + "&ein=" + (page * 30 - 1) + "&categoryId=10000000&sortId=5&format=json&inCharset=utf8&outCharset=utf-8&utf8=1";
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONObject("data").getJSONArray("list");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new PlaylistInfo("tx_" + o.getString("dissid"), o.getString("dissname"),
                                o.optJSONObject("creator") != null ? o.optJSONObject("creator").optString("name", "") : "",
                                o.optString("imgurl", ""), String.valueOf(o.optLong("listennum", 0)), "tx"));
                    }
                }
            } else if ("kg".equalsIgnoreCase(platform)) {
                String url = "http://mobilecdn.kugou.com/api/v3/tag/specialList?pagesize=30&page=" + page + "&sort=2";
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONObject("data").getJSONArray("info");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new PlaylistInfo("kg_" + o.getString("specialid"), o.getString("specialname"),
                                o.optString("nickname", "酷狗音乐"), o.optString("imgurl", "").replace("{size}", "400"),
                                o.optString("playcount", ""), "kg"));
                    }
                }
            } else if ("kw".equalsIgnoreCase(platform)) {
                String url = "http://wapi.kuwo.cn/api/pc/classify/playlist/getRcmPlayList?pn=" + page + "&rn=30&order=hot";
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONObject("data").getJSONArray("data");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new PlaylistInfo("kw_" + o.getString("id"), o.getString("name"),
                                o.optString("uname", "酷我音乐"), o.optString("img", ""),
                                String.valueOf(o.optLong("listencnt", 0)), "kw"));
                    }
                }
            } else {
                // 网易云
                String cat = (tag == null || tag.length() == 0 || "全部".equals(tag)) ? "全部" : tag;
                String encodedTag = URLEncoder.encode(cat, "UTF-8");
                String url = "https://music.163.com/api/playlist/list?cat=" + encodedTag + "&order=hot&limit=30&offset=" + ((page - 1) * 30);
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONArray("playlists");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new PlaylistInfo("wy_" + o.getLong("id"), o.getString("name"),
                                o.optJSONObject("creator") != null ? o.optJSONObject("creator").optString("nickname", "") : "",
                                o.optString("coverImgUrl", ""), String.valueOf(o.optLong("playCount", 0)), "wy"));
                    }
                }
            }
        } catch (Throwable ignored) {}
        return list;
    }

    // 4. 歌单搜索
    public static ArrayList<PlaylistInfo> searchPlaylists(String platform, String keyword, int page) {
        ArrayList<PlaylistInfo> list = new ArrayList<PlaylistInfo>();
        try {
            String encoded = URLEncoder.encode(keyword, "UTF-8");
            if ("tx".equalsIgnoreCase(platform)) {
                String url = "https://c.y.qq.com/soso/fcgi-bin/client_music_search_songlist?page_no=" + (page - 1) + "&num_per_page=30&format=json&inCharset=utf8&outCharset=utf-8&utf8=1&query=" + encoded;
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONObject("data").getJSONArray("list");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new PlaylistInfo("tx_" + o.getString("dissid"), o.getString("dissname"),
                                o.optJSONObject("creator") != null ? o.optJSONObject("creator").optString("name", "") : "",
                                o.optString("imgurl", ""), String.valueOf(o.optLong("listennum", 0)), "tx"));
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
                        list.add(new PlaylistInfo("kg_" + o.getString("specialid"), o.getString("specialname"),
                                o.optString("nickname", "酷狗音乐"), o.optString("imgurl", "").replace("{size}", "400"),
                                o.optString("playcount", ""), "kg"));
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
                        list.add(new PlaylistInfo("kw_" + o.getString("playlistid"), o.getString("name"),
                                o.optString("uname", "酷我音乐"), o.optString("pic", ""),
                                String.valueOf(o.optLong("playcnt", 0)), "kw"));
                    }
                }
            } else {
                String url = "https://music.163.com/api/search/get?s=" + encoded + "&type=1000&limit=30&offset=" + ((page - 1) * 30);
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONObject("result").getJSONArray("playlists");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new PlaylistInfo("wy_" + o.getLong("id"), o.getString("name"),
                                o.optJSONObject("creator") != null ? o.optJSONObject("creator").optString("nickname", "") : "",
                                o.optString("coverImgUrl", ""), String.valueOf(o.optLong("playCount", 0)), "wy"));
                    }
                }
            }
        } catch (Throwable ignored) {}
        return list;
    }

    // 5. 歌单内歌曲获取（规范生成下划线格式 ID，确保可播）
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
                        String songId = "wy_" + t.getLong("id");
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
                        String songId = "kg_" + hash;
                        songs.add(new MainActivity.DisplayEntry(songId, title, artist, artist + " [320K MP3]", null, "320K MP3", true, 320));
                    }
                }
            } else if (rawPlaylistId.startsWith("tx_")) {
                String id = rawPlaylistId.substring(3);
                String url = "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg?type=1&json=1&utf8=1&onlysong=0&disstid=" + id + "&format=json&inCharset=utf8&outCharset=utf-8&utf8=1";
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray songlist = root.getJSONArray("cdlist").getJSONObject(0).getJSONArray("songlist");
                    for (int i = 0; i < songlist.length(); i++) {
                        JSONObject s = songlist.getJSONObject(i);
                        String songmid = s.getString("songmid");
                        String title = s.getString("songname");
                        String artist = "QQ歌手";
                        if (s.has("singer") && s.getJSONArray("singer").length() > 0) {
                            artist = s.getJSONArray("singer").getJSONObject(0).getString("name");
                        }
                        songs.add(new MainActivity.DisplayEntry("tx_" + songmid, title, artist, artist + " [320K MP3]", null, "320K MP3", true, 320));
                    }
                }
            }
        } catch (Throwable ignored) {}
        return songs;
    }

    // 6. 各大平台排行榜定义
    public static String[][] getPresetLeaderboards(String platformCode) {
        if ("wy".equals(platformCode)) {
            return new String[][]{
                {"飙升榜", "19723756"}, {"新歌榜", "3779629"}, {"原创榜", "2884035"},
                {"热歌榜", "3778678"}, {"说唱榜", "991319590"}, {"古典榜", "71385702"},
                {"电音榜", "1978921795"}, {"ACG榜", "3001835"}, {"韩语榜", "745956260"},
                {"欧美热歌榜", "2809513713"}, {"日本Oricon榜", "60198"}, {"美国Billboard榜", "60131"}
            };
        } else if ("tx".equals(platformCode)) {
            return new String[][]{
                {"流行指数榜", "4"}, {"热歌榜", "26"}, {"新歌榜", "27"},
                {"飙升榜", "62"}, {"说唱榜", "58"}, {"电音榜", "57"},
                {"网络歌曲榜", "28"}, {"内地榜", "5"}, {"欧美榜", "3"},
                {"香港地区榜", "59"}, {"韩国榜", "16"}, {"日本榜", "17"},
                {"影视金曲榜", "29"}, {"国风热歌榜", "65"}, {"动漫音乐榜", "72"}
            };
        } else if ("kg".equals(platformCode)) {
            return new String[][]{
                {"TOP500", "8888"}, {"飙升榜", "6666"}, {"蜂鸟流行音乐榜", "52144"},
                {"网络红歌榜", "23784"}, {"说唱先锋榜", "46910"}, {"电音榜", "33161"},
                {"内地榜", "31308"}, {"香港地区榜", "31310"}, {"欧美榜", "31313"},
                {"民谣榜", "30972"}, {"日本榜", "31312"}, {"粤语金曲榜", "21101"}
            };
        } else if ("kw".equals(platformCode)) {
            return new String[][]{
                {"酷我飙升榜", "93"}, {"酷我新歌榜", "17"}, {"酷我热歌榜", "16"},
                {"网络歌曲榜", "158"}, {"抖音热歌榜", "145"}, {"影视金曲榜", "26"},
                {"欧美榜", "13"}, {"日韩榜", "12"}
            };
        } else {
            return new String[][]{
                {"咪咕热歌榜", "27553319"}, {"咪咕新歌榜", "27186466"}, {"咪咕飙升榜", "27553258"},
                {"影视金曲榜", "27553408"}, {"网络热歌榜", "27553380"}
            };
        }
    }

    // 7. 排行榜歌曲数据抓取（下划线格式 ID，支持可播）
    public static ArrayList<MainActivity.DisplayEntry> fetchLeaderboardSongs(String platform, String boardKey) {
        ArrayList<MainActivity.DisplayEntry> songs = new ArrayList<MainActivity.DisplayEntry>();
        try {
            if ("wy".equalsIgnoreCase(platform)) {
                return fetchPlaylistSongs("wy_" + boardKey);
            } else if ("tx".equalsIgnoreCase(platform)) {
                String url = "https://c.y.qq.com/v8/fcg-bin/fcg_v8_toplist_cp.fcg?topid=" + boardKey + "&type=top&format=json&inCharset=utf8&outCharset=utf-8&utf8=1";
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONArray("songlist");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject data = arr.getJSONObject(i).getJSONObject("data");
                        String mid = data.getString("songmid");
                        String title = data.getString("songname");
                        String artist = "群星";
                        if (data.has("singer") && data.getJSONArray("singer").length() > 0) {
                            artist = data.getJSONArray("singer").getJSONObject(0).getString("name");
                        }
                        songs.add(new MainActivity.DisplayEntry("tx_" + mid, title, artist, artist + " [320K MP3]", null, "320K MP3", true, 320));
                    }
                }
            } else if ("kg".equalsIgnoreCase(platform)) {
                String url = "http://mobilecdn.kugou.com/api/v3/rank/song?rankid=" + boardKey + "&page=1&pagesize=100";
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
                        songs.add(new MainActivity.DisplayEntry("kg_" + hash, title, artist, artist + " [320K MP3]", null, "320K MP3", true, 320));
                    }
                }
            } else if ("kw".equalsIgnoreCase(platform)) {
                String url = "http://kbangserver.kuwo.cn/ksong.s?from=pc&fmt=json&type=bang&data=content&id=" + boardKey + "&pn=0&rn=100";
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONArray("musiclist");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        String id = o.getString("id");
                        String title = o.getString("name");
                        String artist = o.optString("artist", "酷我歌手");
                        songs.add(new MainActivity.DisplayEntry("kw_" + id, title, artist, artist + " [320K MP3]", null, "320K MP3", true, 320));
                    }
                }
            }
        } catch (Throwable ignored) {}
        return songs;
    }

    // 8. 通用 GET 请求（动态检测 Content-Type 编码，彻底防止中文乱码）
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
            if (conn.getResponseCode() == 200) {
                String charset = "UTF-8";
                String contentType = conn.getContentType();
                if (contentType != null) {
                    String lower = contentType.toLowerCase();
                    if (lower.contains("charset=gbk")) charset = "GBK";
                    else if (lower.contains("charset=gb2312")) charset = "GB2312";
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), charset));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
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
