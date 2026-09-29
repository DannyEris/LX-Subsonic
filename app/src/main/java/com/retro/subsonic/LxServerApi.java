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

public class LxServerApi {
    public static final String[] PLATFORM_NAMES = new String[]{"聚合搜索", "QQ音乐", "网易云", "酷狗音乐", "酷我音乐", "咪咕音乐"};
    public static final String[] PLATFORM_CODES = new String[]{"all", "tx", "wy", "kg", "kw", "mg"};

    public static final String[] PLAZA_PLATFORM_NAMES = new String[]{"QQ音乐", "网易云", "酷狗音乐", "酷我音乐", "咪咕音乐"};
    public static final String[] PLAZA_PLATFORM_CODES = new String[]{"tx", "wy", "kg", "kw", "mg"};

    // 各大音乐平台内置预设分类 (匹配截图)
    public static Map<String, String[]> getPresetCategories(String platformCode) {
        Map<String, String[]> cat = new LinkedHashMap<String, String[]>();
        if ("wy".equals(platformCode)) {
            cat.put("热门", new String[]{"全部歌单", "华语", "欧美", "流行", "摇滚", "民谣", "电子", "说唱"});
            cat.put("语种", new String[]{"华语", "欧美", "日语", "韩语", "粤语"});
            cat.put("风格", new String[]{"流行", "摇滚", "民谣", "电子", "舞曲", "说唱", "轻音乐", "爵士", "古典", "古风", "ACG"});
            cat.put("场景", new String[]{"清晨", "夜晚", "学习", "工作", "午休", "驾车", "运动", "旅行", "散步"});
            cat.put("情感", new String[]{"怀旧", "清新", "浪漫", "伤感", "治愈", "放松", "孤独", "励志"});
            cat.put("主题", new String[]{"影视原声", "KTV", "经典", "翻唱", "网易热歌", "吉他", "钢琴"});
        } else if ("tx".equals(platformCode)) {
            cat.put("热门", new String[]{"全部歌单", "官方歌单", "流行", "伤感", "经典", "自驾", "网络"});
            cat.put("语种", new String[]{"国语", "粤语", "英语", "韩语", "日语", "闽南语"});
            cat.put("风格", new String[]{"流行", "摇滚", "电子", "民谣", "说唱", "R&B", "中国风", "轻音乐"});
            cat.put("场景", new String[]{"睡前", "运动", "夜店", "咖啡馆", "工作", "学习", "旅行"});
            cat.put("主题", new String[]{"K歌金曲", "经典老歌", "影视原声", "ACG", "游戏", "网络歌曲"});
        } else if ("kg".equals(platformCode)) {
            cat.put("热门", new String[]{"全部歌单", "推荐", "最热", "最新", "飙升"});
            cat.put("主题", new String[]{"KTV", "经典", "DJ", "网络热歌", "广场舞", "背景音乐"});
            cat.put("语种", new String[]{"华语", "欧美", "粤语", "日韩", "闽南"});
            cat.put("风格", new String[]{"流行", "电子", "摇滚", "民谣", "古风", "说唱"});
            cat.put("心情", new String[]{"伤感", "治愈", "甜蜜", "欢快", "安静", "励志"});
        } else if ("kw".equals(platformCode)) {
            cat.put("热门", new String[]{"全部歌单", "经典专区", "DJ专区", "影视专区", "车载专区"});
            cat.put("主题", new String[]{"流行", "民谣", "网络", "摇滚", "BGM", "伴奏"});
            cat.put("心情", new String[]{"伤感", "治愈", "励志", "开心", "思念", "怀旧"});
            cat.put("场景", new String[]{"开车", "工作", "睡眠", "散步", "学习", "运动"});
        } else if ("mg".equals(platformCode)) {
            cat.put("热门", new String[]{"全部歌单", "华语经典", "热门流行", "咪咕精选"});
            cat.put("语种", new String[]{"华语", "欧美", "日韩", "粤语"});
            cat.put("风格", new String[]{"流行", "摇滚", "民谣", "电子", "古风"});
            cat.put("主题", new String[]{"影视原声", "网络歌曲", "K歌", "动漫"});
        }
        return cat;
    }

    // 各大平台核心排行榜列表 (匹配截图)
    public static String[][] getPresetLeaderboards(String platformCode) {
        if ("wy".equals(platformCode)) {
            return new String[][]{
                {"飙升榜", "wy_top_soar"}, {"新歌榜", "wy_top_new"}, {"原创榜", "wy_top_origin"},
                {"热歌榜", "wy_top_hot"}, {"说唱榜", "wy_top_rap"}, {"古典榜", "wy_top_classic"},
                {"电音榜", "wy_top_electronic"}, {"ACG榜", "wy_top_acg"}, {"韩语榜", "wy_top_korea"},
                {"欧美热歌榜", "wy_top_western"}, {"日本Oricon榜", "wy_top_oricon"}, {"美国Billboard榜", "wy_top_billboard"}
            };
        } else if ("tx".equals(platformCode)) {
            return new String[][]{
                {"流行指数榜", "tx_top_trend"}, {"热歌榜", "tx_top_hot"}, {"新歌榜", "tx_top_new"},
                {"飙升榜", "tx_top_soar"}, {"说唱榜", "tx_top_rap"}, {"电音榜", "tx_top_electronic"},
                {"网络歌曲榜", "tx_top_net"}, {"内地榜", "tx_top_mainland"}, {"欧美榜", "tx_top_western"},
                {"香港地区榜", "tx_top_hk"}, {"韩国榜", "tx_top_korea"}, {"日本榜", "tx_top_japan"},
                {"影视金曲榜", "tx_top_ost"}, {"国风热歌榜", "tx_top_guofeng"}, {"动漫音乐榜", "tx_top_acg"}
            };
        } else if ("kg".equals(platformCode)) {
            return new String[][]{
                {"TOP500", "kg_top_500"}, {"飙升榜", "kg_top_soar"}, {"蜂鸟流行音乐榜", "kg_top_hummingbird"},
                {"网络红歌榜", "kg_top_net"}, {"说唱先锋榜", "kg_top_rap"}, {"电音榜", "kg_top_electronic"},
                {"内地榜", "kg_top_mainland"}, {"香港地区榜", "kg_top_hk"}, {"欧美榜", "kg_top_western"},
                {"民谣榜", "kg_top_folk"}, {"日本榜", "kg_top_japan"}, {"粤语金曲榜", "kg_top_cantonese"}
            };
        } else if ("kw".equals(platformCode)) {
            return new String[][]{
                {"酷我飙升榜", "kw_top_soar"}, {"酷我新歌榜", "kw_top_new"}, {"酷我热歌榜", "kw_top_hot"},
                {"网络歌曲榜", "kw_top_net"}, {"抖音热歌榜", "kw_top_tiktok"}, {"影视金曲榜", "kw_top_ost"},
                {"欧美榜", "kw_top_western"}, {"日韩榜", "kw_top_jk"}
            };
        } else {
            return new String[][]{
                {"咪咕热歌榜", "mg_top_hot"}, {"咪咕新歌榜", "mg_top_new"}, {"咪咕飙升榜", "mg_top_soar"},
                {"影视金曲榜", "mg_top_ost"}, {"网络热歌榜", "mg_top_net"}
            };
        }
    }

    // 执行通用 HTTP GET 请求
    public static String httpGet(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            if (conn instanceof HttpsURLConnection) {
                ((HttpsURLConnection) conn).setSSLSocketFactory(new TLSSocketFactory());
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = reader.readLine()) != null) sb.append(l).append("\n");
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // 获取平台热门搜索
    public static ArrayList<String> fetchHotSearch(String serverUrl, String source) {
        ArrayList<String> list = new ArrayList<String>();
        try {
            String base = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
            String jsonStr = httpGet(base + "/api/hotSearch?source=" + source);
            if (jsonStr != null) {
                JSONObject root = new JSONObject(jsonStr);
                JSONArray arr = root.optJSONArray("data");
                if (arr == null) arr = root.optJSONArray("list");
                if (arr != null) {
                    for (int i = 0; i < arr.length() && i < 15; i++) {
                        Object o = arr.get(i);
                        if (o instanceof String) list.add((String) o);
                        else if (o instanceof JSONObject) list.add(((JSONObject) o).optString("keyword", ((JSONObject) o).optString("title", "")));
                    }
                }
            }
        } catch (Exception ignored) {}
        if (list.isEmpty()) {
            list.add("周杰伦"); list.add("林俊杰"); list.add("陈奕迅"); list.add("邓紫棋");
            list.add("薛之谦"); list.add("告白气球"); list.add("七里香"); list.add("晴天");
        }
        return list;
    }
}
