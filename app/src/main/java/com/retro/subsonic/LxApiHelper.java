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
import java.util.Iterator;
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

    public static class CategoryTag {
        public String name;
        public String id;

        public CategoryTag(String name, String id) {
            this.name = name;
            this.id = id;
        }
    }

    public static class LeaderboardInfo {
        public String id;
        public String name;
        public String coverImg;

        public LeaderboardInfo(String id, String name, String coverImg) {
            this.id = id;
            this.name = name;
            this.coverImg = coverImg;
        }
    }

    public static final String[] PLATFORM_NAMES = new String[]{"聚合搜索", "QQ音乐", "网易云", "酷狗音乐", "酷我音乐", "咪咕音乐"};
    public static final String[] PLATFORM_CODES = new String[]{"all", "tx", "wy", "kg", "kw", "mg"};

    public static final String[] PLAZA_PLATFORM_NAMES = new String[]{"QQ音乐", "网易云", "酷狗音乐", "酷我音乐", "咪咕音乐"};
    public static final String[] PLAZA_PLATFORM_CODES = new String[]{"tx", "wy", "kg", "kw", "mg"};

    private static final Map<String, Map<String, ArrayList<CategoryTag>>> CATEGORY_CACHE = new HashMap<String, Map<String, ArrayList<CategoryTag>>>();
    private static final Map<String, ArrayList<LeaderboardInfo>> LEADERBOARD_CACHE = new HashMap<String, ArrayList<LeaderboardInfo>>();

    public static void clearCategoryCache(String platform) {
        if (platform == null) CATEGORY_CACHE.clear();
        else CATEGORY_CACHE.remove(platform.toLowerCase());
    }

    public static void clearLeaderboardCache(String platform) {
        if (platform == null) LEADERBOARD_CACHE.clear();
        else LEADERBOARD_CACHE.remove(platform.toLowerCase());
    }

    public static ArrayList<String> fetchHotSearch(String platform) {
        ArrayList<String> list = new ArrayList<String>();
        try {
            if ("wy".equalsIgnoreCase(platform)) {
                String res = httpGet("https://music.163.com/api/search/hot/get");
                if (res != null) {
                    JSONArray hots = new JSONObject(res).getJSONObject("result").getJSONArray("hots");
                    for (int i = 0; i < Math.min(hots.length(), 30); i++) {
                        list.add(hots.getJSONObject(i).getString("first").trim());
                    }
                }
            } else if ("kg".equalsIgnoreCase(platform)) {
                String res = httpGet("http://mobilecdn.kugou.com/api/v3/search/hot");
                if (res != null) {
                    JSONArray arr = new JSONObject(res).getJSONObject("data").getJSONArray("info");
                    for (int i = 0; i < Math.min(arr.length(), 30); i++) {
                        list.add(arr.getJSONObject(i).getString("keyword").trim());
                    }
                }
            } else if ("kw".equalsIgnoreCase(platform)) {
                String res = httpGet("http://kuwo.cn/api/www/search/searchKey");
                if (res != null) {
                    JSONArray arr = new JSONObject(res).getJSONArray("data");
                    for (int i = 0; i < Math.min(arr.length(), 30); i++) {
                        String s = arr.getString(i).trim();
                        list.add(s.split("\r\n|\\n")[0].trim());
                    }
                }
            } else if ("mg".equalsIgnoreCase(platform)) {
                String res = httpGet("https://c.musicapp.migu.cn/MIGUM2.0/v1.0/content/search_hot_info.do");
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    if (root.has("hotSearchWord")) {
                        JSONArray arr = root.getJSONArray("hotSearchWord");
                        for (int i = 0; i < Math.min(arr.length(), 30); i++) {
                            list.add(arr.getJSONObject(i).optString("word", "").trim());
                        }
                    }
                }
            } else {
                String res = httpGet("https://c.y.qq.com/splcloud/fcgi-bin/gethotkey.fcg?g_tk=5381&format=json&inCharset=utf8&outCharset=utf-8&utf8=1");
                if (res != null) {
                    JSONArray arr = new JSONObject(res).getJSONObject("data").getJSONArray("hotkey");
                    for (int i = 0; i < Math.min(arr.length(), 30); i++) {
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

    public static String[] getPlatformSorts(String platformCode) {
        if ("kg".equals(platformCode)) {
            return new String[]{"推荐", "最热", "最新", "热藏", "飙升"};
        } else if ("kw".equals(platformCode)) {
            return new String[]{"最热", "最新"};
        } else if ("mg".equals(platformCode)) {
            return new String[]{"最热", "最新", "推荐"};
        } else {
            return new String[]{"最热", "最新"};
        }
    }

    public static Map<String, ArrayList<CategoryTag>> fetchDynamicCategories(String platform) {
        String key = platform.toLowerCase();
        if (CATEGORY_CACHE.containsKey(key)) {
            return CATEGORY_CACHE.get(key);
        }

        Map<String, ArrayList<CategoryTag>> result = new LinkedHashMap<String, ArrayList<CategoryTag>>();

        try {
            if ("tx".equalsIgnoreCase(platform)) {
                String res = httpGet("https://c.y.qq.com/splcloud/fcgi-bin/fcg_get_diss_tag_conf.fcg?g_tk=5381&format=json&inCharset=utf8&outCharset=utf-8");
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray groupArr = root.getJSONObject("data").getJSONArray("categories");
                    for (int i = 0; i < groupArr.length(); i++) {
                        JSONObject group = groupArr.getJSONObject(i);
                        String gName = group.getString("categoryGroupName");
                        ArrayList<CategoryTag> tags = new ArrayList<CategoryTag>();
                        JSONArray items = group.getJSONArray("items");
                        for (int j = 0; j < items.length(); j++) {
                            JSONObject item = items.getJSONObject(j);
                            tags.add(new CategoryTag(item.getString("categoryName"), String.valueOf(item.getInt("categoryId"))));
                        }
                        result.put(gName, tags);
                    }
                }
            } else if ("wy".equalsIgnoreCase(platform)) {
                String res = httpGet("https://music.163.com/api/playlist/catalogue");
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONObject categories = root.getJSONObject("categories");
                    JSONArray sub = root.getJSONArray("sub");

                    Map<Integer, String> groupMap = new HashMap<Integer, String>();
                    Iterator<?> it = categories.keys();
                    while (it.hasNext()) {
                        String gIdStr = (String) it.next();
                        String gName = categories.getString(gIdStr);
                        groupMap.put(Integer.parseInt(gIdStr), gName);
                        result.put(gName, new ArrayList<CategoryTag>());
                    }

                    for (int i = 0; i < sub.length(); i++) {
                        JSONObject s = sub.getJSONObject(i);
                        int catType = s.getInt("category");
                        String gName = groupMap.get(catType);
                        if (gName != null && result.containsKey(gName)) {
                            String name = s.getString("name");
                            result.get(gName).add(new CategoryTag(name, name));
                        }
                    }
                }
            } else if ("kg".equalsIgnoreCase(platform)) {
                String res = httpGet("http://mobilecdn.kugou.com/api/v3/tag/list?pid=0&apiversion=4");
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray infoArr = root.getJSONObject("data").getJSONArray("info");
                    for (int i = 0; i < infoArr.length(); i++) {
                        JSONObject group = infoArr.getJSONObject(i);
                        String gName = group.getString("categoryname");
                        ArrayList<CategoryTag> tags = new ArrayList<CategoryTag>();
                        if (group.has("tags")) {
                            JSONArray items = group.getJSONArray("tags");
                            for (int j = 0; j < items.length(); j++) {
                                JSONObject item = items.getJSONObject(j);
                                tags.add(new CategoryTag(item.getString("tagname"), String.valueOf(item.getInt("tagid"))));
                            }
                        }
                        result.put(gName, tags);
                    }
                }
            } else if ("kw".equalsIgnoreCase(platform)) {
                String res = httpGet("http://wapi.kuwo.cn/api/pc/classify/playlist/getTagList");
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray groupArr = root.getJSONArray("data");
                    for (int i = 0; i < groupArr.length(); i++) {
                        JSONObject group = groupArr.getJSONObject(i);
                        String gName = group.getString("name");
                        ArrayList<CategoryTag> tags = new ArrayList<CategoryTag>();
                        JSONArray items = group.getJSONArray("data");
                        for (int j = 0; j < items.length(); j++) {
                            JSONObject item = items.getJSONObject(j);
                            tags.add(new CategoryTag(item.getString("name"), item.getString("id")));
                        }
                        result.put(gName, tags);
                    }
                }
            } else if ("mg".equalsIgnoreCase(platform)) {
                String res = httpGet("https://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/queryTagGroup.do");
                if (res != null && res.contains("tagGroups")) {
                    JSONObject root = new JSONObject(res);
                    JSONArray groupArr = root.getJSONObject("columnInfo").getJSONArray("tagGroups");
                    for (int i = 0; i < groupArr.length(); i++) {
                        JSONObject group = groupArr.getJSONObject(i);
                        String gName = group.optString("tagGroupName", "精选");
                        ArrayList<CategoryTag> tags = new ArrayList<CategoryTag>();
                        if (group.has("tags")) {
                            JSONArray items = group.getJSONArray("tags");
                            for (int j = 0; j < items.length(); j++) {
                                JSONObject item = items.getJSONObject(j);
                                tags.add(new CategoryTag(item.getString("tagName"), item.optString("tagId", item.getString("tagName"))));
                            }
                        }
                        result.put(gName, tags);
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (result.isEmpty()) {
            Map<String, String[]> preset = getFallbackCategories(platform);
            for (Map.Entry<String, String[]> entry : preset.entrySet()) {
                ArrayList<CategoryTag> tags = new ArrayList<CategoryTag>();
                for (String t : entry.getValue()) tags.add(new CategoryTag(t, t));
                result.put(entry.getKey(), tags);
            }
        } else {
            CATEGORY_CACHE.put(key, result);
        }

        return result;
    }

    private static Map<String, String[]> getFallbackCategories(String platformCode) {
        Map<String, String[]> cat = new LinkedHashMap<String, String[]>();
        if ("wy".equals(platformCode)) {
            cat.put("语种", new String[]{"华语", "欧美", "日语", "韩语", "粤语"});
            cat.put("风格", new String[]{"流行", "摇滚", "民谣", "电子", "舞曲", "说唱", "轻音乐", "爵士", "古风", "ACG"});
            cat.put("场景", new String[]{"清晨", "夜晚", "学习", "工作", "驾车", "运动", "旅行"});
            cat.put("情感", new String[]{"怀旧", "清新", "浪漫", "伤感", "治愈", "放松", "快乐"});
            cat.put("主题", new String[]{"影视原声", "ACG", "经典", "翻唱", "KTV", "网络歌曲"});
        } else if ("tx".equals(platformCode)) {
            cat.put("热门", new String[]{"官方歌单", "免费热歌"});
            cat.put("语种", new String[]{"国语", "粤语", "英语", "韩语", "日语", "闽南语"});
            cat.put("流派", new String[]{"流行", "电子", "轻音乐", "民谣", "说唱", "摇滚", "古风"});
            cat.put("场景", new String[]{"夜店", "学习工作", "咖啡馆", "运动", "睡前", "旅行"});
            cat.put("心情", new String[]{"伤感", "快乐", "安静", "治愈", "甜蜜"});
        } else {
            cat.put("精选", new String[]{"流行", "热歌", "经典", "网络", "车载", "古风", "民谣", "轻音乐"});
            cat.put("语种", new String[]{"华语", "欧美", "粤语", "日韩"});
        }
        return cat;
    }

    public static ArrayList<LeaderboardInfo> fetchDynamicLeaderboards(String platform) {
        String key = platform.toLowerCase();
        if (LEADERBOARD_CACHE.containsKey(key)) {
            return LEADERBOARD_CACHE.get(key);
        }

        ArrayList<LeaderboardInfo> list = new ArrayList<LeaderboardInfo>();

        try {
            if ("tx".equalsIgnoreCase(platform)) {
                String reqJson = "{\"comm\":{\"cv\":4747474,\"ct\":24,\"format\":\"json\",\"inCharset\":\"utf-8\",\"outCharset\":\"utf-8\",\"platform\":\"yqq.json\"},\"toplist\":{\"module\":\"musicToplist.ToplistInfoServer\",\"method\":\"GetAll\",\"param\":{}}}";
                String url = "https://u.y.qq.com/cgi-bin/musicu.fcg?format=json&inCharset=utf8&outCharset=utf-8&data=" + URLEncoder.encode(reqJson, "UTF-8");
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray groups = root.getJSONObject("toplist").getJSONObject("data").getJSONArray("group");
                    for (int i = 0; i < groups.length(); i++) {
                        JSONArray topList = groups.getJSONObject(i).getJSONArray("toplist");
                        for (int j = 0; j < topList.length(); j++) {
                            JSONObject item = topList.getJSONObject(j);
                            list.add(new LeaderboardInfo(String.valueOf(item.getInt("topId")), item.getString("title"), item.optString("headPicUrl", "")));
                        }
                    }
                }
            } else if ("wy".equalsIgnoreCase(platform)) {
                String res = httpGet("https://music.163.com/api/toplist");
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONArray("list");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new LeaderboardInfo(String.valueOf(o.getLong("id")), o.getString("name"), o.optString("coverImgUrl", "")));
                    }
                }
            } else if ("kg".equalsIgnoreCase(platform)) {
                String res = httpGet("http://mobilecdn.kugou.com/api/v3/rank/list?version=9108&plat=0&showtype=2&parentid=0&apiversion=6");
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONObject("data").getJSONArray("info");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new LeaderboardInfo(String.valueOf(o.getInt("rankid")), o.getString("rankname"), o.optString("bannerurl", "").replace("{size}", "400")));
                    }
                }
            } else if ("kw".equalsIgnoreCase(platform)) {
                String res = httpGet("http://kbangserver.kuwo.cn/ksong.s?from=pc&fmt=json&type=bang&data=index");
                if (res != null) {
                    JSONArray groups = new JSONArray(res);
                    for (int i = 0; i < groups.length(); i++) {
                        JSONObject g = groups.getJSONObject(i);
                        if (g.has("list")) {
                            JSONArray arr = g.getJSONArray("list");
                            for (int j = 0; j < arr.length(); j++) {
                                JSONObject o = arr.getJSONObject(j);
                                list.add(new LeaderboardInfo(o.getString("sourceid"), o.getString("name"), o.optString("pic", "")));
                            }
                        }
                    }
                }
            } else if ("mg".equalsIgnoreCase(platform)) {
                list.add(new LeaderboardInfo("27553319", "咪咕热歌榜", ""));
                list.add(new LeaderboardInfo("27186466", "咪咕新歌榜", ""));
                list.add(new LeaderboardInfo("27553258", "咪咕飙升榜", ""));
                list.add(new LeaderboardInfo("27553408", "影视金曲榜", ""));
                list.add(new LeaderboardInfo("27553380", "网络热歌榜", ""));
                list.add(new LeaderboardInfo("27553423", "欧美热歌榜", ""));
                list.add(new LeaderboardInfo("27553435", "日韩音乐榜", ""));
                list.add(new LeaderboardInfo("27553450", "国风新韵榜", ""));
                list.add(new LeaderboardInfo("27553462", "说唱音乐榜", ""));
                list.add(new LeaderboardInfo("27553474", "DJ嗨歌榜", ""));
                list.add(new LeaderboardInfo("27553486", "KTV点唱榜", ""));
            }
        } catch (Throwable ignored) {}

        if (!list.isEmpty()) {
            LEADERBOARD_CACHE.put(key, list);
        } else {
            list.add(new LeaderboardInfo("hot", "热歌榜", ""));
            list.add(new LeaderboardInfo("new", "新歌榜", ""));
            list.add(new LeaderboardInfo("soar", "飙升榜", ""));
        }

        return list;
    }

    public static ArrayList<PlaylistInfo> fetchPlaylists(String platform, String tagId, String tagName, String sort, int page) {
        ArrayList<PlaylistInfo> list = new ArrayList<PlaylistInfo>();
        try {
            if ("tx".equalsIgnoreCase(platform)) {
                String catId = (tagId != null && tagId.length() > 0 && !"全部".equals(tagId)) ? tagId : "10000000";
                int sortId = "最新".equals(sort) ? 2 : 5;
                String url = "https://c.y.qq.com/splcloud/fcgi-bin/fcg_get_diss_by_tag.fcg?sin=" + ((page - 1) * 30) + "&ein=" + (page * 30 - 1) + "&categoryId=" + catId + "&sortId=" + sortId + "&format=json&inCharset=utf8&outCharset=utf-8&utf8=1";
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    if (root.has("data") && root.getJSONObject("data").has("list")) {
                        JSONArray arr = root.getJSONObject("data").getJSONArray("list");
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            list.add(new PlaylistInfo("tx_" + o.getString("dissid"), o.getString("dissname"),
                                    o.optJSONObject("creator") != null ? o.optJSONObject("creator").optString("name", "QQ音乐") : "QQ音乐",
                                    o.optString("imgurl", ""), String.valueOf(o.optLong("listennum", 0)), "tx"));
                        }
                    }
                }
            } else if ("kg".equalsIgnoreCase(platform)) {
                int sortVal = 2;
                if ("推荐".equals(sort)) sortVal = 5;
                else if ("最新".equals(sort)) sortVal = 1;
                else if ("热藏".equals(sort)) sortVal = 3;
                else if ("飙升".equals(sort)) sortVal = 4;

                String tagParam = "";
                if (tagId != null && tagId.length() > 0 && !"全部".equals(tagId)) {
                    tagParam = "&tagid=" + tagId;
                } else if (tagName != null && tagName.length() > 0 && !"全部".equals(tagName)) {
                    tagParam = "&tagname=" + URLEncoder.encode(tagName, "UTF-8");
                }

                String url = "http://mobilecdn.kugou.com/api/v3/tag/specialList?pagesize=30&page=" + page + "&sort=" + sortVal + tagParam;
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    if (root.has("data") && root.getJSONObject("data").has("info")) {
                        JSONArray arr = root.getJSONObject("data").getJSONArray("info");
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            list.add(new PlaylistInfo("kg_" + o.getString("specialid"), o.getString("specialname"),
                                    o.optString("nickname", "酷狗音乐"), o.optString("imgurl", "").replace("{size}", "400"),
                                    o.optString("playcount", ""), "kg"));
                        }
                    }
                }
            } else if ("kw".equalsIgnoreCase(platform)) {
                String orderVal = "最新".equals(sort) ? "new" : "hot";
                String url;
                if (tagId != null && tagId.length() > 0 && !"全部".equals(tagId)) {
                    url = "http://wapi.kuwo.cn/api/pc/classify/playlist/getTagPlayList?pn=" + page + "&rn=30&id=" + tagId + "&order=" + orderVal;
                } else {
                    url = "http://wapi.kuwo.cn/api/pc/classify/playlist/getRcmPlayList?pn=" + page + "&rn=30&order=" + orderVal;
                }
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
                String tagParam = (tagName == null || tagName.length() == 0 || "全部".equals(tagName)) ? "" : ("&tag=" + URLEncoder.encode(tagName, "UTF-8"));
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
                String cat = (tagName == null || tagName.length() == 0 || "全部".equals(tagName)) ? "全部" : tagName;
                String orderVal = "最新".equals(sort) ? "new" : "hot";
                String encodedTag = URLEncoder.encode(cat, "UTF-8");
                String url = "https://music.163.com/api/playlist/list?cat=" + encodedTag + "&order=" + orderVal + "&limit=30&offset=" + ((page - 1) * 30);
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    if (root.has("playlists")) {
                        JSONArray arr = root.getJSONArray("playlists");
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            list.add(new PlaylistInfo("wy_" + o.getLong("id"), o.getString("name"),
                                    o.optJSONObject("creator") != null ? o.optJSONObject("creator").optString("nickname", "网易云") : "网易云",
                                    o.optString("coverImgUrl", ""), String.valueOf(o.optLong("playCount", 0)), "wy"));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return list;
    }

    public static ArrayList<PlaylistInfo> fetchPlaylists(String platform, String tag, String sort, int page) {
        return fetchPlaylists(platform, tag, tag, sort, page);
    }

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
                                o.optJSONObject("creator") != null ? o.optJSONObject("creator").optString("name", "QQ音乐") : "QQ音乐",
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
                                    o.optString("name", "咪咕歌单"), o.optString("userNick", "咪咕音乐"),
                                    o.optString("img", ""), String.valueOf(o.optLong("playNum", 0)), "mg"));
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
                                o.optJSONObject("creator") != null ? o.optJSONObject("creator").optString("nickname", "网易云") : "网易云",
                                o.optString("coverImgUrl", ""), String.valueOf(o.optLong("playCount", 0)), "wy"));
                    }
                }
            }
        } catch (Throwable ignored) {}
        return list;
    }

    public static ArrayList<MainActivity.DisplayEntry> fetchPlaylistSongs(String rawPlaylistId) {
        return fetchPlaylistSongs(rawPlaylistId, "320K MP3");
    }

    public static ArrayList<MainActivity.DisplayEntry> fetchPlaylistSongs(String rawPlaylistId, String defaultQuality) {
        ArrayList<MainActivity.DisplayEntry> songs = new ArrayList<MainActivity.DisplayEntry>();
        String qualityLabel = (defaultQuality != null && defaultQuality.length() > 0) ? defaultQuality : "320K MP3";
        int bitRateNumeric = qualityLabel.contains("FLAC") ? 999 : (qualityLabel.contains("128") ? 128 : 320);

        try {
            if (rawPlaylistId.startsWith("wy_")) {
                String id = rawPlaylistId.substring(3);
                String res = httpGet("https://music.163.com/api/playlist/detail?id=" + id);
                if (res == null || !res.contains("\"tracks\"")) {
                    res = httpGet("https://music.163.com/api/v6/playlist/detail?id=" + id);
                }
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    JSONObject pl = root.optJSONObject("playlist");
                    if (pl == null) pl = root.optJSONObject("result");
                    if (pl != null && pl.has("tracks")) {
                        JSONArray tracks = pl.getJSONArray("tracks");
                        for (int i = 0; i < tracks.length(); i++) {
                            JSONObject t = tracks.getJSONObject(i);
                            String songId = "wy_" + t.getLong("id");
                            String title = t.getString("name");
                            String artist = "群星";
                            if (t.has("ar") && t.getJSONArray("ar").length() > 0) {
                                artist = t.getJSONArray("ar").getJSONObject(0).getString("name");
                            } else if (t.has("artists") && t.getJSONArray("artists").length() > 0) {
                                artist = t.getJSONArray("artists").getJSONObject(0).getString("name");
                            }
                            String cover = null;
                            if (t.optJSONObject("al") != null) cover = t.optJSONObject("al").optString("picUrl", null);
                            else if (t.optJSONObject("album") != null) cover = t.optJSONObject("album").optString("picUrl", null);
                            songs.add(new MainActivity.DisplayEntry(songId, title, artist, artist + " [" + qualityLabel + "]", cover, qualityLabel, true, bitRateNumeric));
                        }
                    }
                }
            } else if (rawPlaylistId.startsWith("kg_")) {
                String id = rawPlaylistId.substring(3);
                String res = httpGet("http://mobilecdn.kugou.com/api/v3/special/song?specialid=" + id + "&pagesize=100&page=1");
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    if (root.has("data") && root.getJSONObject("data").has("info")) {
                        JSONArray arr = root.getJSONObject("data").getJSONArray("info");
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            String filename = o.optString("filename", "");
                            String title = filename, artist = "酷狗歌手";
                            if (filename.contains(" - ")) {
                                String[] p = filename.split(" - ", 2);
                                artist = p[0].trim(); title = p[1].trim();
                            }
                            String hash = o.optString("hash", "");
                            songs.add(new MainActivity.DisplayEntry("kg_" + hash, title, artist, artist + " [" + qualityLabel + "]", null, qualityLabel, true, bitRateNumeric));
                        }
                    }
                }
            } else if (rawPlaylistId.startsWith("tx_")) {
                String id = rawPlaylistId.substring(3);
                String res = httpGet("https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg?type=1&json=1&utf8=1&onlysong=0&disstid=" + id + "&format=json&inCharset=utf8&outCharset=utf-8&utf8=1");
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    if (root.has("cdlist") && root.getJSONArray("cdlist").length() > 0) {
                        JSONArray songlist = root.getJSONArray("cdlist").getJSONObject(0).getJSONArray("songlist");
                        for (int i = 0; i < songlist.length(); i++) {
                            JSONObject s = songlist.getJSONObject(i);
                            String songmid = s.getString("songmid");
                            String title = s.getString("songname");
                            String artist = "QQ歌手";
                            if (s.has("singer") && s.getJSONArray("singer").length() > 0) {
                                artist = s.getJSONArray("singer").getJSONObject(0).getString("name");
                            }
                            songs.add(new MainActivity.DisplayEntry("tx_" + songmid, title, artist, artist + " [" + qualityLabel + "]", null, qualityLabel, true, bitRateNumeric));
                        }
                    }
                }
            } else if (rawPlaylistId.startsWith("kw_")) {
                String id = rawPlaylistId.substring(3);
                String res = httpGet("http://nplserver.kuwo.cn/pl.s?content=list&id=" + id + "&pn=0&rn=100");
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    if (root.has("musiclist")) {
                        JSONArray musiclist = root.getJSONArray("musiclist");
                        for (int i = 0; i < musiclist.length(); i++) {
                            JSONObject o = musiclist.getJSONObject(i);
                            String songId = o.getString("id");
                            String title = o.getString("name");
                            String artist = o.optString("artist", "酷我歌手");
                            songs.add(new MainActivity.DisplayEntry("kw_" + songId, title, artist, artist + " [" + qualityLabel + "]", null, qualityLabel, true, bitRateNumeric));
                        }
                    }
                }
            } else if (rawPlaylistId.startsWith("mg_")) {
                String id = rawPlaylistId.substring(3);
                String res = httpGet("https://app.c.nf.migu.cn/MIGUM2.0/v1.0/user/queryMusicListSongs.do?musicListId=" + id + "&pageNo=1&pageSize=100");
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    if (root.has("list")) {
                        JSONArray listArr = root.getJSONArray("list");
                        for (int i = 0; i < listArr.length(); i++) {
                            JSONObject o = listArr.getJSONObject(i);
                            String songId = o.optString("songId", "");
                            String title = o.optString("songName", "");
                            String artist = o.optString("singerName", "咪咕歌手");
                            songs.add(new MainActivity.DisplayEntry("mg_" + songId, title, artist, artist + " [" + qualityLabel + "]", null, qualityLabel, true, bitRateNumeric));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return songs;
    }

    public static ArrayList<MainActivity.DisplayEntry> fetchLeaderboardSongs(String platform, String boardKey) {
        return fetchLeaderboardSongs(platform, boardKey, "320K MP3");
    }

    public static ArrayList<MainActivity.DisplayEntry> fetchLeaderboardSongs(String platform, String boardKey, String defaultQuality) {
        ArrayList<MainActivity.DisplayEntry> songs = new ArrayList<MainActivity.DisplayEntry>();
        String qualityLabel = (defaultQuality != null && defaultQuality.length() > 0) ? defaultQuality : "320K MP3";
        int bitRateNumeric = qualityLabel.contains("FLAC") ? 999 : (qualityLabel.contains("128") ? 128 : 320);

        try {
            if ("wy".equalsIgnoreCase(platform)) {
                return fetchPlaylistSongs("wy_" + boardKey, qualityLabel);
            } else if ("tx".equalsIgnoreCase(platform)) {
                String reqJson = "{\"detail\":{\"module\":\"musicToplist.ToplistInfoServer\",\"method\":\"GetDetail\",\"param\":{\"topId\":" + boardKey + ",\"offset\":0,\"num\":100}}}";
                String url = "https://u.y.qq.com/cgi-bin/musicu.fcg?format=json&inCharset=utf8&outCharset=utf-8&utf8=1&data=" + URLEncoder.encode(reqJson, "UTF-8");
                String res = httpGet(url);
                if (res != null && res.contains("\"songInfoList\"")) {
                    JSONObject root = new JSONObject(res);
                    JSONArray arr = root.getJSONObject("detail").getJSONObject("data").getJSONArray("songInfoList");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject s = arr.getJSONObject(i);
                        String mid = s.getString("mid");
                        String title = s.getString("name");
                        String artist = "群星";
                        if (s.has("singer") && s.getJSONArray("singer").length() > 0) {
                            artist = s.getJSONArray("singer").getJSONObject(0).getString("name");
                        }
                        songs.add(new MainActivity.DisplayEntry("tx_" + mid, title, artist, artist + " [" + qualityLabel + "]", null, qualityLabel, true, bitRateNumeric));
                    }
                }
            } else if ("kg".equalsIgnoreCase(platform)) {
                String url = "http://mobilecdn.kugou.com/api/v3/rank/song?rankid=" + boardKey + "&page=1&pagesize=100";
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    if (root.has("data") && root.getJSONObject("data").has("info")) {
                        JSONArray arr = root.getJSONObject("data").getJSONArray("info");
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            String filename = o.optString("filename", "");
                            String title = filename, artist = "酷狗歌手";
                            if (filename.contains(" - ")) {
                                String[] p = filename.split(" - ", 2);
                                artist = p[0].trim(); title = p[1].trim();
                            }
                            String hash = o.optString("hash", "");
                            songs.add(new MainActivity.DisplayEntry("kg_" + hash, title, artist, artist + " [" + qualityLabel + "]", null, qualityLabel, true, bitRateNumeric));
                        }
                    }
                }
            } else if ("kw".equalsIgnoreCase(platform)) {
                String url = "http://kbangserver.kuwo.cn/ksong.s?from=pc&fmt=json&type=bang&data=content&id=" + boardKey + "&pn=0&rn=100";
                String res = httpGet(url);
                if (res != null) {
                    JSONObject root = new JSONObject(res);
                    if (root.has("musiclist")) {
                        JSONArray arr = root.getJSONArray("musiclist");
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            String id = o.getString("id");
                            String title = o.getString("name");
                            String artist = o.optString("artist", "酷我歌手");
                            songs.add(new MainActivity.DisplayEntry("kw_" + id, title, artist, artist + " [" + qualityLabel + "]", null, qualityLabel, true, bitRateNumeric));
                        }
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
                                songs.add(new MainActivity.DisplayEntry("mg_" + songId, title, artist, artist + " [" + qualityLabel + "]", null, qualityLabel, true, bitRateNumeric));
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return songs;
    }

    public static String httpGet(String urlStr) {
        return httpGetWithRedirect(urlStr, 0);
    }

    private static String httpGetWithRedirect(String urlStr, int depth) {
        if (depth > 5) return null;
        HttpURLConnection conn = null;
        try {
            TLSSocketFactory.install();
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false); // 手动接管 301/302，确保跨 HTTP/HTTPS 跳转正确追踪
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(9000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36");

            if (urlStr.contains("163.com")) {
                conn.setRequestProperty("Referer", "https://music.163.com/");
            } else if (urlStr.contains("qq.com")) {
                conn.setRequestProperty("Referer", "https://y.qq.com/");
            } else if (urlStr.contains("kugou.com")) {
                conn.setRequestProperty("Referer", "http://www.kugou.com/");
            } else if (urlStr.contains("kuwo.cn")) {
                conn.setRequestProperty("Referer", "http://www.kuwo.cn/");
                conn.setRequestProperty("csrf", "HH12345678");
                conn.setRequestProperty("Cookie", "kw_token=HH12345678");
            } else if (urlStr.contains("migu.cn")) {
                conn.setRequestProperty("Referer", "https://music.migu.cn/");
            }

            if (conn instanceof HttpsURLConnection) {
                ((HttpsURLConnection) conn).setSSLSocketFactory(new TLSSocketFactory());
            }

            int code = conn.getResponseCode();

            if (code == 301 || code == 302 || code == 303 || code == 307) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location != null && location.length() > 0) {
                    URL nextUrl = new URL(url, location);
                    return httpGetWithRedirect(nextUrl.toString(), depth + 1);
                }
                return null;
            }

            if (code == 200) {
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
