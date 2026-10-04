package com.retro.subsonic;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class MusicService extends Service {
    public static final String BROADCAST_STATUS = "com.retro.subsonic.STATUS";

    public static final String ACTION_TOGGLE = "TOGGLE";
    public static final String ACTION_PLAY_INDEX = "PLAY_INDEX";
    public static final String ACTION_NEXT = "NEXT";
    public static final String ACTION_PREV = "PREV";
    public static final String ACTION_SEEK = "SEEK";
    public static final String ACTION_CYCLE_MODE = "CYCLE_MODE";
    public static final String ACTION_SET_MUTE = "SET_MUTE";

    public static final int MODE_LOOP_ALL = 0;
    public static final int MODE_SINGLE = 1;
    public static final int MODE_SHUFFLE = 2;

    private static final ArrayList<SongItem> playlist = new ArrayList<SongItem>();
    private static int currentIndex = -1;
    private static int currentMode = MODE_LOOP_ALL;

    private MediaPlayer mediaPlayer;
    private LocalStreamProxy currentProxy;
    private boolean isMuted = false;
    private volatile boolean isChangingSong = false;
    private volatile int lastBufferPercent = -1;

    private Handler preloadHandler = new Handler();
    private Runnable preloadRunnable = new Runnable() {
        @Override
        public void run() {
            triggerPreloadNextSong();
        }
    };

    private Handler progressHandler = new Handler();
    private Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && !isChangingSong) {
                try {
                    if (mediaPlayer.isPlaying()) {
                        int pos = mediaPlayer.getCurrentPosition();
                        int dur = mediaPlayer.getDuration();
                        if (dur > 0 && dur < 7200000 && pos <= dur) {
                            broadcastStatus(true, pos, dur);
                        }
                    }
                } catch (Throwable ignored) {}
            }
            progressHandler.postDelayed(this, 800);
        }
    };

    public static class SongItem {
        public String id;
        public String title;
        public String artist;
        public String streamUrl;
        public String coverArtId;
        public String quality;

        public SongItem(String id, String title, String artist, String streamUrl, String coverArtId, String quality) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.streamUrl = streamUrl;
            this.coverArtId = coverArtId;
            this.quality = quality;
        }
    }

    public static ArrayList<SongItem> getPlaylist() {
        return playlist;
    }

    public static int getCurrentIndex() {
        return currentIndex;
    }

    public static int getCurrentMode() {
        return currentMode;
    }

    public static void setQueue(ArrayList<SongItem> newQueue, int startIndex, Context context) {
        playlist.clear();
        if (newQueue != null) {
            playlist.addAll(newQueue);
        }
        currentIndex = startIndex;
        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(ACTION_PLAY_INDEX);
        intent.putExtra("target_index", startIndex);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initMediaPlayer();
        progressHandler.post(progressRunnable);
    }

    private void initMediaPlayer() {
        if (mediaPlayer != null) {
            try { mediaPlayer.release(); } catch (Throwable ignored) {}
        }
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                isChangingSong = false;
                applyMuteState();
                mp.start();
                AudioEffectsManager.getInstance().attachMediaPlayer(mp, MusicService.this);
                int dur = mp.getDuration();
                broadcastStatus(true, 0, (dur > 0 && dur < 7200000) ? dur : 0);
            }
        });

        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                handleAutoNext();
            }
        });

        mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                isChangingSong = false;
                lastBufferPercent = -1;
                return true;
            }
        });

        // 彻底移除 mediaPlayer.setOnBufferingUpdateListener 对真实下载进度的干扰
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String act = intent.getAction();
            if (ACTION_PLAY_INDEX.equals(act)) {
                int target = intent.getIntExtra("target_index", 0);
                playSong(target);
            } else if (ACTION_TOGGLE.equals(act)) {
                togglePlayPause();
            } else if (ACTION_NEXT.equals(act)) {
                playNextManual();
            } else if (ACTION_PREV.equals(act)) {
                playPrevManual();
            } else if (ACTION_SEEK.equals(act)) {
                int pos = intent.getIntExtra("position", 0);
                if (mediaPlayer != null) {
                    try { mediaPlayer.seekTo(pos); } catch (Throwable ignored) {}
                }
            } else if (ACTION_CYCLE_MODE.equals(act)) {
                currentMode = (currentMode + 1) % 3;
                broadcastCurrentState();
            } else if (ACTION_SET_MUTE.equals(act)) {
                isMuted = intent.getBooleanExtra("is_muted", false);
                applyMuteState();
            }
        }
        return START_STICKY;
    }

    private void applyMuteState() {
        if (mediaPlayer != null) {
            float vol = isMuted ? 0.0f : 1.0f;
            try { mediaPlayer.setVolume(vol, vol); } catch (Throwable ignored) {}
        }
    }

    private void playSong(int index) {
        if (index < 0 || index >= playlist.size()) return;
        currentIndex = index;
        final SongItem item = playlist.get(index);
        isChangingSong = true;

        preloadHandler.removeCallbacks(preloadRunnable);
        SongPreloadManager.getInstance().cancel();

        if (currentProxy != null) {
            currentProxy.stop();
            currentProxy = null;
        }

        try {
            mediaPlayer.reset();
            applyMuteState();

            // 1. 命中本地缓存文件或本地扫描文件：不显示缓冲数值，并在平稳起播后调度下一曲预加载
            if (CacheManager.isSongCached(this, item.id) || (item.streamUrl != null && item.streamUrl.startsWith("file://"))) {
                lastBufferPercent = -1;
                broadcastStatus(false, 0, 0);

                File localFile = CacheManager.isSongCached(this, item.id) ?
                        CacheManager.getSongFile(this, item.id) : new File(item.streamUrl.substring(7));
                mediaPlayer.setDataSource(this, Uri.fromFile(localFile));
                mediaPlayer.prepareAsync();
                savePlaybackState();

                preloadHandler.postDelayed(preloadRunnable, 1500);
                return;
            }

            // 2. 需从网络拉取：显示缓冲进度，严格缓冲完成后再触发下一首静默缓冲
            lastBufferPercent = 0;
            broadcastStatus(false, 0, 0);

            currentProxy = new LocalStreamProxy(this, item.id, item.streamUrl, new LocalStreamProxy.ProxyListener() {
                @Override
                public void onProgress(int percent) {
                    if (currentIndex < 0 || currentIndex >= playlist.size() || !item.id.equals(playlist.get(currentIndex).id)) return;
                    if (percent >= 100) {
                        lastBufferPercent = -1;
                    } else {
                        lastBufferPercent = percent;
                    }
                    broadcastCurrentState();
                }

                @Override
                public void onCached(File cachedFile) {
                    if (currentIndex < 0 || currentIndex >= playlist.size() || !item.id.equals(playlist.get(currentIndex).id)) return;
                    // 当前歌曲 100% 写入完毕，立即隐藏缓冲数值
                    lastBufferPercent = -1;
                    broadcastCurrentState();
                    // 仅当当前歌曲彻底缓冲完毕后，才启动下一首后台静默预缓冲
                    triggerPreloadNextSong();
                }

                @Override
                public void onError(String reason) {
                    lastBufferPercent = -1;
                    broadcastCurrentState();
                }
            });

            String proxyUrl = currentProxy.start();
            mediaPlayer.setDataSource(this, Uri.parse(proxyUrl));
            mediaPlayer.prepareAsync();
            savePlaybackState();

        } catch (Throwable t) {
            isChangingSong = false;
            lastBufferPercent = -1;
        }
    }

    private void triggerPreloadNextSong() {
        if (playlist.isEmpty() || currentIndex < 0) return;
        int nextIdx = (currentIndex + 1) % playlist.size();
        SongItem nextSong = playlist.get(nextIdx);
        if (nextSong != null && nextSong.streamUrl != null && !nextSong.streamUrl.isEmpty()) {
            SongPreloadManager.getInstance().preload(this, nextSong.id, nextSong.streamUrl);
        }
    }

    private void togglePlayPause() {
        if (mediaPlayer == null) return;
        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                broadcastCurrentState();
            } else {
                mediaPlayer.start();
                broadcastCurrentState();
            }
        } catch (Throwable ignored) {}
    }

    private void playNextManual() {
        if (playlist.isEmpty()) return;
        int next = (currentIndex + 1) % playlist.size();
        playSong(next);
    }

    private void playPrevManual() {
        if (playlist.isEmpty()) return;
        int prev = (currentIndex - 1 + playlist.size()) % playlist.size();
        playSong(prev);
    }

    private void handleAutoNext() {
        if (playlist.isEmpty()) return;
        if (currentMode == MODE_SINGLE) {
            playSong(currentIndex);
        } else if (currentMode == MODE_SHUFFLE) {
            int r = new Random().nextInt(playlist.size());
            playSong(r);
        } else {
            playNextManual();
        }
    }

    private void broadcastStatus(boolean isPlaying, int position, int duration) {
        if (currentIndex < 0 || currentIndex >= playlist.size()) return;
        SongItem cur = playlist.get(currentIndex);
        Intent intent = new Intent(BROADCAST_STATUS);
        intent.putExtra("isPlaying", isPlaying);
        intent.putExtra("mode", currentMode);
        intent.putExtra("songId", cur.id);
        intent.putExtra("title", cur.title);
        intent.putExtra("artist", cur.artist);
        intent.putExtra("coverArtId", cur.coverArtId);
        intent.putExtra("quality", cur.quality);
        intent.putExtra("streamUrl", cur.streamUrl);
        intent.putExtra("position", position);
        intent.putExtra("duration", duration);
        intent.putExtra("bufferPercent", lastBufferPercent);
        sendBroadcast(intent);
    }

    private void broadcastCurrentState() {
        if (mediaPlayer != null && !isChangingSong) {
            try {
                boolean playing = mediaPlayer.isPlaying();
                int pos = mediaPlayer.getCurrentPosition();
                int dur = mediaPlayer.getDuration();
                broadcastStatus(playing, pos, (dur > 0 && dur < 7200000) ? dur : 0);
            } catch (Throwable ignored) {}
        }
    }

    private void savePlaybackState() {
        if (currentIndex < 0 || currentIndex >= playlist.size()) return;
        try {
            SharedPreferences sp = getSharedPreferences("subsonic_playback_state", MODE_PRIVATE);
            JSONArray arr = new JSONArray();
            for (SongItem item : playlist) {
                JSONObject obj = new JSONObject();
                obj.put("id", item.id);
                obj.put("title", item.title);
                obj.put("artist", item.artist);
                obj.put("streamUrl", item.streamUrl);
                obj.put("coverArtId", item.coverArtId);
                obj.put("quality", item.quality);
                arr.put(obj);
            }
            sp.edit().putString("playlist_json", arr.toString())
                    .putInt("current_index", currentIndex)
                    .putInt("current_mode", currentMode)
                    .commit();
        } catch (Throwable ignored) {}
    }

    public static boolean restorePlaybackState(Context context) {
        try {
            SharedPreferences sp = context.getSharedPreferences("subsonic_playback_state", MODE_PRIVATE);
            String json = sp.getString("playlist_json", null);
            if (json == null) return false;
            JSONArray arr = new JSONArray(json);
            playlist.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                playlist.add(new SongItem(
                        o.getString("id"),
                        o.getString("title"),
                        o.getString("artist"),
                        o.optString("streamUrl", ""),
                        o.optString("coverArtId", null),
                        o.optString("quality", "")
                ));
            }
            currentIndex = sp.getInt("current_index", 0);
            currentMode = sp.getInt("current_mode", MODE_LOOP_ALL);
            return !playlist.isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        progressHandler.removeCallbacksAndMessages(null);
        preloadHandler.removeCallbacksAndMessages(null);
        SongPreloadManager.getInstance().cancel();
        if (currentProxy != null) {
            currentProxy.stop();
        }
        if (mediaPlayer != null) {
            try { mediaPlayer.release(); } catch (Throwable ignored) {}
            mediaPlayer = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
