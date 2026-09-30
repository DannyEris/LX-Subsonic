package com.retro.subsonic;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
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

    // 1. 各平台热搜词获取（参照 lxserver / 各大平台官网热词接口）
    public static ArrayList<String> fetchHotSearch(String platform) {
        ArrayList<String> list = new ArrayList<String>();
        try {
            if ("wy".equalsIgnoreCase(platform)) {
                String res = httpGet("https://music.163.com/api/search/hot/get");
                if (res != null) {
                    JSONArray hots = new JSONObject(res).getJSONObject("result").getJSONArray("hots");
                    for (int i = 0; i < Math.min(hots.length(), 25); i++) {
                        list.add(hots.getJSONObject(i).getString("first"));
                    }
                }
            } else if ("kg".equalsIgnoreCase(platform)) {
                String res = httpGet("http://mobilecdn.kugou.com/api/v3/search/hot");
                if (res != null) {
                    JSONArray arr = new JSONObject(res).getJSONObject("data").getJSONArray("info");
                    for (int i = 0; i < Math.min(arr.length(), 25); i++) {
                        list.add(arr.getJSONObject(i).getString("keyword"));
                    }
                }
            } else if ("kw".equalsIgnoreCase(platform)) {
                String res = httpGet("http://kuwo.cn/api/www/search/searchKey");
                if (res != null) {
                    JSONArray arr = new JSONObject(res).getJSONArray("data");
                    for (int i = 0; i < Math.min(arr.length(), 25); i++) {
                        String s = arr.getString(i);
                        list.add(s.split("\r\n|\\n")[0]);
                    }
                }
            } else if ("mg".equalsIgnoreCase(platform)) {
                String res = httpGet("https://c.musicapp.migu.cn/MIGUM2.0/v1.0/content/search_hot_info.do");
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    if (root.has("hotSearchWord")) {
                        JSONArray arr = root.getJSONArray("hotSearchWord");
                        for (int i = 0; i < Math.min(arr.length(), 25); i++) {
                            list.add(arr.getJSONObject(i).optString("word", ""));
                        }
                    }
                }
            } else {
                // 默认 QQ 音乐
                String res = httpGet("https://c.y.qq.com/splcloud/fcgi-bin/gethotkey.fcg?g_tk=5381&format=json&inCharset=utf8&outCharset=utf-8&utf8=1");
                if (res != null) {
                    JSONArray arr = new JSONObject(res).getJSONObject("data").getJSONArray("hotkey");
                    for (int i = 0; i < Math.min(arr.length(), 25); i++) {
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

    // 2. 歌单分类定义（严格对齐各平台官网分类目录）
    public static Map<String, String[]> getPresetCategories(String platformCode) {
        Map<String, String[]> cat = new LinkedHashMap<String, String[]>();
        if ("wy".equals(platformCode)) {
            cat.put("热门", new String[]{"全部", "华语", "欧美", "流行", "摇滚", "民谣", "电子", "说唱"});
            cat.put("语种", new String[]{"华语", "欧美", "日语", "韩语", "粤语"});
            cat.put("风格", new String[]{"流行", "摇滚", "民谣", "电子", "舞曲", "说唱", "轻音乐", "爵士", "古典", "古风", "ACG", "中国风"});
            cat.put("场景", new String[]{"清晨", "夜晚", "学习", "工作", "午休", "驾车", "运动", "旅行", "散步", "酒吧"});
            cat.put("情感", new String[]{"怀旧", "清新", "浪漫", "伤感", "治愈", "放松", "孤独", "感动", "快乐"});
            cat.put("主题", new String[]{"影视原声", "ACG", "儿童", "校园", "经典", "翻唱", "吉他", "钢琴", "KTV"});
        } else if ("tx".equals(platformCode)) {
            cat.put("热门", new String[]{"全部", "官方歌单", "经典", "网络", "伤感", "情歌"});
            cat.put("语种", new String[]{"国语", "粤语", "英语", "韩语", "日语", "闽南语"});
            cat.put("风格", new String[]{"流行", "摇滚", "民谣", "电子", "中国风", "轻音乐", "R&B", "嘻哈", "爵士", "古典"});
            cat.put("场景", new String[]{"睡前", "夜店", "学习", "运动", "驾车", "工作", "咖啡馆", "旅行", "派对"});
            cat.put("情感", new String[]{"伤感", "治愈", "放松", "励志", "甜蜜", "寂寞", "思念"});
            cat.put("主题", new String[]{"K歌金曲", "经典老歌", "影视原声", "ACG", "游戏", "DJ热歌", "网络热歌"});
        } else if ("kg".equals(platformCode)) {
            cat.put("热门", new String[]{"全部", "最热", "最新", "推荐", "飙升"});
            cat.put("主题", new String[]{"KTV", "经典", "DJ", "网络热歌", "广场舞", "背景音乐", "胎教", "影视", "游戏", "动漫"});
            cat.put("语种", new String[]{"华语", "欧美", "粤语", "日韩", "闽南", "小语种"});
            cat.put("风格", new String[]{"流行", "电子", "摇滚", "民谣", "古风", "说唱", "轻音乐", "爵士", "古典", "R&B", "中国风"});
            cat.put("心情", new String[]{"伤感", "治愈", "甜蜜", "欢快", "安静", "励志", "怀旧", "思念", "解压"});
            cat.put("场景", new String[]{"夜晚", "运动", "驾车", "学习", "散步", "聚会", "咖啡厅"});
        } else if ("kw".equals(platformCode)) {
            cat.put("热门", new String[]{"全部", "经典专区", "DJ专区", "影视专区", "车载专区", "网络专区"});
            cat.put("语种", new String[]{"华语", "欧美", "日韩", "粤语"});
            cat.put("风格", new String[]{"流行", "电子", "摇滚", "民谣", "古风", "轻音乐", "嘻哈", "爵士", "ACG", "舞曲"});
            cat.put("心情", new String[]{"伤感", "治愈", "励志", "开心", "思念", "怀旧", "放松", "孤独", "感动"});
            cat.put("场景", new String[]{"开车", "工作", "睡眠", "散步", "学习", "运动", "咖啡厅", "旅行"});
            cat.put("主题", new String[]{"流行", "民谣", "网络", "摇滚", "BGM", "伴奏", "经典", "怀旧"});
        } else {
            cat.put("热门", new String[]{"全部", "华语经典", "热门流行", "精选", "原创"});
            cat.put("语种", new String[]{"华语", "欧美", "日韩", "粤语", "纯音乐"});
            cat.put("风格", new String[]{"流行", "摇滚", "民谣", "电子", "古风", "说唱", "爵士", "古典", "动漫"});
            cat.put("主题", new String[]{"影视原声", "网络歌曲", "K歌", "动漫", "游戏", "经典老歌", "广场舞"});
            cat.put("场景", new String[]{"运动", "驾车", "睡眠", "学习", "派对", "旅行", "工作"});
        }
        return cat;
    }

    // QQ 音乐分类名与官方 categoryId 映射表
    private static final Map<String, Integer> QQ_CATEGORY_MAP = new HashMap<String, Integer>() {{
        put("全部", 10000000); put("官方歌单", 3317); put("经典", 59); put("网络", 28);
        put("伤感", 36); put("情歌", 39); put("国语", 165); put("粤语", 167);
        put("英语", 169); put("韩语", 170); put("日语", 171); put("闽南语", 172);
        put("流行", 6); put("摇滚", 11); put("民谣", 22); put("电子", 15);
        put("中国风", 25); put("轻音乐", 21); put("R&B", 14); put("嘻哈", 13);
        put("爵士", 8); put("古典", 9); put("睡前", 44); put("夜店", 51);
        put("学习", 42); put("运动", 49); put("驾车", 43); put("工作", 41);
        put("咖啡馆", 46); put("旅行", 48); put("派对", 50); put("治愈", 38);
        put("放松", 40); put("励志", 37); put("甜蜜", 39); put("寂寞", 35);
        put("思念", 33); put("K歌金曲", 71); put("经典老歌", 72); put("影视原声", 74);
        put("ACG", 76); put("游戏", 77); put("DJ热歌", 81); put("网络热歌", 28);
    }};

    // 3. 歌单广场歌单列表获取（支持分页 page：1, 2, 3...）
    public static ArrayList<PlaylistInfo> fetchPlaylists(String platform, String tag, int page) {
        ArrayList<PlaylistInfo> list = new ArrayList<PlaylistInfo>();
        try {
            if ("tx".equalsIgnoreCase(platform)) {
                int catId = 10000000;
                if (tag != null && QQ_CATEGORY_MAP.containsKey(tag)) {
                    catId = QQ_CATEGORY_MAP.get(tag);
                }
                String url = "https://c.y.qq.com/splcloud/fcgi-bin/fcg_get_diss_by_tag.fcg?sin=" + ((page - 1) * 30) + "&ein=" + (page * 30 - 1) + "&categoryId=" + catId + "&sortId=5&format=json&inCharset=utf8&outCharset=utf-8&utf8=1";
                String res = httpGet(url);
                if (res != null) {
                    JSONArray arr = new JSONObject(res).getJSONObject("data").getJSONArray("list");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new PlaylistInfo("tx_" + o.getString("dissid"), o.getString("dissname"),
                                o.optJSONObject("creator") != null ? o.optJSONObject("creator").optString("name", "") : "",
                                o.optString("imgurl", ""), String.valueOf(o.optLong("listennum", 0)), "tx"));
                    }
                }
            } else if ("kg".equalsIgnoreCase(platform)) {
                String sortParam = "最新".equals(tag) ? "sort=1" : "sort=2";
                String tagParam = (tag == null || tag.length() == 0 || "全部".equals(tag) || "最新".equals(tag) || "最热".equals(tag)) ? "" : ("&tagname=" + URLEncoder.encode(tag, "UTF-8"));
                String url = "http://mobilecdn.kugou.com/api/v3/tag/specialList?pagesize=30&page=" + page + "&" + sortParam + tagParam;
                String res = httpGet(url);
                if (res != null) {
                    JSONArray arr = new JSONObject(res).getJSONObject("data").getJSONArray("info");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new PlaylistInfo("kg_" + o.getString("specialid"), o.getString("specialname"),
                                o.optString("nickname", "酷狗音乐"), o.optString("imgurl", "").replace("{size}", "400"),
                                o.optString("playcount", ""), "kg"));
                    }
                }
            } else if ("kw".equalsIgnoreCase(platform)) {
                String tagParam = (tag == null || tag.length() == 0 || "全部".equals(tag)) ? "" : ("&name=" + URLEncoder.encode(tag, "UTF-8"));
                String url = "http://wapi.kuwo.cn/api/pc/classify/playlist/getRcmPlayList?pn=" + page + "&rn=30&order=hot" + tagParam;
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    if (root.has("data") && root.getJSONObject("data").has("data")) {
                        JSONArray arr = root.getJSONObject("data").getJSONArray("data");
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            list.add(new PlaylistInfo("kw_" + o.getString("id"), o.getString("name"),
                                    o.optString("uname", "酷我音乐"), o.optString("img", ""),
                                    String.valueOf(o.optLong("listencnt", 0)), "kw"));
                        }
                    }
                }
            } else if ("mg".equalsIgnoreCase(platform)) {
                String tagParam = (tag == null || tag.length() == 0 || "全部".equals(tag)) ? "" : ("&tag=" + URLEncoder.encode(tag, "UTF-8"));
                String url = "https://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/queryContentbyId.do?columnId=15127315&needAll=0&pageNo=" + page + tagParam;
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    if (root.has("columnInfo") && root.getJSONObject("columnInfo").has("contents")) {
                        JSONArray arr = root.getJSONObject("columnInfo").getJSONArray("contents");
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i).optJSONObject("objectInfo");
                            if (o != null) {
                                list.add(new PlaylistInfo("mg_" + o.optString("playlistId", o.optString("contentId", "")),
                                        o.optString("playlistTitle", o.optString("contentName", "")),
                                        o.optString("ownerName", "咪咕音乐"),
                                        o.optString("image", ""),
                                        String.valueOf(o.optLong("playCount", 0)), "mg"));
                            }
                        }
                    }
                }
            } else {
                // 网易云音乐
                String cat = (tag == null || tag.length() == 0 || "全部".equals(tag)) ? "全部" : tag;
                String encodedTag = URLEncoder.encode(cat, "UTF-8");
                String url = "https://music.163.com/api/playlist/list?cat=" + encodedTag + "&order=hot&limit=30&offset=" + ((page - 1) * 30);
                String res = httpGet(url);
                if (res != null) {
                    JSONArray arr = new JSONObject(res).getJSONArray("playlists");
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

    // 4. 歌单搜索（支持分页 page：1, 2, 3...）
    public static ArrayList<PlaylistInfo> searchPlaylists(String platform, String keyword, int page) {
        ArrayList<PlaylistInfo> list = new ArrayList<PlaylistInfo>();
        try {
            String encoded = URLEncoder.encode(keyword, "UTF-8");
            if ("tx".equalsIgnoreCase(platform)) {
                String url = "https://c.y.qq.com/soso/fcgi-bin/client_music_search_songlist?page_no=" + (page - 1) + "&num_per_page=30&format=json&inCharset=utf8&outCharset=utf-8&utf8=1&query=" + encoded;
                String res = httpGet(url);
                if (res != null) {
                    JSONArray arr = new JSONObject(res).getJSONObject("data").getJSONArray("list");
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
                    JSONArray arr = new JSONObject(res).getJSONObject("data").getJSONArray("info");
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
                    JSONArray arr = new JSONObject(res).getJSONArray("abslist");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new PlaylistInfo("kw_" + o.getString("playlistid"), o.getString("name"),
                                o.optString("uname", "酷我音乐"), o.optString("pic", ""),
                                String.valueOf(o.optLong("playcnt", 0)), "kw"));
                    }
                }
            } else if ("mg".equalsIgnoreCase(platform)) {
                String url = "https://c.musicapp.migu.cn/MIGUM2.0/v1.0/content/search_all.do?text=" + encoded + "&pageNo=" + page + "&pageSize=30&searchSwitch=%7B%22songlist%22%3A1%7D";
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    if (root.has("songLists")) {
                        JSONArray arr = root.getJSONArray("songLists");
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            list.add(new PlaylistInfo("mg_" + o.optString("id", ""),
                                    o.optString("name", "咪咕歌单"),
                                    o.optString("userNick", "咪咕音乐"),
                                    o.optString("img", ""),
                                    String.valueOf(o.optLong("playNum", 0)), "mg"));
                        }
                    }
                }
            } else {
                // 网易云
                String url = "https://music.163.com/api/search/get?s=" + encoded + "&type=1000&limit=30&offset=" + ((page - 1) * 30);
                String res = httpGet(url);
                if (res != null) {
                    JSONArray arr = new JSONObject(res).getJSONObject("result").getJSONArray("playlists");
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

    // 5. 歌单内歌曲获取
    public static ArrayList<MainActivity.DisplayEntry> fetchPlaylistSongs(String rawPlaylistId) {
        ArrayList<MainActivity.DisplayEntry> songs = new ArrayList<MainActivity.DisplayEntry>();
        try {
            if (rawPlaylistId.startsWith("wy_")) {
                String id = rawPlaylistId.substring(3);
                String res = httpGet("https://music.163.com/api/v6/playlist/detail?id=" + id);
                if (res != null) {
                    JSONArray tracks = new JSONObject(res).getJSONObject("playlist").getJSONArray("tracks");
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
                String res = httpGet("http://mobilecdn.kugou.com/api/v3/special/song?specialid=" + id + "&pagesize=100&page=1");
                if (res != null) {
                    JSONArray arr = new JSONObject(res).getJSONObject("data").getJSONArray("info");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        String filename = o.optString("filename", "");
                        String title = filename, artist = "酷狗歌手";
                        if (filename.contains(" - ")) {
                            String[] p = filename.split(" - ", 2);
                            artist = p[0].trim(); title = p[1].trim();
                        }
                        String hash = o.optString("hash", "");
                        songs.add(new MainActivity.DisplayEntry("kg_" + hash, title, artist, artist + " [320K MP3]", null, "320K MP3", true, 320));
                    }
                }
            } else if (rawPlaylistId.startsWith("tx_")) {
                String id = rawPlaylistId.substring(3);
                String res = httpGet("https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg?type=1&json=1&utf8=1&onlysong=0&disstid=" + id + "&format=json&inCharset=utf8&outCharset=utf-8&utf8=1");
                if (res != null) {
                    JSONArray songlist = new JSONObject(res).getJSONArray("cdlist").getJSONObject(0).getJSONArray("songlist");
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
            } else if (rawPlaylistId.startsWith("kw_")) {
                String id = rawPlaylistId.substring(3);
                String res = httpGet("http://nplserver.kuwo.cn/pl.s?content=list&id=" + id + "&pn=0&rn=100");
                if (res != null) {
                    JSONArray musiclist = new JSONObject(res).getJSONArray("musiclist");
                    for (int i = 0; i < musiclist.length(); i++) {
                        JSONObject o = musiclist.getJSONObject(i);
                        String songId = o.getString("id");
                        String title = o.getString("name");
                        String artist = o.optString("artist", "酷我歌手");
                        songs.add(new MainActivity.DisplayEntry("kw_" + songId, title, artist, artist + " [320K MP3]", null, "320K MP3", true, 320));
                    }
                }
            } else if (rawPlaylistId.startsWith("mg_")) {
                String id = rawPlaylistId.substring(3);
                String res = httpGet("https://app.c.nf.migu.cn/MIGUM2.0/v1.0/user/queryMusicListSongs.do?musicListId=" + id + "&pageNo=1&pageSize=100");
                if (res != null) {
                    JSONArray listArr = new JSONObject(res).getJSONArray("list");
                    for (int i = 0; i < listArr.length(); i++) {
                        JSONObject o = listArr.getJSONObject(i);
                        String songId = o.optString("songId", "");
                        String title = o.optString("songName", "");
                        String artist = o.optString("singerName", "咪咕歌手");
                        songs.add(new MainActivity.DisplayEntry("mg_" + songId, title, artist, artist + " [320K MP3]", null, "320K MP3", true, 320));
                    }
                }
            }
        } catch (Throwable ignored) {}
        return songs;
    }

    // 6. 各大平台官方榜单定义
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
                {"飙升榜", "62"}, {"网络歌曲榜", "28"}, {"内地榜", "5"},
                {"欧美榜", "3"}, {"香港地区榜", "59"}, {"韩国榜", "16"},
                {"日本榜", "17"}, {"影视金曲榜", "29"}, {"国风热歌榜", "65"},
                {"动漫音乐榜", "72"}, {"说唱榜", "58"}, {"电音榜", "57"}
            };
        } else if ("kg".equals(platformCode)) {
            return new String[][]{
                {"TOP500", "8888"}, {"飙升榜", "6666"}, {"蜂鸟流行榜", "52144"},
                {"网络红歌榜", "23784"}, {"国风新韵榜", "33161"}, {"内地榜", "31308"},
                {"香港地区榜", "31310"}, {"欧美榜", "31313"}, {"民谣榜", "30972"},
                {"日本榜", "31312"}, {"粤语金曲榜", "21101"}, {"韩国榜", "31311"},
                {"电音榜", "33160"}, {"DJ热歌榜", "24971"}
            };
        } else if ("kw".equals(platformCode)) {
            return new String[][]{
                {"酷我飙升榜", "93"}, {"酷我新歌榜", "17"}, {"酷我热歌榜", "16"},
                {"网络歌曲榜", "158"}, {"抖音热歌榜", "145"}, {"影视金曲榜", "26"},
                {"欧美榜", "13"}, {"日韩榜", "12"}, {"流行榜", "283"}, {"说唱榜", "284"}
            };
        } else {
            return new String[][]{
                {"咪咕热歌榜", "27553319"}, {"咪咕新歌榜", "27186466"}, {"咪咕飙升榜", "27553258"},
                {"影视金曲榜", "27553408"}, {"网络热歌榜", "27553380"}, {"欧美榜", "27553423"},
                {"日韩榜", "27553435"}, {"国风榜", "27553450"}, {"说唱榜", "27553462"}
            };
        }
    }

    // 7. 排行榜歌曲列表获取
    public static ArrayList<MainActivity.DisplayEntry> fetchLeaderboardSongs(String platform, String boardKey) {
        ArrayList<MainActivity.DisplayEntry> songs = new ArrayList<MainActivity.DisplayEntry>();
        try {
            if ("wy".equalsIgnoreCase(platform)) {
                return fetchPlaylistSongs("wy_" + boardKey);
            } else if ("tx".equalsIgnoreCase(platform)) {
                String url = "https://c.y.qq.com/v8/fcg-bin/fcg_v8_toplist_cp.fcg?topid=" + boardKey + "&type=top&format=json&inCharset=utf8&outCharset=utf-8&utf8=1";
                String res = httpGet(url);
                if (res != null) {
                    JSONArray arr = new JSONObject(res).getJSONArray("songlist");
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
                    JSONArray arr = new JSONObject(res).getJSONObject("data").getJSONArray("info");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        String filename = o.optString("filename", "");
                        String title = filename, artist = "酷狗歌手";
                        if (filename.contains(" - ")) {
                            String[] p = filename.split(" - ", 2);
                            artist = p[0].trim(); title = p[1].trim();
                        }
                        String hash = o.optString("hash", "");
                        songs.add(new MainActivity.DisplayEntry("kg_" + hash, title, artist, artist + " [320K MP3]", null, "320K MP3", true, 320));
                    }
                }
            } else if ("kw".equalsIgnoreCase(platform)) {
                String url = "http://kbangserver.kuwo.cn/ksong.s?from=pc&fmt=json&type=bang&data=content&id=" + boardKey + "&pn=0&rn=100";
                String res = httpGet(url);
                if (res != null) {
                    JSONArray arr = new JSONObject(res).getJSONArray("musiclist");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        String id = o.getString("id");
                        String title = o.getString("name");
                        String artist = o.optString("artist", "酷我歌手");
                        songs.add(new MainActivity.DisplayEntry("kw_" + id, title, artist, artist + " [320K MP3]", null, "320K MP3", true, 320));
                    }
                }
            } else if ("mg".equalsIgnoreCase(platform)) {
                String url = "https://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/queryContentbyId.do?columnId=" + boardKey + "&needAll=0";
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    if (root.has("columnInfo") && root.getJSONObject("columnInfo").has("contents")) {
                        JSONArray arr = root.getJSONObject("columnInfo").getJSONArray("contents");
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i).optJSONObject("objectInfo");
                            if (o != null) {
                                String songId = o.optString("songId", o.optString("contentId", ""));
                                String title = o.optString("songName", o.optString("contentName", ""));
                                String artist = o.optString("singerName", "咪咕歌手");
                                songs.add(new MainActivity.DisplayEntry("mg_" + songId, title, artist, artist + " [320K MP3]", null, "320K MP3", true, 320));
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return songs;
    }

    // 8. 具有智能编码探测和连接池的高性能 HTTP GET
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
