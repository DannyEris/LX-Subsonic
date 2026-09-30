// 歌词候选模型
    public static class LyricCandidate {
        public String source; // "wy" 或 "kg"
        public String id;
        public String title;
        public String artist;
        public String accessKey;

        public LyricCandidate(String source, String id, String title, String artist, String accessKey) {
            this.source = source;
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.accessKey = accessKey;
        }

        @Override
        public String toString() {
            String tag = "wy".equals(source) ? "[网易云]" : "[酷狗]";
            return tag + " " + title + " - " + artist;
        }
    }

    // 跨平台（网易云 + 酷狗）搜索候选歌词列表
    public static ArrayList<LyricCandidate> searchLyricCandidates(String keyword) {
        ArrayList<LyricCandidate> candidates = new ArrayList<LyricCandidate>();
        if (keyword == null || keyword.trim().length() == 0) return candidates;

        String clean = keyword.replaceAll("\\([^)]*\\)", "").replaceAll("\\[[^\\]]*\\]", "").trim();

        // 1. 网易云搜索前 5 条候选
        try {
            String wyUrl = "https://music.163.com/api/search/get/web?s=" + URLEncoder.encode(clean, "UTF-8") + "&type=1&offset=0&total=true&limit=5";
            String res = httpGet(wyUrl);
            if (res != null && res.contains("\"songs\"")) {
                JSONArray arr = new JSONObject(res).getJSONObject("result").getJSONArray("songs");
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject s = arr.getJSONObject(i);
                    long id = s.getLong("id");
                    String title = s.getString("name");
                    String artist = "群星";
                    if (s.has("artists") && s.getJSONArray("artists").length() > 0) {
                        artist = s.getJSONArray("artists").getJSONObject(0).getString("name");
                    }
                    candidates.add(new LyricCandidate("wy", String.valueOf(id), title, artist, ""));
                }
            }
        } catch (Throwable ignored) {}

        // 2. 酷狗搜索前 5 条候选
        try {
            String kgUrl = "http://mobilecdn.kugou.com/api/v3/search/song?keyword=" + URLEncoder.encode(clean, "UTF-8") + "&page=1&pagesize=5";
            String res = httpGet(kgUrl);
            if (res != null && res.contains("\"info\"")) {
                JSONArray arr = new JSONObject(res).getJSONObject("data").getJSONArray("info");
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject s = arr.getJSONObject(i);
                    String songname = s.optString("songname", "");
                    String singername = s.optString("singername", "");
                    String hash = s.optString("hash", "");
                    if (hash.length() > 0) {
                        candidates.add(new LyricCandidate("kg", hash, songname, singername, ""));
                    }
                }
            }
        } catch (Throwable ignored) {}

        return candidates;
    }

    // 根据候选目标下载对应的歌词内容（酷狗走 KRC 解码，网易云走 LPC 解码）
    public static String fetchLyricFromCandidate(LyricCandidate c) {
        if (c == null) return null;
        try {
            if ("wy".equals(c.source)) {
                String lrcUrl = "https://music.163.com/api/song/lyric?os=pc&id=" + c.id + "&lv=-1&kv=-1&tv=-1";
                String res = httpGet(lrcUrl);
                if (res != null && res.contains("\"lrc\"")) {
                    return new JSONObject(res).getJSONObject("lrc").getString("lyric");
                }
            } else if ("kg".equals(c.source)) {
                return fetchKugouLyricByHash(c.id);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // 酷狗原站歌词解析通道（利用 hash 换取 accesskey，Base64 解出标准 LRC）
    public static String fetchKugouLyricByHash(String hash) {
        try {
            String searchUrl = "http://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword=&duration=&hash=" + hash;
            String searchRes = httpGet(searchUrl);
            if (searchRes != null && searchRes.contains("\"candidates\"")) {
                JSONArray cArr = new JSONObject(searchRes).getJSONArray("candidates");
                if (cArr.length() > 0) {
                    JSONObject best = cArr.getJSONObject(0);
                    String id = best.getString("id");
                    String accesskey = best.getString("accesskey");

                    String downloadUrl = "http://krcs.kugou.com/download?ver=1&client=mobi&id=" + id + "&accesskey=" + accesskey + "&fmt=lrc&charset=utf8";
                    String downloadRes = httpGet(downloadUrl);
                    if (downloadRes != null && downloadRes.contains("\"content\"")) {
                        String b64 = new JSONObject(downloadRes).getString("content");
                        byte[] decoded = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                        return new String(decoded, "UTF-8");
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }
