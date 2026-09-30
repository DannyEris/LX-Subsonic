package com.retro.subsonic;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
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

    public static final String[] PLATFORM_NAMES = new String[]{"全部平台", "QQ音乐", "网易云", "酷狗音乐", "酷我音乐", "咪咕音乐"};
    public static final String[] PLATFORM_CODES = new String[]{"all", "tx", "wy", "kg", "kw", "mg"};

    public static final String[] PLAZA_PLATFORM_NAMES = new String[]{"QQ音乐", "网易云", "酷狗音乐", "酷我音乐", "咪咕音乐"};
    public static final String[] PLAZA_PLATFORM_CODES = new String[]{"tx", "wy", "kg", "kw", "mg"};

    // 1. 各平台官方实时热门搜索词接口 (避免请求头缺失被拦截)
    public static ArrayList<String> fetchHotSearch(String platform) {
        ArrayList<String> list = new ArrayList<String>();
        try {
            if ("wy".equalsIgnoreCase(platform)) {
                String res = httpGet("https://music.163.com/api/search/hot/get");
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    if (root.has("result") && root.getJSONObject("result").has("hots")) {
                        JSONArray hots = root.getJSONObject("result").getJSONArray("hots");
                        for (int i = 0; i < Math.min(hots.length(), 25); i++) {
                            list.add(hots.getJSONObject(i).getString("first").trim());
                        }
                    }
                }
            } else if ("kg".equalsIgnoreCase(platform)) {
                // 酷狗移动端接口，无需Cookie防盗链
                String res = httpGet("https://mobilecdn.kugou.com/api/v3/search/hot?format=json");
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    if (root.has("data") && root.getJSONObject("data").has("info")) {
                        JSONArray arr = root.getJSONObject("data").getJSONArray("info");
                        for (int i = 0; i < Math.min(arr.length(), 25); i++) {
                            list.add(arr.getJSONObject(i).getString("keyword").trim());
                        }
                    }
                }
            } else if ("kw".equalsIgnoreCase(platform)) {
                // 酷我开放搜索热词接口，避免PC端403跨域与Token拦截
                String res = httpGet("http://search.kuwo.cn/r.s?all=&ft=hot&item=30&rformat=json&encoding=utf8");
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    if (root.has("abslist")) {
                        JSONArray arr = root.getJSONArray("abslist");
                        for (int i = 0; i < Math.min(arr.length(), 25); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            String kw = o.optString("key", o.optString("KEY", ""));
                            if (kw.length() > 0) list.add(kw.trim());
                        }
                    }
                }
            } else if ("mg".equalsIgnoreCase(platform)) {
                String res = httpGet("https://c.musicapp.migu.cn/MIGUM2.0/v1.0/content/search_hot_info.do");
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    if (root.has("hotSearchWord")) {
                        JSONArray arr = root.getJSONArray("hotSearchWord");
                        for (int i = 0; i < Math.min(arr.length(), 25); i++) {
                            list.add(arr.getJSONObject(i).optString("word", "").trim());
                        }
                    }
                }
            } else {
                // QQ 音乐：移动端微信小程序通用无鉴权接口
                String qqUrl = "https://u.y.qq.com/cgi-bin/musicu.fcg?data=%7B%22hotkey%22%3A%7B%22module%22%3A%22tencent_musicsoso_hotkey.HotkeyService%22%2C%22method%22%3A%22GetHotkeyForQQMusicMobile%22%2C%22param%22%3A%7B%22remoteplace%22%3A%22txt.miniapp.wx%22%7D%7D%7D";
                String res = httpGet(qqUrl);
                if (res != null && res.contains("\"vec_hotkey\"")) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONObject("hotkey").getJSONObject("data").getJSONArray("vec_hotkey");
                    for (int i = 0; i < Math.min(arr.length(), 25); i++) {
                        list.add(arr.getJSONObject(i).getString("title").trim());
                    }
                } else {
                    String resOld = httpGet("https://c.y.qq.com/splcloud/fcgi-bin/gethotkey.fcg?g_tk=5381&format=json&inCharset=utf8&outCharset=utf-8&utf8=1");
                    if (resOld != null) {
                        JSONArray arr = new JSONObject(resOld).getJSONObject("data").getJSONArray("hotkey");
                        for (int i = 0; i < Math.min(arr.length(), 25); i++) {
                            list.add(arr.getJSONObject(i).getString("k").trim());
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (list.isEmpty()) {
            list.add("周杰伦"); list.add("陈奕迅"); list.add("林俊杰"); list.add("薛之谦");
            list.add("邓紫棋"); list.add("流行趋势"); list.add("欧美热歌"); list.add("车载DJ");
        }
        return list;
    }

    // 2. 歌单广场分类标签（严格对齐 LX Music 桌面端分类结构）
    public static Map<String, String[]> getPresetCategories(String platformCode) {
        Map<String, String[]> map = new LinkedHashMap<String, String[]>();
        if ("wy".equalsIgnoreCase(platformCode)) {
            map.put("语种", new String[]{"华语", "欧美", "日语", "韩语", "粤语"});
            map.put("风格", new String[]{"流行", "摇滚", "民谣", "电子", "舞曲", "说唱", "轻音乐", "爵士", "乡村", "R&B/Soul", "古典", "民族", "英伦", "金属", "朋克", "蓝调", "雷鬼", "世界音乐", "拉丁", "New Age", "古风", "后摇", "Bossa Nova"});
            map.put("场景", new String[]{"清晨", "夜晚", "学习", "工作", "午休", "下午茶", "地铁", "驾车", "运动", "旅行", "散步", "酒吧"});
            map.put("情感", new String[]{"怀旧", "清新", "浪漫", "伤感", "治愈", "放松", "孤独", "感动", "兴奋", "快乐", "安静", "思念"});
            map.put("主题", new String[]{"综艺", "影视原声", "ACG", "儿童", "校园", "游戏", "70后", "80后", "90后", "00后", "网络歌曲", "KTV", "经典", "翻唱", "吉他", "钢琴", "器乐", "榜单"});
        } else if ("kg".equalsIgnoreCase(platformCode)) {
            map.put("主题", new String[]{"精选", "经典", "网络", "DJ热碟", "情歌对唱", "游戏", "舞曲", "KTV", "影视", "翻唱", "ACG", "现场", "综艺", "厂牌音乐", "BGM", "儿童", "器乐演奏", "官方歌单", "草原风", "广场舞"});
            map.put("语种", new String[]{"国语", "英语", "粤语", "日语", "韩语", "闽南语", "小语种", "法语"});
            map.put("风格", new String[]{"流行", "古风", "电子", "民谣", "摇滚", "嘻哈", "后摇", "中国风", "R&B", "古典", "乡村", "爵士", "新世纪", "布鲁斯", "拉丁", "轻音乐", "中国传统", "金属", "雷鬼"});
            map.put("年代", new String[]{"70后", "80后", "90后", "00后"});
            map.put("心情", new String[]{"怀旧", "伤感", "安静", "兴奋", "轻松", "治愈", "快乐", "甜蜜", "寂寞", "感动", "小清新", "励志", "减压", "失恋"});
            map.put("场景", new String[]{"学习", "工作", "通勤", "运动", "校园", "旅途", "咖啡厅", "店铺", "清晨", "下午茶", "夜晚", "睡前", "派对", "宅家", "车载", "夜店", "婚礼"});
        } else if ("kw".equalsIgnoreCase(platformCode)) {
            map.put("专区", new String[]{"经典老歌专区", "网红专区", "DJ专区", "轻音乐专区", "国风专区", "影视专区", "铃声专区", "动漫专区", "新歌首发专区", "K歌专区", "小说专区", "佛乐专区", "儿童专区", "评书专区", "综艺专区", "古典专区", "Vlog音乐"});
            map.put("主题", new String[]{"短视频", "经典", "情歌", "BGM", "演唱会", "游戏", "怀旧", "合唱", "网络", "儿童", "ACG", "影视", "网红", "春节", "翻唱", "轻音助眠"});
            map.put("心情", new String[]{"伤感", "解压", "励志", "开心", "甜蜜", "兴奋", "安静", "思念"});
            map.put("场景", new String[]{"开车", "运动", "睡眠", "跳舞", "学习", "清晨", "KTV", "店铺专用", "校园", "旅行", "工作", "广场舞", "通勤", "宅家", "Citywalk", "露营"});
            map.put("年代", new String[]{"70后", "80后", "90后", "00后"});
            map.put("曲风流派", new String[]{"流行", "DJ", "古风", "佛乐", "轻音乐", "纯音乐", "电子", "喊麦", "3D", "器乐", "摇滚", "民歌", "民谣", "古典", "嘻哈", "乡村", "爵士", "R&B"});
            map.put("语言", new String[]{"华语", "欧美", "日韩", "粤语", "闽南语", "小语种"});
        } else if ("mg".equalsIgnoreCase(platformCode)) {
            map.put("风格", new String[]{"流行", "电子", "国风", "爵士", "乡村", "蓝调", "民谣", "纯音乐", "古典", "摇滚", "嘻哈", "R&B"});
            map.put("语种", new String[]{"国语", "粤语", "英语", "日语", "韩语", "小语种"});
            map.put("场景", new String[]{"运动", "瑜伽", "学习", "睡前安眠", "驾车", "旅行", "夜店", "派对", "下午茶", "读书"});
            map.put("主题", new String[]{"综艺", "KTV金曲", "爱情", "经典老歌", "网络热歌", "儿歌", "70后", "80后", "90后", "广场舞", "红歌", "游戏", "国风", "安静", "青春校园", "小清新", "DJ舞曲", "电视剧", "电影", "翻唱"});
            map.put("心情", new String[]{"幸福", "治愈", "思念", "励志", "欢快", "叛逆", "宣泄", "怀旧", "减压", "寂寞", "忧郁", "伤感", "安静"});
        } else {
            // QQ音乐 (tx)
            map.put("热门", new String[]{"官方歌单", "免费热歌"});
            map.put("主题", new String[]{"KTV金曲", "网络歌曲", "现场音乐", "背景音乐", "经典老歌", "情歌", "儿歌", "ACG", "影视", "综艺", "游戏", "乐器", "城市", "戏曲", "DJ神曲", "MC喊麦", "佛教音乐", "厂牌专区", "人气音乐节", "精品"});
            map.put("场景", new String[]{"夜店", "学习工作", "咖啡馆", "运动", "睡前", "旅行", "跳舞", "派对", "婚礼", "约会", "校园"});
            map.put("心情", new String[]{"伤感", "快乐", "安静", "励志", "治愈", "思念", "甜蜜", "寂寞", "宣泄"});
            map.put("年代", new String[]{"00年代", "90年代", "80年代", "70年代"});
            map.put("流派", new String[]{"流行", "电子", "轻音乐", "民谣", "说唱", "摇滚", "爵士", "R&B", "布鲁斯", "古典", "后摇", "古风", "中国风", "乡村", "金属", "新世纪", "世界音乐", "中国传统"});
            map.put("语种", new String[]{"国语", "英语", "粤语", "韩语", "日语", "小语种", "闽南语", "法语"});
        }
        return map;
    }

    // 3. 各平台支持的排序方式字典（名称与请求参数代码）
    public static String[][] getSortOptions(String platformCode) {
        if ("kg".equalsIgnoreCase(platformCode)) {
            return new String[][]{{"推荐", "5"}, {"最热", "6"}, {"最新", "7"}, {"热藏", "8"}, {"飙升", "9"}};
        } else if ("kw".equalsIgnoreCase(platformCode)) {
            return new String[][]{{"最新", "new"}, {"最热", "hot"}};
        } else if ("wy".equalsIgnoreCase(platformCode)) {
            return new String[][]{{"最热", "hot"}, {"最新", "new"}};
        } else if ("mg".equalsIgnoreCase(platformCode)) {
            return new String[][]{{"推荐", "recommend"}};
        } else {
            // tx
            return new String[][]{{"最热", "5"}, {"最新", "2"}};
        }
    }

    // 4. QQ 音乐 categoryId 映射表
    private static final Map<String, Integer> QQ_CATEGORY_MAP = new HashMap<String, Integer>() {{
        put("官方歌单", 3317); put("免费热歌", 59); put("KTV金曲", 28); put("网络歌曲", 36);
        put("现场音乐", 39); put("背景音乐", 165); put("经典老歌", 167); put("情歌", 169);
        put("儿歌", 170); put("ACG", 76); put("影视", 172); put("综艺", 6);
        put("游戏", 11); put("乐器", 22); put("城市", 15); put("戏曲", 25);
        put("DJ神曲", 81); put("MC喊麦", 21); put("佛教音乐", 14); put("厂牌专区", 13);
        put("人气音乐节", 8); put("精品", 9);
        put("夜店", 44); put("学习工作", 51); put("咖啡馆", 42); put("运动", 49);
        put("睡前", 43); put("旅行", 41); put("跳舞", 46); put("派对", 48);
        put("婚礼", 50); put("约会", 38); put("校园", 40);
        put("伤感", 37); put("快乐", 39); put("安静", 35); put("励志", 33);
        put("治愈", 71); put("思念", 72); put("甜蜜", 74); put("寂寞", 77); put("宣泄", 80);
        put("00年代", 175); put("90年代", 176); put("80年代", 177); put("70年代", 178);
        put("流行", 165); put("电子", 166); put("轻音乐", 167); put("民谣", 168);
        put("说唱", 169); put("摇滚", 170); put("爵士", 171); put("R&B", 172);
        put("布鲁斯", 173); put("古典", 174); put("后摇", 179); put("古风", 180);
        put("中国风", 181); put("乡村", 182); put("金属", 183); put("新世纪", 184);
        put("世界音乐", 185); put("中国传统", 186);
        put("国语", 100); put("英语", 101); put("粤语", 102); put("韩语", 103);
        put("日语", 104); put("小语种", 105); put("闽南语", 106); put("法语", 107);
    }};

    // 5. 抓取歌单广场歌单（增加 sort 排序参数：最新、最热等）
    public static ArrayList<PlaylistInfo> fetchPlaylists(String platform, String tag, String sortCode, int page) {
        ArrayList<PlaylistInfo> list = new ArrayList<PlaylistInfo>();
        try {
            if ("tx".equalsIgnoreCase(platform)) {
                int catId = 10000000;
                if (tag != null && QQ_CATEGORY_MAP.containsKey(tag)) {
                    catId = QQ_CATEGORY_MAP.get(tag);
                }
                int sortId = 5; // 默认最热
                try {
                    if (sortCode != null && sortCode.length() > 0) sortId = Integer.parseInt(sortCode);
                } catch (Exception ignored) {}
                String url = "https://c.y.qq.com/splcloud/fcgi-bin/fcg_get_diss_by_tag.fcg?sin=" + ((page - 1) * 30)
                        + "&ein=" + (page * 30 - 1) + "&categoryId=" + catId + "&sortId=" + sortId
                        + "&format=json&inCharset=utf8&outCharset=utf-8&utf8=1";
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
                String sortVal = (sortCode != null && sortCode.length() > 0) ? sortCode : "6";
                String tagParam = (tag == null || tag.length() == 0 || "全部".equals(tag) || "全部分类".equals(tag)) ? "" : ("&tagname=" + URLEncoder.encode(tag, "UTF-8"));
                String url = "http://mobilecdn.kugou.com/api/v3/tag/specialList?pagesize=30&page=" + page + "&sort=" + sortVal + tagParam;
                String res = httpGet(url);
                if (res != null) {
                    JSONArray arr = new JSONObject(res).getJSONObject("data").getJSONArray("info");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new PlaylistInfo("kg_" + o.getString("specialid"), o.getString("specialname"),
                                o.optString("nickname", "酷狗网友"), o.optString("imgurl", "").replace("{size}", "400"),
                                o.optString("playcount", "0"), "kg"));
                    }
                }
            } else if ("kw".equalsIgnoreCase(platform)) {
                String orderVal = (sortCode != null && sortCode.length() > 0) ? sortCode : "hot";
                String tagParam = (tag == null || tag.length() == 0 || "全部".equals(tag) || "全部分类".equals(tag)) ? "" : ("&name=" + URLEncoder.encode(tag, "UTF-8"));
                String url = "http://wapi.kuwo.cn/api/pc/classify/playlist/getRcmPlayList?pn=" + page + "&rn=30&order=" + orderVal + tagParam;
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    if (root.has("data") && root.getJSONObject("data").has("data")) {
                        JSONArray arr = root.getJSONObject("data").getJSONArray("data");
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            list.add(new PlaylistInfo("kw_" + o.getString("id"), o.getString("name"),
                                    o.optString("uname", "酷我网友"), o.optString("img", ""),
                                    String.valueOf(o.optLong("listencnt", 0)), "kw"));
                        }
                    }
                }
            } else if ("mg".equalsIgnoreCase(platform)) {
                String tagParam = (tag == null || tag.length() == 0 || "全部".equals(tag) || "全部分类".equals(tag)) ? "" : ("&tag=" + URLEncoder.encode(tag, "UTF-8"));
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
                // 网易云
                String orderVal = (sortCode != null && sortCode.length() > 0) ? sortCode : "hot";
                String cat = (tag == null || tag.length() == 0 || "全部".equals(tag) || "全部分类".equals(tag)) ? "全部" : tag;
                String encodedTag = URLEncoder.encode(cat, "UTF-8");
                String url = "https://music.163.com/api/playlist/list?cat=" + encodedTag + "&order=" + orderVal + "&limit=30&offset=" + ((page - 1) * 30);
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

    // 兼容原接口签名
    public static ArrayList<PlaylistInfo> fetchPlaylists(String platform, String tag, int page) {
        return fetchPlaylists(platform, tag, "hot", page);
    }

    // 6. 搜索歌单
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
                                o.optString("nickname", "酷狗网友"), o.optString("imgurl", "").replace("{size}", "400"),
                                o.optString("playcount", "0"), "kg"));
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
                                o.optString("uname", "酷我网友"), o.optString("pic", ""),
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
                                    o.optString("userNick", "咪咕网友"),
                                    o.optString("img", ""),
                                    String.valueOf(o.optLong("playNum", 0)), "mg"));
                        }
                    }
                }
            } else {
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

    // 7. 歌单内歌曲详情
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
                        String artist = "网易歌手";
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

    // 8. 完整排行榜预设清单 (完全对齐 LX Music 排行榜)
    public static String[][] getPresetLeaderboards(String platformCode) {
        if ("wy".equalsIgnoreCase(platformCode)) {
            return new String[][]{
                    {"飙升榜", "19723756"},
                    {"新歌榜", "3779629"},
                    {"原创榜", "2884035"},
                    {"热歌榜", "3778678"},
                    {"说唱榜", "991319590"},
                    {"古典榜", "71385702"},
                    {"电音榜", "1978921795"},
                    {"抖音排行榜", "2250011882"},
                    {"韩语榜", "745956261"},
                    {"日本Oricon榜", "60198"},
                    {"欧美热歌榜", "2809513713"},
                    {"欧美新歌榜", "2809577409"},
                    {"法国 NRJ Vos Hits", "27135204"},
                    {"ACG动画榜", "3001835560"},
                    {"ACG游戏榜", "3001795926"},
                    {"ACG VOCALOID榜", "3001890046"},
                    {"中国新乡村音乐榜", "3112516681"},
                    {"日语榜", "5059632707"},
                    {"摇滚榜", "5059634623"},
                    {"国风榜", "5059642708"},
                    {"潜力爆款榜", "5059644507"},
                    {"民谣榜", "5059661515"},
                    {"听歌识曲榜", "5059644508"},
                    {"网络热歌榜", "5059633707"},
                    {"俄语榜", "5059646506"},
                    {"越南语榜", "5059661516"},
                    {"中文DJ榜", "5059644509"},
                    {"俄罗斯top hit流行榜", "5059642709"},
                    {"泰语榜", "5059661517"},
                    {"BEAT排行榜", "5059633708"},
                    {"编辑推荐榜VOL", "5059646507"},
                    {"LOOK直播歌曲榜", "5059661518"},
                    {"赏音榜", "5059644510"},
                    {"黑胶VIP新歌榜", "5338990334"},
                    {"黑胶VIP热歌榜", "5338990335"},
                    {"黑胶VIP爱搜榜", "5338990336"}
            };
        } else if ("kg".equalsIgnoreCase(platformCode)) {
            return new String[][]{
                    {"TOP500", "8888"},
                    {"飙升榜", "6666"},
                    {"新歌榜", "23784"},
                    {"华语新歌榜", "31308"},
                    {"欧美新歌榜", "31310"},
                    {"韩国新歌榜", "31311"},
                    {"日本新歌榜", "31312"},
                    {"粤语新歌榜", "31313"},
                    {"会员专享榜", "33161"},
                    {"雷达榜", "46910"},
                    {"分享榜", "52144"},
                    {"综艺新歌榜", "24971"},
                    {"酷狗音乐人原创榜", "21101"},
                    {"闽南语榜", "30972"},
                    {"儿歌榜", "30973"},
                    {"美国BillBoard榜", "4681"},
                    {"Beatport电子舞曲榜", "33163"},
                    {"英国单曲榜", "4680"},
                    {"韩国Melon音乐榜", "4672"},
                    {"joox本地热歌榜", "31687"},
                    {"小语种热歌榜", "31314"},
                    {"日本公信榜", "4673"},
                    {"日本SPACE SHOWER榜", "31689"},
                    {"KKBOX风云榜", "31688"},
                    {"越南语榜", "30974"},
                    {"泰语榜", "30975"},
                    {"R&B榜", "30976"},
                    {"摇滚榜", "30977"},
                    {"爵士榜", "30978"},
                    {"乡村音乐榜", "30979"},
                    {"纯音乐榜", "30980"},
                    {"古典榜", "30981"},
                    {"5sing音乐榜", "22603"},
                    {"繁星音乐榜", "21335"},
                    {"古风新歌榜", "33160"}
            };
        } else if ("kw".equalsIgnoreCase(platformCode)) {
            return new String[][]{
                    {"飙升榜", "kw__62"},
                    {"新歌榜", "kw__17"},
                    {"热歌榜", "kw__16"},
                    {"流行趋势榜", "kw__158"},
                    {"现场音乐榜", "kw__186"},
                    {"ACG神曲榜", "kw__187"},
                    {"最强翻唱榜", "kw__185"},
                    {"经典怀旧榜", "kw__26"},
                    {"华语榜", "kw__284"},
                    {"粤语榜", "kw__285"},
                    {"欧美榜", "kw__281"},
                    {"韩语榜", "kw__283"},
                    {"日语榜", "kw__282"},
                    {"会员畅听榜", "kw__278"},
                    {"网红新歌榜", "kw__157"},
                    {"影视金曲榜", "kw__64"},
                    {"DJ嗨歌榜", "kw__154"},
                    {"真声音", "kw__206"},
                    {"Billboard榜", "kw__153"},
                    {"iTunes音乐榜", "kw__49"},
                    {"beatport电音榜", "kw__269"},
                    {"英国UK榜", "kw__151"},
                    {"百大DJ榜", "kw__268"},
                    {"YouTube音乐排行榜", "kw__280"},
                    {"韩国Genie榜", "kw__279"},
                    {"韩国M-net榜", "kw__266"},
                    {"香港电台榜", "kw__150"},
                    {"日本公信榜", "kw__152"},
                    {"腾讯音乐人原创榜", "kw__276"}
            };
        } else if ("mg".equalsIgnoreCase(platformCode)) {
            return new String[][]{
                    {"尖叫榜", "mg__尖叫榜"},
                    {"新歌榜", "mg__新歌榜"},
                    {"热歌榜", "mg__热歌榜"},
                    {"原创榜", "mg__原创榜"},
                    {"影视榜", "mg__影视榜"},
                    {"内地榜", "mg__内地榜"},
                    {"港台榜", "mg__港台榜"},
                    {"欧美榜", "mg__欧美榜"},
                    {"日韩榜", "mg__日韩榜"}
            };
        } else {
            // QQ 音乐 (tx)
            return new String[][]{
                    {"流行指数榜", "tx__4"},
                    {"热歌榜", "tx__26"},
                    {"新歌榜", "tx__27"},
                    {"飙升榜", "tx__62"},
                    {"说唱榜", "tx__58"},
                    {"喜力电音榜", "tx__57"},
                    {"网络歌曲榜", "tx__28"},
                    {"内地榜", "tx__5"},
                    {"欧美榜", "tx__3"},
                    {"香港地区榜", "tx__59"},
                    {"韩国榜", "tx__16"},
                    {"抖快榜", "tx__60"},
                    {"影视金曲榜", "tx__29"},
                    {"日本榜", "tx__17"},
                    {"腾讯音乐人原创榜", "tx__52"},
                    {"K歌金曲榜", "tx__36"},
                    {"台湾地区榜", "tx__61"},
                    {"DJ舞曲榜", "tx__63"},
                    {"综艺新歌榜", "tx__64"},
                    {"国风热歌榜", "tx__65"},
                    {"听歌识曲榜", "tx__67"},
                    {"动漫音乐榜", "tx__66"},
                    {"游戏音乐榜", "tx__70"},
                    {"有声榜", "tx__71"},
                    {"校园音乐人排行榜", "tx__72"}
            };
        }
    }

    // 9. 排行榜歌曲抓取
    public static ArrayList<MainActivity.DisplayEntry> fetchLeaderboardSongs(String platform, String boardKey) {
        ArrayList<MainActivity.DisplayEntry> songs = new ArrayList<MainActivity.DisplayEntry>();
        try {
            if ("wy".equalsIgnoreCase(platform)) {
                return fetchPlaylistSongs("wy_" + boardKey);
            } else if ("tx".equalsIgnoreCase(platform)) {
                String actualKey = boardKey.startsWith("tx__") ? boardKey.substring(4) : boardKey;
                String url = "https://c.y.qq.com/v8/fcg-bin/fcg_v8_toplist_cp.fcg?topid=" + actualKey + "&type=top&format=json&inCharset=utf8&outCharset=utf-8&utf8=1";
                String res = httpGet(url);
                if (res != null) {
                    JSONArray arr = new JSONObject(res).getJSONArray("songlist");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject data = arr.getJSONObject(i).getJSONObject("data");
                        String mid = data.getString("songmid");
                        String title = data.getString("songname");
                        String artist = "QQ歌手";
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
                String actualKey = boardKey.startsWith("kw__") ? boardKey.substring(4) : boardKey;
                String url = "http://kbangserver.kuwo.cn/ksong.s?from=pc&fmt=json&type=bang&data=content&id=" + actualKey + "&pn=0&rn=100";
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
                String actualKey = boardKey.startsWith("mg__") ? boardKey.substring(4) : boardKey;
                String url = "https://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/queryContentbyId.do?columnId=" + actualKey + "&needAll=0";
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

    // 10. 通用 HTTP GET 请求（携带标准模拟请求头）
    public static String httpGet(String urlStr) {
        HttpURLConnection conn = null;
        try {
            TLSSocketFactory.install();
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Referer", "https://y.qq.com/");
            conn.setRequestProperty("Accept", "*/*");
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
