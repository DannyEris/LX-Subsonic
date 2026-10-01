package com.retro.subsonic;

import android.os.Build;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

public class LxApiHelper {
    private static final String TAG = "LxApiHelper";

    // 默认 User-Agent
    private static final String UA_MOBILE = "Mozilla/5.0 (Linux; U; Android 4.2.2; zh-cn) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30";
    private static final String UA_KUWO = "kwplayer_ar_8.5.5.0";
    private static final String UA_PC = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36";

    // 数据模型
    public static class SongItem {
        public String id;
        public String title;
        public String artist;
        public String album;
        public String cover;
        public String url;
        public String source; // "wy", "kg", "tx", "kw"
        public long duration;
        public String hash;   // 酷狗专用
        public String songmid; // QQ专用
    }

    public static class PlaylistItem {
        public String id;
        public String title;
        public String cover;
        public String playCount;
        public String source;
        public int songCount;
    }

    public static class CategoryTag {
        public String id;
        public String name;

        public CategoryTag(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static class LyricItem {
        public String source;
        public String songId;
        public String title;
        public String artist;
        public String album;
        public String accessKey; // 酷狗专用
    }

    /**
     * 针对 Android 4.2 建立网络连接，处理 TLS 握手及超时
     */
    private static HttpURLConnection openConnection(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (conn instanceof HttpsURLConnection) {
            if (Build.VERSION.SDK_INT <= 19) {
                try {
                    TLSSocketFactory tlsSocketFactory = new TLSSocketFactory();
                    ((HttpsURLConnection) conn).setSSLSocketFactory(tlsSocketFactory);
                } catch (Exception e) {
                    Log.e(TAG, "TLS Socket Factory Error", e);
                }
            }
        }
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("Accept-Charset", "UTF-8");
        return conn;
    }

    /**
     * 统一读取输入流
     */
    private static String readStream(InputStream in) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        reader.close();
        return sb.toString();
    }

    /**
     * 问题2修复：全局封面 URL 清洗，替换占位符并对 Android 4.2 降级 HTTP
     */
    public static String fixCoverUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "";
        }
        String fixed = url.trim();

        // 酷狗占位符替换
        if (fixed.contains("{size}")) {
            fixed = fixed.replace("{size}", "400");
        }
        // 酷我尺寸替换
        if (fixed.contains("/100/")) {
            fixed = fixed.replace("/100/", "/300/");
        }

        // 老旧 Android 4.2 对近代 ECC / Let's Encrypt 证书无法握手，降级为 HTTP 加速加载
        if (fixed.startsWith("https://imge.kugou.com")) {
            fixed = "http://" + fixed.substring(8);
        } else if (fixed.startsWith("https://img1.kuwo.cn") || fixed.startsWith("https://img2.kuwo.cn") ||
                fixed.startsWith("https://img3.kuwo.cn") || fixed.startsWith("https://img4.kuwo.cn") ||
                fixed.startsWith("https://star.kuwo.cn") || fixed.startsWith("https://kwimg")) {
            fixed = "http://" + fixed.substring(8);
        } else if (fixed.startsWith("https://y.gtimg.cn")) {
            fixed = "http://" + fixed.substring(8);
        } else if (fixed.startsWith("https://p1.music.126.net") || fixed.startsWith("https://p2.music.126.net") ||
                fixed.startsWith("https://p3.music.126.net") || fixed.startsWith("https://p4.music.126.net")) {
            fixed = "http://" + fixed.substring(8);
        }
        return fixed;
    }

    /**
     * 解决酷狗播放时无封面的情况：通过 Hash 补全歌曲封面
     */
    public static String getKugouSongCover(String hash) {
        if (hash == null || hash.isEmpty()) return "";
        try {
            String url = "http://m.kugou.com/app/i/getSongInfo.php?cmd=playInfo&hash=" + hash;
            HttpURLConnection conn = openConnection(url);
            conn.setRequestProperty("User-Agent", UA_MOBILE);
            if (conn.getResponseCode() == 200) {
                String resp = readStream(conn.getInputStream());
                JSONObject json = new JSONObject(resp);
                String imgUrl = json.optString("imgUrl");
                if (imgUrl.isEmpty()) {
                    imgUrl = json.optString("album_img");
                }
                return fixCoverUrl(imgUrl);
            }
        } catch (Exception e) {
            Log.e(TAG, "getKugouSongCover error", e);
        }
        return "";
    }

    /**
     * 获取歌单分类标签
     */
    public static List<CategoryTag> getCategories(String source) {
        List<CategoryTag> list = new ArrayList<CategoryTag>();
        if ("wy".equals(source)) {
            list.add(new CategoryTag("全部", "全部"));
            list.add(new CategoryTag("华语", "华语"));
            list.add(new CategoryTag("流行", "流行"));
            list.add(new CategoryTag("摇滚", "摇滚"));
            list.add(new CategoryTag("民谣", "民谣"));
            list.add(new CategoryTag("电子", "电子"));
            list.add(new CategoryTag("轻音乐", "轻音乐"));
            list.add(new CategoryTag("ACG", "ACG"));
            list.add(new CategoryTag("怀旧", "怀旧"));
        } else if ("kg".equals(source)) {
            list.add(new CategoryTag("0", "推荐"));
            list.add(new CategoryTag("666", "华语"));
            list.add(new CategoryTag("888", "流行"));
            list.add(new CategoryTag("123", "经典"));
            list.add(new CategoryTag("456", "伤感"));
            list.add(new CategoryTag("789", "纯音乐"));
            list.add(new CategoryTag("101", "网络"));
            list.add(new CategoryTag("202", "轻音乐"));
        } else if ("tx".equals(source)) {
            list.add(new CategoryTag("10000000", "全部"));
            list.add(new CategoryTag("165", "华语"));
            list.add(new CategoryTag("167", "流行"));
            list.add(new CategoryTag("169", "摇滚"));
            list.add(new CategoryTag("171", "民谣"));
            list.add(new CategoryTag("173", "轻音乐"));
        } else if ("kw".equals(source)) {
            list.add(new CategoryTag("hot", "热门"));
            list.add(new CategoryTag("new", "最新"));
            list.add(new CategoryTag("184", "华语"));
            list.add(new CategoryTag("187", "经典"));
            list.add(new CategoryTag("186", "网络"));
            list.add(new CategoryTag("621", "伤感"));
            list.add(new CategoryTag("146", "轻音乐"));
        }
        return list;
    }

    /**
     * 歌单广场：获取歌单列表
     */
    public static List<PlaylistItem> getPlaylists(String source, String tag, int page, int pageSize) {
        List<PlaylistItem> result = new ArrayList<PlaylistItem>();
        try {
            if ("wy".equals(source)) {
                String cat = (tag == null || tag.isEmpty() || "全部".equals(tag)) ? "全部" : tag;
                int offset = (page > 0 ? page - 1 : 0) * pageSize;
                String url = "http://music.163.com/api/playlist/list?cat=" + URLEncoder.encode(cat, "UTF-8")
                        + "&order=hot&offset=" + offset + "&limit=" + pageSize;
                HttpURLConnection conn = openConnection(url);
                conn.setRequestProperty("User-Agent", UA_PC);
                if (conn.getResponseCode() == 200) {
                    JSONObject json = new JSONObject(readStream(conn.getInputStream()));
                    JSONArray arr = json.optJSONArray("playlists");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            PlaylistItem item = new PlaylistItem();
                            item.source = "wy";
                            item.id = String.valueOf(obj.optLong("id"));
                            item.title = obj.optString("name");
                            item.cover = fixCoverUrl(obj.optString("coverImgUrl"));
                            item.playCount = String.valueOf(obj.optLong("playCount"));
                            item.songCount = obj.optInt("trackCount");
                            result.add(item);
                        }
                    }
                }
            } else if ("kg".equals(source)) {
                // 酷狗歌单列表及封面清洗
                String tagId = (tag == null || tag.isEmpty() || "全部".equals(tag)) ? "0" : tag;
                String url = "http://mobilecdn.kugou.com/api/v3/tag/specialList?tagid=" + tagId
                        + "&page=" + page + "&pagesize=" + pageSize + "&sort=2";
                HttpURLConnection conn = openConnection(url);
                conn.setRequestProperty("User-Agent", UA_MOBILE);
                if (conn.getResponseCode() == 200) {
                    JSONObject json = new JSONObject(readStream(conn.getInputStream()));
                    JSONObject data = json.optJSONObject("data");
                    if (data != null) {
                        JSONArray arr = data.optJSONArray("info");
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject obj = arr.getJSONObject(i);
                                PlaylistItem item = new PlaylistItem();
                                item.source = "kg";
                                item.id = String.valueOf(obj.optLong("specialid"));
                                item.title = obj.optString("specialname");
                                // 替换酷狗歌单封面中的 {size} 占位符
                                item.cover = fixCoverUrl(obj.optString("imgurl"));
                                item.playCount = String.valueOf(obj.optLong("playcount"));
                                item.songCount = obj.optInt("songcount");
                                result.add(item);
                            }
                        }
                    }
                }
            } else if ("tx".equals(source)) {
                String catId = (tag == null || tag.isEmpty() || "全部".equals(tag)) ? "10000000" : tag;
                int sin = (page > 0 ? page - 1 : 0) * pageSize;
                int ein = sin + pageSize - 1;
                String url = "http://c.y.qq.com/splcloud/fcgi-bin/fcg_get_diss_by_tag.fcg?categoryId=" + catId
                        + "&sortId=5&sin=" + sin + "&ein=" + ein + "&format=json";
                HttpURLConnection conn = openConnection(url);
                conn.setRequestProperty("Referer", "https://y.qq.com/");
                conn.setRequestProperty("User-Agent", UA_PC);
                if (conn.getResponseCode() == 200) {
                    JSONObject json = new JSONObject(readStream(conn.getInputStream()));
                    JSONObject data = json.optJSONObject("data");
                    if (data != null) {
                        JSONArray arr = data.optJSONArray("list");
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject obj = arr.getJSONObject(i);
                                PlaylistItem item = new PlaylistItem();
                                item.source = "tx";
                                item.id = obj.optString("dissid");
                                item.title = obj.optString("dissname");
                                item.cover = fixCoverUrl(obj.optString("imgurl"));
                                item.playCount = String.valueOf(obj.optLong("listennum"));
                                result.add(item);
                            }
                        }
                    }
                }
            } else if ("kw".equals(source)) {
                // 酷我广场歌单
                String url;
                if ("new".equals(tag)) {
                    url = "http://mobilecdnbasic.kuwo.cn/api?op=getlistinfo&pid=selected&pn=" + (page > 0 ? page - 1 : 0) + "&rn=" + pageSize + "&encode=utf-8&keyset=pl2012&identity=kuwo";
                } else {
                    url = "http://mobilecdnbasic.kuwo.cn/api?op=getlistinfo&pid=rec&pn=" + (page > 0 ? page - 1 : 0) + "&rn=" + pageSize + "&encode=utf-8&keyset=pl2012&identity=kuwo";
                }
                HttpURLConnection conn = openConnection(url);
                conn.setRequestProperty("User-Agent", UA_KUWO);
                if (conn.getResponseCode() == 200) {
                    JSONObject json = new JSONObject(readStream(conn.getInputStream()));
                    JSONArray arr = json.optJSONArray("list");
                    if (arr == null) arr = json.optJSONArray("child");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            PlaylistItem item = new PlaylistItem();
                            item.source = "kw";
                            item.id = obj.optString("sourceid");
                            if (item.id.isEmpty()) item.id = obj.optString("id");
                            item.title = obj.optString("name");
                            item.cover = fixCoverUrl(obj.optString("pic"));
                            item.playCount = obj.optString("playcnt");
                            result.add(item);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getPlaylists error for " + source, e);
        }
        return result;
    }

    /**
     * 问题1修复：歌单歌曲列表加载，适配酷我移动端轻量接口与多字段容错
     */
    public static List<SongItem> getPlaylistSongs(String source, String playlistId, int page, int pageSize) {
        List<SongItem> result = new ArrayList<SongItem>();
        try {
            if ("kw".equals(source)) {
                // 修复：使用免签名的移动端 API，彻底绕开 Web 端的 csrf 与 Secret 拦截
                String url = "http://mobilecdnbasic.kuwo.cn/api?op=getlistinfo&pid=" + playlistId
                        + "&pn=" + (page > 0 ? page - 1 : 0) + "&rn=" + pageSize + "&encode=utf-8&keyset=pl2012&identity=kuwo";
                HttpURLConnection conn = openConnection(url);
                conn.setRequestProperty("User-Agent", UA_KUWO);

                if (conn.getResponseCode() == 200) {
                    String resp = readStream(conn.getInputStream());
                    JSONObject json = new JSONObject(resp);

                    // 多结构容错：musiclist, data.musicList, child
                    JSONArray list = json.optJSONArray("musiclist");
                    if (list == null) {
                        JSONObject data = json.optJSONObject("data");
                        if (data != null) {
                            list = data.optJSONArray("musicList");
                            if (list == null) list = data.optJSONArray("list");
                        }
                    }
                    if (list == null) {
                        list = json.optJSONArray("child");
                    }

                    if (list != null) {
                        for (int i = 0; i < list.length(); i++) {
                            JSONObject obj = list.getJSONObject(i);
                            SongItem song = new SongItem();
                            song.source = "kw";

                            // ID 清洗：过滤 MUSIC_ 前缀
                            String id = obj.optString("id");
                            if (id.isEmpty()) id = obj.optString("rid");
                            if (id.isEmpty()) id = obj.optString("musicrid");
                            if (id.startsWith("MUSIC_")) {
                                id = id.substring(6);
                            }
                            song.id = id;

                            song.title = obj.optString("name");
                            if (song.title.isEmpty()) song.title = obj.optString("songName");
                            if (song.title.isEmpty()) song.title = obj.optString("title");

                            song.artist = obj.optString("artist");
                            if (song.artist.isEmpty()) song.artist = obj.optString("singer");

                            song.album = obj.optString("album");
                            if (song.album.isEmpty()) song.album = obj.optString("albumName");

                            String pic = obj.optString("pic");
                            if (pic.isEmpty()) pic = obj.optString("pic300");
                            if (pic.isEmpty()) pic = obj.optString("pic120");
                            if (pic.isEmpty()) pic = obj.optString("cover");
                            song.cover = fixCoverUrl(pic);

                            song.duration = obj.optLong("duration", 0);
                            if (song.duration > 0 && song.duration < 10000) {
                                song.duration *= 1000;
                            }
                            result.add(song);
                        }
                    }
                }
            } else if ("wy".equals(source)) {
                String url = "http://music.163.com/api/playlist/detail?id=" + playlistId;
                HttpURLConnection conn = openConnection(url);
                conn.setRequestProperty("User-Agent", UA_PC);
                if (conn.getResponseCode() == 200) {
                    JSONObject json = new JSONObject(readStream(conn.getInputStream()));
                    JSONObject resultObj = json.optJSONObject("result");
                    if (resultObj != null) {
                        JSONArray tracks = resultObj.optJSONArray("tracks");
                        if (tracks != null) {
                            for (int i = 0; i < tracks.length(); i++) {
                                JSONObject obj = tracks.getJSONObject(i);
                                SongItem song = new SongItem();
                                song.source = "wy";
                                song.id = String.valueOf(obj.optLong("id"));
                                song.title = obj.optString("name");
                                song.duration = obj.optLong("duration");

                                JSONArray artists = obj.optJSONArray("artists");
                                if (artists != null && artists.length() > 0) {
                                    StringBuilder sb = new StringBuilder();
                                    for (int j = 0; j < artists.length(); j++) {
                                        if (j > 0) sb.append(", ");
                                        sb.append(artists.getJSONObject(j).optString("name"));
                                    }
                                    song.artist = sb.toString();
                                }

                                JSONObject albumObj = obj.optJSONObject("album");
                                if (albumObj != null) {
                                    song.album = albumObj.optString("name");
                                    song.cover = fixCoverUrl(albumObj.optString("picUrl"));
                                }
                                result.add(song);
                            }
                        }
                    }
                }
            } else if ("kg".equals(source)) {
                // 酷狗歌单歌曲列表及封面处理
                String url = "http://mobilecdn.kugou.com/api/v3/special/song?specialid=" + playlistId
                        + "&page=" + page + "&pagesize=" + pageSize;
                HttpURLConnection conn = openConnection(url);
                conn.setRequestProperty("User-Agent", UA_MOBILE);
                if (conn.getResponseCode() == 200) {
                    JSONObject json = new JSONObject(readStream(conn.getInputStream()));
                    JSONObject data = json.optJSONObject("data");
                    if (data != null) {
                        JSONArray info = data.optJSONArray("info");
                        if (info != null) {
                            for (int i = 0; i < info.length(); i++) {
                                JSONObject obj = info.getJSONObject(i);
                                SongItem song = new SongItem();
                                song.source = "kg";
                                song.id = obj.optString("hash");
                                song.hash = song.id;
                                song.duration = obj.optLong("duration") * 1000;

                                String filename = obj.optString("filename");
                                if (filename.contains(" - ")) {
                                    String[] parts = filename.split(" - ", 2);
                                    song.artist = parts[0].trim();
                                    song.title = parts[1].trim();
                                } else {
                                    song.title = filename;
                                    song.artist = obj.optString("singername");
                                }

                                song.album = obj.optString("album_name");
                                // 替换封面占位符
                                String img = obj.optString("imgurl");
                                if (img.isEmpty()) img = obj.optString("album_img");
                                song.cover = fixCoverUrl(img);

                                result.add(song);
                            }
                        }
                    }
                }
            } else if ("tx".equals(source)) {
                String url = "http://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg?disstid=" + playlistId
                        + "&format=json&type=1&json=1&utf8=1";
                HttpURLConnection conn = openConnection(url);
                conn.setRequestProperty("Referer", "https://y.qq.com/");
                conn.setRequestProperty("User-Agent", UA_PC);
                if (conn.getResponseCode() == 200) {
                    JSONObject json = new JSONObject(readStream(conn.getInputStream()));
                    JSONArray cdlist = json.optJSONArray("cdlist");
                    if (cdlist != null && cdlist.length() > 0) {
                        JSONArray songlist = cdlist.getJSONObject(0).optJSONArray("songlist");
                        if (songlist != null) {
                            for (int i = 0; i < songlist.length(); i++) {
                                JSONObject obj = songlist.getJSONObject(i);
                                SongItem song = new SongItem();
                                song.source = "tx";
                                song.id = obj.optString("songmid");
                                song.songmid = song.id;
                                song.title = obj.optString("songname");
                                song.album = obj.optString("albumname");
                                song.duration = obj.optLong("interval") * 1000;

                                JSONArray singers = obj.optJSONArray("singer");
                                if (singers != null && singers.length() > 0) {
                                    StringBuilder sb = new StringBuilder();
                                    for (int j = 0; j < singers.length(); j++) {
                                        if (j > 0) sb.append(", ");
                                        sb.append(singers.getJSONObject(j).optString("name"));
                                    }
                                    song.artist = sb.toString();
                                }

                                String albummid = obj.optString("albummid");
                                if (!albummid.isEmpty()) {
                                    song.cover = fixCoverUrl("http://y.gtimg.cn/music/photo_new/T002R300x300M000" + albummid + ".jpg");
                                }
                                result.add(song);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getPlaylistSongs error for " + source, e);
        }
        return result;
    }

    /**
     * 问题3修复：歌词搜索列表（加入 QQ 音乐防盗链与 JSONP 清洗、酷我单引号伪 JSON 转义）
     */
    public static List<LyricItem> searchLyric(String source, String keyword) {
        List<LyricItem> results = new ArrayList<LyricItem>();
        if (keyword == null || keyword.trim().isEmpty()) return results;
        try {
            if ("tx".equals(source)) {
                // QQ音乐歌词检索：补全 Referer 防盗链头并清洗客户端回调包装
                String url = "http://c.y.qq.com/soso/fcgi-bin/client_search_cp?p=1&n=20&w="
                        + URLEncoder.encode(keyword, "UTF-8") + "&format=json&t=0";
                HttpURLConnection conn = openConnection(url);
                conn.setRequestProperty("Referer", "https://y.qq.com/");
                conn.setRequestProperty("User-Agent", UA_PC);

                if (conn.getResponseCode() == 200) {
                    String text = readStream(conn.getInputStream()).trim();
                    // 剥离 JSONP 回调
                    int first = text.indexOf('{');
                    int last = text.lastIndexOf('}');
                    if (first >= 0 && last > first) {
                        text = text.substring(first, last + 1);
                    }
                    JSONObject json = new JSONObject(text);
                    JSONObject data = json.optJSONObject("data");
                    if (data != null) {
                        JSONObject song = data.optJSONObject("song");
                        if (song != null) {
                            JSONArray list = song.optJSONArray("list");
                            if (list != null) {
                                for (int i = 0; i < list.length(); i++) {
                                    JSONObject item = list.getJSONObject(i);
                                    LyricItem li = new LyricItem();
                                    li.source = "tx";
                                    li.songId = item.optString("songmid");
                                    if (li.songId.isEmpty()) li.songId = item.optString("mid");
                                    li.title = item.optString("songname");
                                    li.album = item.optString("albumname");

                                    JSONArray singers = item.optJSONArray("singer");
                                    if (singers != null && singers.length() > 0) {
                                        StringBuilder sb = new StringBuilder();
                                        for (int j = 0; j < singers.length(); j++) {
                                            if (j > 0) sb.append(", ");
                                            sb.append(singers.getJSONObject(j).optString("name"));
                                        }
                                        li.artist = sb.toString();
                                    }
                                    results.add(li);
                                }
                            }
                        }
                    }
                }
            } else if ("kw".equals(source)) {
                // 酷我歌词检索：使用免签通道并纠正单引号伪 JSON 格式
                String url = "http://search.kuwo.cn/r.s?client=kt&all="
                        + URLEncoder.encode(keyword, "UTF-8") + "&pn=0&rn=20&rformat=json&encoding=utf8";
                HttpURLConnection conn = openConnection(url);
                conn.setRequestProperty("User-Agent", UA_KUWO);

                if (conn.getResponseCode() == 200) {
                    String text = readStream(conn.getInputStream()).trim();
                    // 将酷我 r.s 返回的单引号伪 JSON 转换为标准双引号 JSON
                    text = text.replace('\'', '"');
                    int first = text.indexOf('{');
                    int last = text.lastIndexOf('}');
                    if (first >= 0 && last > first) {
                        text = text.substring(first, last + 1);
                    }

                    JSONObject json = new JSONObject(text);
                    JSONArray list = json.optJSONArray("abslist");
                    if (list != null) {
                        for (int i = 0; i < list.length(); i++) {
                            JSONObject item = list.getJSONObject(i);
                            LyricItem li = new LyricItem();
                            li.source = "kw";
                            String rid = item.optString("MUSICRID");
                            if (rid.startsWith("MUSIC_")) {
                                rid = rid.substring(6);
                            }
                            li.songId = rid;
                            li.title = item.optString("SONGNAME");
                            li.artist = item.optString("ARTIST");
                            li.album = item.optString("ALBUM");
                            results.add(li);
                        }
                    }
                }
            } else if ("wy".equals(source)) {
                String url = "http://music.163.com/api/search/get/web?s="
                        + URLEncoder.encode(keyword, "UTF-8") + "&type=1&offset=0&total=true&limit=20";
                HttpURLConnection conn = openConnection(url);
                conn.setRequestProperty("User-Agent", UA_PC);
                if (conn.getResponseCode() == 200) {
                    JSONObject json = new JSONObject(readStream(conn.getInputStream()));
                    JSONObject res = json.optJSONObject("result");
                    if (res != null) {
                        JSONArray songs = res.optJSONArray("songs");
                        if (songs != null) {
                            for (int i = 0; i < songs.length(); i++) {
                                JSONObject item = songs.getJSONObject(i);
                                LyricItem li = new LyricItem();
                                li.source = "wy";
                                li.songId = String.valueOf(item.optLong("id"));
                                li.title = item.optString("name");

                                JSONArray artists = item.optJSONArray("artists");
                                if (artists != null && artists.length() > 0) {
                                    StringBuilder sb = new StringBuilder();
                                    for (int j = 0; j < artists.length(); j++) {
                                        if (j > 0) sb.append(", ");
                                        sb.append(artists.getJSONObject(j).optString("name"));
                                    }
                                    li.artist = sb.toString();
                                }
                                JSONObject album = item.optJSONObject("album");
                                if (album != null) {
                                    li.album = album.optString("name");
                                }
                                results.add(li);
                            }
                        }
                    }
                }
            } else if ("kg".equals(source)) {
                String url = "http://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword="
                        + URLEncoder.encode(keyword, "UTF-8") + "&duration=&hash=";
                HttpURLConnection conn = openConnection(url);
                conn.setRequestProperty("User-Agent", UA_MOBILE);
                if (conn.getResponseCode() == 200) {
                    JSONObject json = new JSONObject(readStream(conn.getInputStream()));
                    JSONArray candidates = json.optJSONArray("candidates");
                    if (candidates != null) {
                        for (int i = 0; i < candidates.length(); i++) {
                            JSONObject item = candidates.getJSONObject(i);
                            LyricItem li = new LyricItem();
                            li.source = "kg";
                            li.songId = item.optString("id");
                            li.accessKey = item.optString("accesskey");
                            li.title = item.optString("song");
                            li.artist = item.optString("singer");
                            results.add(li);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "searchLyric error for " + source, e);
        }
        return results;
    }

    /**
     * 获取指定歌曲的歌词内容并统一解码为标准 LRC 格式
     */
    public static String getLyric(String source, String songId, String accessKey) {
        if (songId == null || songId.isEmpty()) return "";
        try {
            if ("tx".equals(source)) {
                // QQ音乐歌词获取：校验 Referer，解 Base64 及转义字符
                String url = "http://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid="
                        + songId + "&format=json&nobase64=1";
                HttpURLConnection conn = openConnection(url);
                conn.setRequestProperty("Referer", "https://y.qq.com/");
                conn.setRequestProperty("User-Agent", UA_PC);

                if (conn.getResponseCode() == 200) {
                    String text = readStream(conn.getInputStream()).trim();
                    int first = text.indexOf('{');
                    int last = text.lastIndexOf('}');
                    if (first >= 0 && last > first) {
                        text = text.substring(first, last + 1);
                    }
                    JSONObject json = new JSONObject(text);
                    String lrc = json.optString("lyric");
                    if (!lrc.isEmpty()) {
                        // 若服务端仍然回传 Base64，则执行解码
                        if (!lrc.contains("[00:") && !lrc.contains("[ti:")) {
                            try {
                                byte[] decoded = Base64.decode(lrc, Base64.DEFAULT);
                                lrc = new String(decoded, "UTF-8");
                            } catch (Exception ignored) {}
                        }
                        // HTML 实体编码清理
                        lrc = lrc.replace("&apos;", "'")
                                .replace("&quot;", "\"")
                                .replace("&amp;", "&")
                                .replace("&#58;", ":")
                                .replace("&#46;", ".")
                                .replace("&#10;", "\n")
                                .replace("&#13;", "\r")
                                .replace("&#32;", " ");
                        return lrc;
                    }
                }
            } else if ("kw".equals(source)) {
                // 酷我歌词获取：优先调用 H5 高兼容接口并转换为标准时间轴 LRC
                String cleanRid = songId.startsWith("MUSIC_") ? songId.substring(6) : songId;
                String url = "http://m.kuwo.cn/newh5/singles/songinfoandlrc?musicId=" + cleanRid;
                HttpURLConnection conn = openConnection(url);
                conn.setRequestProperty("User-Agent", UA_MOBILE);
                conn.setRequestProperty("Referer", "http://m.kuwo.cn/");

                if (conn.getResponseCode() == 200) {
                    String resp = readStream(conn.getInputStream());
                    JSONObject json = new JSONObject(resp);
                    JSONObject data = json.optJSONObject("data");
                    if (data != null) {
                        JSONArray lrclist = data.optJSONArray("lrclist");
                        if (lrclist != null && lrclist.length() > 0) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < lrclist.length(); i++) {
                                JSONObject row = lrclist.getJSONObject(i);
                                String timeSec = row.optString("time");
                                String line = row.optString("lineLyric");
                                try {
                                    float s = Float.parseFloat(timeSec);
                                    int min = (int) (s / 60);
                                    float sec = s % 60;
                                    sb.append(String.format("[%02d:%05.2f]%s\n", min, sec, line));
                                } catch (Exception e) {
                                    sb.append(line).append('\n');
                                }
                            }
                            return sb.toString();
                        }
                    }
                }

                // 兜底降级：Kuwo 基础轻量歌词通道
                String fallbackUrl = "http://mobilecdnbasic.kuwo.cn/api?op=getlyric&rid=" + cleanRid + "&identity=kuwo";
                HttpURLConnection fallbackConn = openConnection(fallbackUrl);
                fallbackConn.setRequestProperty("User-Agent", UA_KUWO);
                if (fallbackConn.getResponseCode() == 200) {
                    return readStream(fallbackConn.getInputStream());
                }
            } else if ("wy".equals(source)) {
                String url = "http://music.163.com/api/song/lyric?os=pc&id=" + songId + "&lv=-1&kv=-1&tv=-1";
                HttpURLConnection conn = openConnection(url);
                conn.setRequestProperty("User-Agent", UA_PC);
                if (conn.getResponseCode() == 200) {
                    JSONObject json = new JSONObject(readStream(conn.getInputStream()));
                    JSONObject lrcObj = json.optJSONObject("lrc");
                    if (lrcObj != null) {
                        return lrcObj.optString("lyric");
                    }
                }
            } else if ("kg".equals(source)) {
                String url = "http://krcs.kugou.com/download?ver=1&client=man&id=" + songId
                        + "&accesskey=" + (accessKey != null ? accessKey : "") + "&fmt=lrc&charset=utf8";
                HttpURLConnection conn = openConnection(url);
                conn.setRequestProperty("User-Agent", UA_MOBILE);
                if (conn.getResponseCode() == 200) {
                    JSONObject json = new JSONObject(readStream(conn.getInputStream()));
                    String content = json.optString("content");
                    if (!content.isEmpty()) {
                        byte[] decoded = Base64.decode(content, Base64.DEFAULT);
                        return new String(decoded, "UTF-8");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getLyric error for " + source, e);
        }
        return "";
    }
}
