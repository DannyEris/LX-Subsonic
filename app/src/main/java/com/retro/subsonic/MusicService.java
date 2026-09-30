package com.retro.subsonic;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MusicService extends Service {

    public static final String BROADCAST_STATUS = "com.retro.subsonic.STATUS_CHANGE";

    public static final String ACTION_PLAY_INDEX = "ACTION_PLAY_INDEX";
    public static final String ACTION_TOGGLE = "ACTION_TOGGLE";
    public static final String ACTION_NEXT = "ACTION_NEXT";
    public static final String ACTION_PREV = "ACTION_PREV";
    public static final String ACTION_SEEK = "ACTION_SEEK";
    public static final String ACTION_CYCLE_MODE = "ACTION_CYCLE_MODE";
    public static final String ACTION_SET_MUTE = "ACTION_SET_MUTE";

    public static final int MODE_LOOP_ALL = 0;
    public static final int MODE_SHUFFLE = 1;
    public static final int MODE_SINGLE = 2;

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

    private static ArrayList<SongItem> playlist = new ArrayList<SongItem>();
    private static int currentIndex = -1;
    private static int currentMode = MODE_LOOP_ALL;

    private MediaPlayer mediaPlayer;
    private LocalStreamProxy streamProxy;
    private Handler progressHandler = new Handler(Looper.getMainLooper());
    private boolean isMuted = false;
    private int currentBufferPercent = -1;

    // 核心改进：专属后台单线程执行器，彻底解放 UI 线程，杜绝 ANR
    private final ExecutorService playbackExecutor = Executors.newSingleThreadExecutor();

    private Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                broadcastStatus();
            }
            progressHandler.postDelayed(this, 1000);
        }
    };

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
        playlist.addAll(newQueue);
        currentIndex = startIndex;
        savePlaybackState(context);
        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(ACTION_PLAY_INDEX);
        intent.putExtra("target_index", startIndex);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        currentMode = getSharedPreferences("subsonic_cfg", MODE_PRIVATE).getInt("play_mode", MODE_LOOP_ALL);
        mediaPlayer = new MediaPlayer();
        setupMediaPlayerListeners();
        progressHandler.post(progressRunnable);
    }

    private void setupMediaPlayerListeners() {
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                float v = isMuted ? 0.0f : 1.0f;
                mp.setVolume(v, v);
                mp.start();
                AudioEffectsManager.getInstance().attachMediaPlayer(mp, MusicService.this);
                broadcastStatus();
                startForegroundNotification();
            }
        });

        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                onTrackCompleted();
            }
        });

        // 关键防护：增加错误监听并返回 true，阻止系统在出错时误调用 onCompletion 导致无限切歌死循环
        mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                currentBufferPercent = -1;
                broadcastStatus();
                return true;
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String act = intent.getAction();
            if (ACTION_PLAY_INDEX.equals(act)) {
                int idx = intent.getIntExtra("target_index", currentIndex);
                playIndex(idx);
            } else if (ACTION_TOGGLE.equals(act)) {
                toggle();
            } else if (ACTION_NEXT.equals(act)) {
                next();
            } else if (ACTION_PREV.equals(act)) {
                prev();
            } else if (ACTION_SEEK.equals(act)) {
                int pos = intent.getIntExtra("position", 0);
                seek(pos);
            } else if (ACTION_CYCLE_MODE.equals(act)) {
                cycleMode();
            } else if (ACTION_SET_MUTE.equals(act)) {
                isMuted = intent.getBooleanExtra("is_muted", false);
                if (mediaPlayer != null) {
                    float v = isMuted ? 0.0f : 1.0f;
                    mediaPlayer.setVolume(v, v);
                }
            }
        }
        return START_STICKY;
    }

    private void playIndex(final int index) {
        if (index < 0 || index >= playlist.size()) return;
        currentIndex = index;
        savePlaybackState(this);

        currentBufferPercent = 0;
        broadcastStatus();

        // 将准备、连接、代理全流程派发至工作线程执行，不占主线程一毫秒
        playbackExecutor.execute(new Runnable() {
            @Override
            public void run() {
                doPlayIndexBackground(index);
            }
        });
    }

    private synchronized void doPlayIndexBackground(int index) {
        if (index < 0 || index >= playlist.size()) return;
        final SongItem item = playlist.get(index);

        if (streamProxy != null) {
            streamProxy.stop();
            streamProxy = null;
        }

        if (item.streamUrl != null && (item.streamUrl.startsWith("file://") || item.streamUrl.startsWith("/"))) {
            currentBufferPercent = 100;
            startMediaPlayer(item.streamUrl);
            return;
        }

        if (CacheManager.isSongCached(this, item.id)) {
            File cachedFile = CacheManager.getSongFile(this, item.id);
            currentBufferPercent = 100;
            startMediaPlayer("file://" + cachedFile.getAbsolutePath());
            return;
        }

        try {
            streamProxy = new LocalStreamProxy(this, item.id, item.streamUrl, new LocalStreamProxy.ProxyListener() {
                @Override
                public void onProgress(int percent) {
                    currentBufferPercent = percent;
                    broadcastStatus();
                }

                @Override
                public void onCached(File cachedFile) {
                    currentBufferPercent = 100;
                    broadcastStatus();
                }

                @Override
                public void onError(String reason) {
                    currentBufferPercent = -1;
                    broadcastStatus();
                }
            });
            String localProxyUrl = streamProxy.start();
            startMediaPlayer(localProxyUrl);
        } catch (Exception e) {
            startMediaPlayer(item.streamUrl);
        }
    }

    private synchronized void startMediaPlayer(String playUrl) {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
                setupMediaPlayerListeners();
            } else {
                mediaPlayer.reset();
            }

            if (playUrl.startsWith("file://")) {
                mediaPlayer.setDataSource(this, Uri.parse(playUrl));
            } else {
                mediaPlayer.setDataSource(playUrl);
            }

            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            currentBufferPercent = -1;
            broadcastStatus();
        }
    }

    private void onTrackCompleted() {
        if (currentMode == MODE_SINGLE) {
            playIndex(currentIndex);
        } else {
            next();
        }
    }

    private void toggle() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            } else {
                float v = isMuted ? 0.0f : 1.0f;
                mediaPlayer.setVolume(v, v);
                mediaPlayer.start();
            }
            broadcastStatus();
        } else if (currentIndex >= 0 && currentIndex < playlist.size()) {
            playIndex(currentIndex);
        }
    }

    private void next() {
        if (playlist.isEmpty()) return;
        if (currentMode == MODE_SHUFFLE && playlist.size() > 1) {
            int nextIdx;
            do {
                nextIdx = new Random().nextInt(playlist.size());
            } while (nextIdx == currentIndex);
            playIndex(nextIdx);
        } else {
            int nextIdx = (currentIndex + 1) % playlist.size();
            playIndex(nextIdx);
        }
    }

    private void prev() {
        if (playlist.isEmpty()) return;
        int prevIdx = (currentIndex - 1 + playlist.size()) % playlist.size();
        playIndex(prevIdx);
    }

    private void seek(int pos) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.seekTo(pos);
            } catch (Exception ignored) {}
        }
    }

    private void cycleMode() {
        currentMode = (currentMode + 1) % 3;
        getSharedPreferences("subsonic_cfg", MODE_PRIVATE).edit().putInt("play_mode", currentMode).apply();
        broadcastStatus();
    }

    private void broadcastStatus() {
        Intent intent = new Intent(BROADCAST_STATUS);
        boolean isPlaying = mediaPlayer != null && mediaPlayer.isPlaying();
        intent.putExtra("isPlaying", isPlaying);
        intent.putExtra("mode", currentMode);
        intent.putExtra("bufferPercent", currentBufferPercent);

        if (currentIndex >= 0 && currentIndex < playlist.size()) {
            SongItem song = playlist.get(currentIndex);
            intent.putExtra("songId", song.id);
            intent.putExtra("title", song.title);
            intent.putExtra("artist", song.artist);
            intent.putExtra("coverArtId", song.coverArtId);
            intent.putExtra("quality", song.quality);
            intent.putExtra("streamUrl", song.streamUrl);
        }

        int pos = (mediaPlayer != null) ? mediaPlayer.getCurrentPosition() : 0;
        int dur = (mediaPlayer != null) ? mediaPlayer.getDuration() : 0;
        intent.putExtra("position", pos);
        intent.putExtra("duration", dur);

        sendBroadcast(intent);
    }

    private void startForegroundNotification() {
        try {
            Notification.Builder builder = new Notification.Builder(this)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(currentIndex >= 0 && currentIndex < playlist.size() ? playlist.get(currentIndex).title : "Subsonic 音乐")
                    .setContentText(currentIndex >= 0 && currentIndex < playlist.size() ? playlist.get(currentIndex).artist : "正在播放")
                    .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT));
            startForeground(1001, builder.build());
        } catch (Throwable ignored) {}
    }

    public static void savePlaybackState(Context context) {
        try {
            SharedPreferences sp = context.getSharedPreferences("subsonic_cfg", MODE_PRIVATE);
            JSONArray arr = new JSONArray();
            for (SongItem item : playlist) {
                JSONObject o = new JSONObject();
                o.put("id", item.id);
                o.put("title", item.title);
                o.put("artist", item.artist);
                o.put("streamUrl", item.streamUrl);
                o.put("coverArtId", item.coverArtId);
                o.put("quality", item.quality);
                arr.put(o);
            }
            sp.edit().putString("saved_playlist", arr.toString())
                    .putInt("saved_index", currentIndex)
                    .apply();
        } catch (Exception ignored) {}
    }

    public static boolean restorePlaybackState(Context context) {
        try {
            SharedPreferences sp = context.getSharedPreferences("subsonic_cfg", MODE_PRIVATE);
            String json = sp.getString("saved_playlist", null);
            if (json == null) return false;
            JSONArray arr = new JSONArray(json);
            playlist.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                playlist.add(new SongItem(
                        o.getString("id"),
                        o.getString("title"),
                        o.getString("artist"),
                        o.getString("streamUrl"),
                        o.optString("coverArtId", null),
                        o.optString("quality", "MP3")
                ));
            }
            currentIndex = sp.getInt("saved_index", 0);
            return !playlist.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        progressHandler.removeCallbacksAndMessages(null);
        playbackExecutor.shutdownNow();
        if (streamProxy != null) {
            streamProxy.stop();
            streamProxy = null;
        }
        if (mediaPlayer != null) {
            try {
                AudioEffectsManager.getInstance().detach();
                mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        stopForeground(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
