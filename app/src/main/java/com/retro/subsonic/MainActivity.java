package com.retro.subsonic;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.LruCache;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends Activity {

    private static final int PAGE_SEARCH = 0;
    private static final int PAGE_PLAZA = 1;
    private static final int PAGE_RANKING = 2;
    private static final int PAGE_FAV = 3;
    private static final int PAGE_LOCAL = 4;
    private static final int PAGE_SETTINGS = 5;
    private int currentPage = PAGE_SEARCH;

    private static final String[] BITRATE_LABELS = new String[]{"自动", "128K", "192K", "320K", "FLAC"};
    private static final String[] BITRATE_VALUES = new String[]{"auto", "128", "192", "320", "flac"};
    private static final String[] PLAZA_BITRATE_LABELS = new String[]{"跟随在线", "128K", "192K", "320K", "FLAC"};
    private static final String[] PLAZA_BITRATE_VALUES = new String[]{"follow", "128", "192", "320", "flac"};
    private static final String[] SEARCH_TYPES = new String[]{"歌曲", "歌手", "专辑"};

    // 3D 页面全屏三模式
    public static final int NAV_MODE_LOW_PROFILE = 0; // 全屏:灭灯
    public static final int NAV_MODE_HIDE = 1;        // 全屏:强隐
    public static final int NAV_MODE_VISIBLE = 2;     // 全屏:常驻
    private int currentNavMode = NAV_MODE_LOW_PROFILE;
    private Button btnVisualizerNavMode;
    private Button btnResetVisualizerAngle;
    private TextClock tcVisualizerClock;
    private GestureDetector visualizerGestureDetector;
    private Handler navBarHandler = new Handler();
    private Runnable navBarHideRunnable = new Runnable() {
        @Override
        public void run() {
            if (layoutVisualizerOverlay != null && layoutVisualizerOverlay.getVisibility() == View.VISIBLE) {
                if (currentNavMode == NAV_MODE_HIDE) {
                    getWindow().getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LOW_PROFILE);
                } else if (currentNavMode == NAV_MODE_LOW_PROFILE) {
                    getWindow().getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LOW_PROFILE);
                }
            }
        }
    };

    // 导航按键与主容器
    private Button btnNavSearch, btnNavPlaza, btnNavRanking, btnNavFav, btnNavLocal, btnNavSettings;
    private LinearLayout layoutPageSearch, layoutPagePlaza, layoutPageRanking, layoutPageFav, layoutPageLocal;
    private ScrollView layoutPageSettings;

    // 搜索页面
    private Spinner spinnerSearchPlatform, spinnerSearchType;
    private EditText etSearchKeyword;
    private Button btnSearchSubmit, btnSearchBack, btnToggleHotSearch;
    private LinearLayout layoutHotSearchBox, layoutHotSearchTags, layoutSearchResultBox;
    private TextView tvHotSearchTitle, tvSearchResultTitle;
    private CheckBox cbDedupSongs;
    private ListView lvSearchResults;
    private ArrayList<DisplayEntry> searchResultsList = new ArrayList<DisplayEntry>();
    private ArrayList<Map<String, String>> searchResultsData = new ArrayList<Map<String, String>>();
    private SimpleAdapter searchResultsAdapter;
    private ArrayList<DisplayEntry> rawSearchSongResults = new ArrayList<DisplayEntry>();
    private boolean isBrowsingArtistOrAlbum = false;
    private ArrayList<DisplayEntry> backupSearchList = new ArrayList<DisplayEntry>();
    private ArrayList<Map<String, String>> backupSearchData = new ArrayList<Map<String, String>>();
    private String backupSearchTitle = "";
    private boolean isHotSearchCollapsed = false;

    // 歌单广场
    private Spinner spinnerPlazaPlatform;
    private Button btnPlazaCategory, btnPlazaViewMode, btnPlazaRefresh, btnPlazaImport, btnPlazaSearchSubmit, btnPlazaBack;
    private EditText etPlazaSearch;
    private TextView tvPlazaCurrentTag;
    private LinearLayout layoutPlazaSortContainer;
    private GridView gvPlazaPlaylists;
    private ListView lvPlazaPlaylists;
    private ArrayList<DisplayEntry> plazaPlaylistsList = new ArrayList<DisplayEntry>();
    private ArrayList<Map<String, String>> plazaPlaylistsData = new ArrayList<Map<String, String>>();
    private SimpleAdapter plazaPlaylistsAdapter;
    private PlazaGridAdapter plazaGridAdapter;
    private String currentPlazaTagId = "";
    private String currentPlazaTagName = "";
    private String currentPlazaSort = "最热";
    private int currentPlazaPage = 1;
    private boolean isLoadingPlaza = false;
    private boolean hasMorePlaza = true;
    private boolean isPlazaSearchMode = false;
    private String currentPlazaSearchKeyword = "";
    private boolean isPlazaGridMode = true;
    private TextView footerPlazaLoading;
    private int currentPlazaRequestId = 0;
    private boolean isGridFlinging = false;
    private boolean isBrowsingPlazaSongs = false;
    private ArrayList<DisplayEntry> backupPlazaList = new ArrayList<DisplayEntry>();
    private ArrayList<Map<String, String>> backupPlazaData = new ArrayList<Map<String, String>>();
    private String backupPlazaTitle = "";
    private static final Map<String, ArrayList<DisplayEntry>> PLAZA_SNAPSHOT_CACHE = new HashMap<String, ArrayList<DisplayEntry>>();

    // 排行榜
    private Spinner spinnerRankingPlatform;
    private TextView tvRankingBoardTitle;
    private Button btnRankingRefresh;
    private ListView lvRankingBoards, lvRankingSongs;
    private ArrayList<DisplayEntry> rankingBoardsList = new ArrayList<DisplayEntry>();
    private ArrayList<Map<String, String>> rankingBoardsData = new ArrayList<Map<String, String>>();
    private SimpleAdapter rankingBoardsAdapter;
    private ArrayList<DisplayEntry> rankingSongsList = new ArrayList<DisplayEntry>();
    private ArrayList<Map<String, String>> rankingSongsData = new ArrayList<Map<String, String>>();
    private SimpleAdapter rankingSongsAdapter;

    // 收藏歌单
    private TextView tvFavTitle;
    private Button btnFavRefresh, btnCreatePlaylist, btnFavBack;
    private ListView lvFavPlaylists;
    private ArrayList<DisplayEntry> favPlaylistsList = new ArrayList<DisplayEntry>();
    private ArrayList<Map<String, String>> favPlaylistsData = new ArrayList<Map<String, String>>();
    private SimpleAdapter favPlaylistsAdapter;
    private ArrayList<DisplayEntry> rawServerUserPlaylists = new ArrayList<DisplayEntry>();
    private String currentActiveFavPlaylistId = null;

    // 本地音乐
    private TextView tvLocalPathStatus;
    private Button btnScanLocalMusic;
    private ListView lvLocalMusic;
    private ArrayList<DisplayEntry> localMusicList = new ArrayList<DisplayEntry>();
    private ArrayList<Map<String, String>> localMusicData = new ArrayList<Map<String, String>>();
    private SimpleAdapter localMusicAdapter;

    // 设置页面
    private EditText etServer, etUsername, etPassword, etTimeoutSec, etRetryCount, etDownloadPath, etCacheSize;
    private Spinner spinnerConfigBitrate, spinnerDetailBitrate, spinnerPlazaBitrate;
    private boolean isSpinnersInitializing = true;
    private Button btnClearCache, btnSaveSettings;
    private TextView tvCacheUsed;

    // 底部播放控制条与详情页
    private ImageView btnExitApp, btnDetailExitApp;
    private ImageView ivBottomCover, btnMode, btnDetailMode, btnPrev, btnDetailPrev, btnPlayPause, btnDetailPlayPause, btnNext, btnDetailNext, btnOpenEq, btnDetailEq;
    private TextView tvCurrentSong, tvBottomBuffer, tvTime, tvDetailTitle, tvDetailArtist, tvDetailQuality, tvDetailBuffer, tvDetailTime, tvLyricOffsetStatus;
    private SeekBar seekBar, detailSeekBar;
    private Button btnBottomFav, btnDetailFav, btnToggleQueue, btnCloseQueue, btnCloseDetail, btnDetailDownload, btnDetailDlna, btnDetailKeepScreen, btnDetailQueue;
    private Button btnLyricDelay, btnLyricReset, btnLyricAdvance, btnLyricDec, btnLyricInc, btnSwitchLyric;
    private LinearLayout layoutBottomPlayer, layoutDetailOverlay, layoutQueuePanel, layoutCoverContainer, layoutDetailSeekBox, layoutDetailControls, layoutDetailBottomBlank;
    private LinearLayout layoutDetailLyricsView, layoutDetailQueueView, layoutLyricsContainer;
    private ListView lvQueue, lvDetailQueue;
    private FrameLayout layoutVinylContainer, flVinylDisc;
    private ImageView ivVinylCircularCover, ivSquareCover;
    private TonearmView viewTonearm;
    private ScrollView scrollLyrics;
    private ArrayList<Map<String, String>> queueData = new ArrayList<Map<String, String>>();
    private SimpleAdapter queueAdapter, detailQueueAdapter;

    // 3D 视觉页面
    private Button btnDetailVisualizer;
    private FrameLayout layoutVisualizerOverlay;
    private Visualizer3DView viewVisualizer3D;
    private ImageView ivVisualizerSilhouette;
    private Button btnCloseVisualizer;
    private TextView tvVisualizerCurrentLyric, tvVisualizerNextLyric;
    private ImageView ivCapsuleCover, btnCapsuleMode, btnCapsulePrev, btnCapsulePlayPause, btnCapsuleNext, btnCapsuleQueue;
    private TextView tvCapsuleTitle, tvCapsuleArtist, tvCapsuleCurrentTime, tvCapsuleTotalTime;
    private SeekBar capsuleSeekBar;
    private AudioVisualizerHelper audioVisualizerHelper;

    // 3D 歌曲列表抽屉
    private LinearLayout layoutVisualizerQueuePanel;
    private Button btnCloseVisualizerQueue;
    private ListView lvVisualizerQueue;
    private SimpleAdapter visualizerQueueAdapter;

    // 状态与配置缓存
    private SharedPreferences prefs;
    private Set<String> favSongIds = new HashSet<String>();
    private ArrayList<DisplayEntry> featuredSongs = new ArrayList<DisplayEntry>();
    private ArrayList<DisplayEntry> carSongs = new ArrayList<DisplayEntry>();
    private boolean isVinylDisplayMode = true;
    private boolean isCurrentSongPlaying = false;
    private boolean isKeepScreenOn = false;
    private boolean isUserSeeking = false;
    private boolean isUserTouchingLyrics = false;
    private int currentLyricIndex = -1;
    private int lyricBaseFontSize = 15;
    private long manualLyricOffsetMs = 0;
    private long dlnaGlobalLyricOffsetMs = 0;
    private String currentLoadedRawLyrics = null;
    private String currentSongIdForLyric = null;
    private String currentArtistForLyric = null;
    private String currentTitleForLyric = null;
    private String lastLoadedSongId = "";
    private int lastValidProgressMs = 0;
    private RotateAnimation vinylRotateAnim;
    private Bitmap currentRawCoverBitmap, currentCircularCoverBitmap;
    private Handler lyricHandler = new Handler();
    private static LruCache<String, Bitmap> imageMemoryCache;
    private static final ExecutorService imageLoadExecutor = Executors.newFixedThreadPool(3);
    private Handler dlnaSyncHandler = new Handler();
    private Runnable dlnaSyncRunnable = new Runnable() {
        @Override
        public void run() {
            if (DlnaManager.isCasting()) {
                if (isCurrentSongPlaying && !isUserSeeking) {
                    DlnaManager.getPositionInfo(new DlnaManager.PositionCallback() {
                        @Override
                        public void onPositionReceived(int positionMs, int durationMs) {
                            if (positionMs >= 0 && !isUserSeeking) {
                                if (durationMs > 7200000 || (durationMs > 0 && positionMs > durationMs)) return;
                                int totalDur = durationMs > 0 ? durationMs : (seekBar != null ? seekBar.getMax() : 0);
                                if (totalDur > 5000 && positionMs >= totalDur - 1000 && lastValidProgressMs < totalDur * 0.70) return;
                                lastValidProgressMs = positionMs;
                                if (durationMs > 0) {
                                    seekBar.setMax(durationMs);
                                    detailSeekBar.setMax(durationMs);
                                    if (capsuleSeekBar != null) capsuleSeekBar.setMax(durationMs);
                                    String timeStr = formatTime(positionMs) + " / " + formatTime(durationMs);
                                    tvTime.setText(timeStr);
                                    tvDetailTime.setText(timeStr);
                                    if (tvCapsuleTotalTime != null) tvCapsuleTotalTime.setText(formatTime(durationMs));
                                }
                                seekBar.setProgress(positionMs);
                                detailSeekBar.setProgress(positionMs);
                                if (capsuleSeekBar != null) capsuleSeekBar.setProgress(positionMs);
                                if (tvCapsuleCurrentTime != null) tvCapsuleCurrentTime.setText(formatTime(positionMs));
                                updateLyricPosition(positionMs);
                            }
                        }
                    });
                }
                dlnaSyncHandler.postDelayed(this, 1000);
            }
        }
    };

    public static class DisplayEntry {
        public String id;
        public String title;
        public String artist;
        public String subtitle;
        public String coverArt;
        public String quality;
        public boolean isSong;
        public int bitRateNumeric;
        public String localPath;

        public DisplayEntry(String id, String title, String artist, String subtitle, String coverArt, String quality, boolean isSong) {
            this(id, title, artist, subtitle, coverArt, quality, isSong, 0, null);
        }
        public DisplayEntry(String id, String title, String artist, String subtitle, String coverArt, String quality, boolean isSong, int bitRateNumeric) {
            this(id, title, artist, subtitle, coverArt, quality, isSong, bitRateNumeric, null);
        }
        public DisplayEntry(String id, String title, String artist, String subtitle, String coverArt, String quality, boolean isSong, int bitRateNumeric, String localPath) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.subtitle = subtitle;
            this.coverArt = coverArt;
            this.quality = quality;
            this.isSong = isSong;
            this.bitRateNumeric = bitRateNumeric;
            this.localPath = localPath;
        }
    }

    private static class LyricRow {
        long timeMs;
        String text;
        TextView view;
        LyricRow(long timeMs, String text) { this.timeMs = timeMs; this.text = text; }
    }
    private ArrayList<LyricRow> lyricRows = new ArrayList<LyricRow>();

    private BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (MusicService.BROADCAST_STATUS.equals(intent.getAction())) {
                boolean isPlaying = intent.getBooleanExtra("isPlaying", false);
                isCurrentSongPlaying = isPlaying;
                updatePlayPauseIcons(isPlaying);
                updateVinylAnimationState();
                if (viewVisualizer3D != null) {
                    viewVisualizer3D.setPlaying(isPlaying);
                }
                if (audioVisualizerHelper != null) {
                    audioVisualizerHelper.setPlaying(isPlaying);
                }
                int mode = intent.getIntExtra("mode", MusicService.MODE_LOOP_ALL);
                updateModeIcons(mode);
                String songId = intent.getStringExtra("songId");
                String title = intent.getStringExtra("title");
                String artist = intent.getStringExtra("artist");
                String coverArtId = intent.getStringExtra("coverArtId");
                String quality = intent.getStringExtra("quality");
                String streamUrl = intent.getStringExtra("streamUrl");
                int position = intent.getIntExtra("position", 0);
                int duration = intent.getIntExtra("duration", 0);
                int bufferPercent = intent.getIntExtra("bufferPercent", -1);

                if (bufferPercent >= 0 && bufferPercent < 100) {
                    String bufStr = "缓冲 " + bufferPercent + "%";
                    if (tvBottomBuffer != null) {
                        tvBottomBuffer.setVisibility(View.VISIBLE);
                        tvBottomBuffer.setText(bufStr);
                    }
                    if (tvDetailBuffer != null) {
                        tvDetailBuffer.setVisibility(View.VISIBLE);
                        tvDetailBuffer.setText(bufStr);
                    }
                } else {
                    if (tvBottomBuffer != null) tvBottomBuffer.setVisibility(View.GONE);
                    if (tvDetailBuffer != null) tvDetailBuffer.setVisibility(View.GONE);
                }

                if (title != null) {
                    tvCurrentSong.setText(title + " - " + artist);
                    tvDetailTitle.setText(title);
                    tvDetailArtist.setText(artist);
                    tvDetailQuality.setText(getBitrateDisplay(getSavedBitrate(), quality));
                    if (tvCapsuleTitle != null) tvCapsuleTitle.setText(title);
                    if (tvCapsuleArtist != null) tvCapsuleArtist.setText(artist);

                    if (songId != null && !songId.equals(lastLoadedSongId)) {
                        lastLoadedSongId = songId;
                        lastValidProgressMs = 0;
                        if (!DlnaManager.isCasting()) manualLyricOffsetMs = 0;
                        updateLyricOffsetStatusView();
                        if (tvBottomBuffer != null) tvBottomBuffer.setVisibility(View.GONE);
                        if (tvDetailBuffer != null) tvDetailBuffer.setVisibility(View.GONE);
                        seekBar.setProgress(0);
                        detailSeekBar.setProgress(0);
                        if (capsuleSeekBar != null) capsuleSeekBar.setProgress(0);
                        tvTime.setText("00:00 / 00:00");
                        tvDetailTime.setText("00:00 / 00:00");
                        if (tvCapsuleCurrentTime != null) tvCapsuleCurrentTime.setText("00:00");
                        if (tvCapsuleTotalTime != null) tvCapsuleTotalTime.setText("00:00");
                        loadCoverArt(coverArtId != null ? coverArtId : songId);
                        loadLyrics(songId, artist, title);
                        refreshQueueList();
                        updateCacheSizeDisplay();

                        if (DlnaManager.isCasting()) {
                            dlnaSyncHandler.removeCallbacks(dlnaSyncRunnable);
                            dlnaSyncHandler.postDelayed(dlnaSyncRunnable, 500);
                            if (streamUrl != null && streamUrl.length() > 0) {
                                DlnaManager.playUrl(DlnaManager.getCurrentDevice(), streamUrl, title, artist, 0);
                            }
                        }
                    }
                    updateFavButtonState(songId);
                }

                boolean isDurationValid = (duration > 1000 && duration < 7200000);
                if (!DlnaManager.isCasting() && !isUserSeeking && isPlaying && isDurationValid) {
                    if (position > duration) position = duration;
                    if (position >= 0) {
                        lastValidProgressMs = position;
                        seekBar.setMax(duration);
                        seekBar.setProgress(position);
                        detailSeekBar.setMax(duration);
                        detailSeekBar.setProgress(position);
                        if (capsuleSeekBar != null) {
                            capsuleSeekBar.setMax(duration);
                            capsuleSeekBar.setProgress(position);
                        }
                        String timeStr = formatTime(position) + " / " + formatTime(duration);
                        tvTime.setText(timeStr);
                        tvDetailTime.setText(timeStr);
                        if (tvCapsuleCurrentTime != null) tvCapsuleCurrentTime.setText(formatTime(position));
                        if (tvCapsuleTotalTime != null) tvCapsuleTotalTime.setText(formatTime(duration));
                        updateLyricPosition(position);
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TLSSocketFactory.install();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("subsonic_cfg", MODE_PRIVATE);
        lyricBaseFontSize = prefs.getInt("lyric_font_size", 15);
        isVinylDisplayMode = prefs.getBoolean("is_vinyl_display_mode", true);
        isPlazaGridMode = prefs.getBoolean("is_plaza_grid_mode", true);
        currentNavMode = prefs.getInt("visualizer_nav_mode", NAV_MODE_LOW_PROFILE);

        if (imageMemoryCache == null) {
            int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
            int cacheSize = Math.max(1024 * 4, maxMemory / 8);
            imageMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
                @Override protected int sizeOf(String key, Bitmap bitmap) {
                    return bitmap.getByteCount() / 1024;
                }
            };
        }

        loadFavSet();
        loadLocalPlaylists();
        initViews();
        setupVisualizerGestures();
        setupControlIcons();
        setupBitrateSpinners();
        setupSpinners();
        setupVinylAnimation();
        updateCoverDisplayMode();
        updatePlazaViewModeState();
        loadSavedConfig();
        setupNavigation();
        setupListeners();
        updateCacheSizeDisplay();
        restoreLastSessionIfAvailable();
        switchPage(PAGE_SEARCH);
        fetchHotSearchForCurrentPlatform();

        if (etSearchKeyword != null) etSearchKeyword.clearFocus();

        IntentFilter filter = new IntentFilter(MusicService.BROADCAST_STATUS);
        registerReceiver(statusReceiver, filter);

        audioVisualizerHelper = new AudioVisualizerHelper();
        audioVisualizerHelper.setListener(new AudioVisualizerHelper.OnSpectrumDataListener() {
            @Override
            public void onSpectrumUpdate(float[] spectrum, final float overallEnergy) {
                if (viewVisualizer3D != null && layoutVisualizerOverlay != null && layoutVisualizerOverlay.getVisibility() == View.VISIBLE) {
                    viewVisualizer3D.updateEnergy(overallEnergy);
                }
            }
        });
    }

    private void restoreLastSessionIfAvailable() {
        if (MusicService.getPlaylist().isEmpty()) {
            boolean restored = MusicService.restorePlaybackState(this);
            if (restored) {
                refreshQueueList();
                int curIdx = MusicService.getCurrentIndex();
                ArrayList<MusicService.SongItem> list = MusicService.getPlaylist();
                if (curIdx >= 0 && curIdx < list.size()) {
                    MusicService.SongItem song = list.get(curIdx);
                    lastLoadedSongId = song.id;
                    tvCurrentSong.setText(song.title + " - " + song.artist);
                    tvDetailTitle.setText(song.title);
                    tvDetailArtist.setText(song.artist);
                    tvDetailQuality.setText(getBitrateDisplay(getSavedBitrate(), song.quality));
                    if (tvCapsuleTitle != null) tvCapsuleTitle.setText(song.title);
                    if (tvCapsuleArtist != null) tvCapsuleArtist.setText(song.artist);
                    loadCoverArt(song.coverArtId != null ? song.coverArtId : song.id);
                    loadLyrics(song.id, song.artist, song.title);
                    updateFavButtonState(song.id);
                    updatePlayPauseIcons(false);
                    updateModeIcons(MusicService.getCurrentMode());
                }
            }
        }
    }

    private void initViews() {
        btnNavSearch = (Button) findViewById(R.id.btn_nav_search);
        btnNavPlaza = (Button) findViewById(R.id.btn_nav_plaza);
        btnNavRanking = (Button) findViewById(R.id.btn_nav_ranking);
        btnNavFav = (Button) findViewById(R.id.btn_nav_fav);
        btnNavLocal = (Button) findViewById(R.id.btn_nav_local);
        btnNavSettings = (Button) findViewById(R.id.btn_nav_settings);
        btnExitApp = (ImageView) findViewById(R.id.btn_exit_app);

        layoutPageSearch = (LinearLayout) findViewById(R.id.layout_page_search);
        layoutPagePlaza = (LinearLayout) findViewById(R.id.layout_page_plaza);
        layoutPageRanking = (LinearLayout) findViewById(R.id.layout_page_ranking);
        layoutPageFav = (LinearLayout) findViewById(R.id.layout_page_fav);
        layoutPageLocal = (LinearLayout) findViewById(R.id.layout_page_local);
        layoutPageSettings = (ScrollView) findViewById(R.id.layout_page_settings);

        // 搜索页面控件
        spinnerSearchPlatform = (Spinner) findViewById(R.id.spinner_search_platform);
        spinnerSearchType = (Spinner) findViewById(R.id.spinner_search_type);
        etSearchKeyword = (EditText) findViewById(R.id.et_search_keyword);
        btnSearchSubmit = (Button) findViewById(R.id.btn_search_submit);
        btnSearchBack = (Button) findViewById(R.id.btn_search_back);
        btnToggleHotSearch = (Button) findViewById(R.id.btn_toggle_hot_search);
        layoutHotSearchBox = (LinearLayout) findViewById(R.id.layout_hot_search_box);
        layoutHotSearchTags = (LinearLayout) findViewById(R.id.layout_hot_search_tags);
        tvHotSearchTitle = (TextView) findViewById(R.id.tv_hot_search_title);
        layoutSearchResultBox = (LinearLayout) findViewById(R.id.layout_search_result_box);
        tvSearchResultTitle = (TextView) findViewById(R.id.tv_search_result_title);
        cbDedupSongs = (CheckBox) findViewById(R.id.cb_dedup_songs);
        lvSearchResults = (ListView) findViewById(R.id.lv_search_results);
        searchResultsAdapter = new SimpleAdapter(this, searchResultsData, android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvSearchResults.setAdapter(searchResultsAdapter);

        // 广场页面控件
        spinnerPlazaPlatform = (Spinner) findViewById(R.id.spinner_plaza_platform);
        btnPlazaCategory = (Button) findViewById(R.id.btn_plaza_category);
        btnPlazaViewMode = (Button) findViewById(R.id.btn_plaza_view_mode);
        btnPlazaRefresh = (Button) findViewById(R.id.btn_plaza_refresh);
        btnPlazaImport = (Button) findViewById(R.id.btn_plaza_import);
        btnPlazaSearchSubmit = (Button) findViewById(R.id.btn_plaza_search_submit);
        btnPlazaBack = (Button) findViewById(R.id.btn_plaza_back);
        etPlazaSearch = (EditText) findViewById(R.id.et_plaza_search);
        tvPlazaCurrentTag = (TextView) findViewById(R.id.tv_plaza_current_tag);
        layoutPlazaSortContainer = (LinearLayout) findViewById(R.id.layout_plaza_sort_container);
        gvPlazaPlaylists = (GridView) findViewById(R.id.gv_plaza_playlists);
        lvPlazaPlaylists = (ListView) findViewById(R.id.lv_plaza_playlists);
        footerPlazaLoading = new TextView(this);
        footerPlazaLoading.setText("点击加载更多...");
        footerPlazaLoading.setGravity(Gravity.CENTER);
        footerPlazaLoading.setPadding(0, 24, 0, 24);
        footerPlazaLoading.setTextColor(0xFF888888);
        footerPlazaLoading.setTextSize(13);
        lvPlazaPlaylists.addFooterView(footerPlazaLoading);
        plazaPlaylistsAdapter = new SimpleAdapter(this, plazaPlaylistsData, android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvPlazaPlaylists.setAdapter(plazaPlaylistsAdapter);
        plazaGridAdapter = new PlazaGridAdapter();
        gvPlazaPlaylists.setAdapter(plazaGridAdapter);

        // 排行榜控件
        spinnerRankingPlatform = (Spinner) findViewById(R.id.spinner_ranking_platform);
        tvRankingBoardTitle = (TextView) findViewById(R.id.tv_ranking_board_title);
        btnRankingRefresh = (Button) findViewById(R.id.btn_ranking_refresh);
        lvRankingBoards = (ListView) findViewById(R.id.lv_ranking_boards);
        lvRankingSongs = (ListView) findViewById(R.id.lv_ranking_songs);
        rankingBoardsAdapter = new SimpleAdapter(this, rankingBoardsData, android.R.layout.simple_list_item_1,
                new String[]{"title"}, new int[]{android.R.id.text1});
        lvRankingBoards.setAdapter(rankingBoardsAdapter);
        rankingSongsAdapter = new SimpleAdapter(this, rankingSongsData, android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvRankingSongs.setAdapter(rankingSongsAdapter);

        // 收藏控件
        tvFavTitle = (TextView) findViewById(R.id.tv_fav_title);
        btnFavRefresh = (Button) findViewById(R.id.btn_fav_refresh);
        btnCreatePlaylist = (Button) findViewById(R.id.btn_create_playlist);
        btnFavBack = (Button) findViewById(R.id.btn_fav_back);
        lvFavPlaylists = (ListView) findViewById(R.id.lv_fav_playlists);
        favPlaylistsAdapter = new SimpleAdapter(this, favPlaylistsData, android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvFavPlaylists.setAdapter(favPlaylistsAdapter);

        // 本地控件
        tvLocalPathStatus = (TextView) findViewById(R.id.tv_local_path_status);
        btnScanLocalMusic = (Button) findViewById(R.id.btn_scan_local_music);
        lvLocalMusic = (ListView) findViewById(R.id.lv_local_music);
        localMusicAdapter = new SimpleAdapter(this, localMusicData, android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvLocalMusic.setAdapter(localMusicAdapter);

        // 设置控件
        etServer = (EditText) findViewById(R.id.et_server);
        etUsername = (EditText) findViewById(R.id.et_username);
        etPassword = (EditText) findViewById(R.id.et_password);
        etTimeoutSec = (EditText) findViewById(R.id.et_timeout_sec);
        etRetryCount = (EditText) findViewById(R.id.et_retry_count);
        etDownloadPath = (EditText) findViewById(R.id.et_download_path);
        spinnerConfigBitrate = (Spinner) findViewById(R.id.spinner_config_bitrate);
        spinnerDetailBitrate = (Spinner) findViewById(R.id.spinner_detail_bitrate);
        spinnerPlazaBitrate = (Spinner) findViewById(R.id.spinner_plaza_bitrate);
        etCacheSize = (EditText) findViewById(R.id.et_cache_size);
        btnClearCache = (Button) findViewById(R.id.btn_clear_cache);
        btnSaveSettings = (Button) findViewById(R.id.btn_save_settings);
        tvCacheUsed = (TextView) findViewById(R.id.tv_cache_used);

        // 底栏控制
        layoutBottomPlayer = (LinearLayout) findViewById(R.id.layout_bottom_player);
        ivBottomCover = (ImageView) findViewById(R.id.iv_bottom_cover);
        tvCurrentSong = (TextView) findViewById(R.id.tv_current_song);
        tvBottomBuffer = (TextView) findViewById(R.id.tv_bottom_buffer);
        tvTime = (TextView) findViewById(R.id.tv_time);
        seekBar = (SeekBar) findViewById(R.id.seek_bar);
        btnMode = (ImageView) findViewById(R.id.btn_mode);
        btnPrev = (ImageView) findViewById(R.id.btn_prev);
        btnPlayPause = (ImageView) findViewById(R.id.btn_play_pause);
        btnNext = (ImageView) findViewById(R.id.btn_next);
        btnOpenEq = (ImageView) findViewById(R.id.btn_open_eq);
        btnBottomFav = (Button) findViewById(R.id.btn_bottom_fav);
        btnToggleQueue = (Button) findViewById(R.id.btn_toggle_queue);
        layoutQueuePanel = (LinearLayout) findViewById(R.id.layout_queue_panel);
        btnCloseQueue = (Button) findViewById(R.id.btn_close_queue);
        lvQueue = (ListView) findViewById(R.id.lv_queue);

        // 详情 Overlay
        layoutDetailOverlay = (LinearLayout) findViewById(R.id.layout_detail_overlay);
        btnCloseDetail = (Button) findViewById(R.id.btn_close_detail);
        btnDetailDownload = (Button) findViewById(R.id.btn_detail_download);
        btnDetailDlna = (Button) findViewById(R.id.btn_detail_dlna);
        btnDetailExitApp = (ImageView) findViewById(R.id.btn_detail_exit_app);
        btnDetailVisualizer = (Button) findViewById(R.id.btn_detail_visualizer);
        btnDetailKeepScreen = (Button) findViewById(R.id.btn_detail_keep_screen);
        btnDetailQueue = (Button) findViewById(R.id.btn_detail_queue);
        layoutCoverContainer = (LinearLayout) findViewById(R.id.layout_cover_container);
        layoutVinylContainer = (FrameLayout) findViewById(R.id.layout_vinyl_container);
        flVinylDisc = (FrameLayout) findViewById(R.id.fl_vinyl_disc);
        ivVinylCircularCover = (ImageView) findViewById(R.id.iv_vinyl_circular_cover);
        viewTonearm = (TonearmView) findViewById(R.id.view_tonearm);
        ivSquareCover = (ImageView) findViewById(R.id.iv_square_cover);
        btnDetailFav = (Button) findViewById(R.id.btn_detail_fav);
        tvDetailTitle = (TextView) findViewById(R.id.tv_detail_title);
        tvDetailArtist = (TextView) findViewById(R.id.tv_detail_artist);
        tvDetailQuality = (TextView) findViewById(R.id.tv_detail_quality);
        tvDetailBuffer = (TextView) findViewById(R.id.tv_detail_buffer);
        tvDetailTime = (TextView) findViewById(R.id.tv_detail_time);
        detailSeekBar = (SeekBar) findViewById(R.id.detail_seek_bar);
        btnDetailMode = (ImageView) findViewById(R.id.btn_detail_mode);
        btnDetailPrev = (ImageView) findViewById(R.id.btn_detail_prev);
        btnDetailPlayPause = (ImageView) findViewById(R.id.btn_detail_play_pause);
        btnDetailNext = (ImageView) findViewById(R.id.btn_detail_next);
        btnDetailEq = (ImageView) findViewById(R.id.btn_detail_eq);
        layoutDetailBottomBlank = (LinearLayout) findViewById(R.id.layout_detail_bottom_blank);
        layoutDetailLyricsView = (LinearLayout) findViewById(R.id.layout_detail_lyrics_view);
        layoutDetailQueueView = (LinearLayout) findViewById(R.id.layout_detail_queue_view);
        lvDetailQueue = (ListView) findViewById(R.id.lv_detail_queue);
        scrollLyrics = (ScrollView) findViewById(R.id.scroll_lyrics);
        layoutLyricsContainer = (LinearLayout) findViewById(R.id.layout_lyrics_container);
        btnLyricDelay = (Button) findViewById(R.id.btn_lyric_delay);
        btnLyricReset = (Button) findViewById(R.id.btn_lyric_reset);
        btnLyricAdvance = (Button) findViewById(R.id.btn_lyric_advance);
        tvLyricOffsetStatus = (TextView) findViewById(R.id.tv_lyric_offset_status);
        btnLyricDec = (Button) findViewById(R.id.btn_lyric_dec);
        btnLyricInc = (Button) findViewById(R.id.btn_lyric_inc);
        btnSwitchLyric = (Button) findViewById(R.id.btn_switch_lyric);

        queueAdapter = new SimpleAdapter(this, queueData, android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvQueue.setAdapter(queueAdapter);
        detailQueueAdapter = new SimpleAdapter(this, queueData, android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvDetailQueue.setAdapter(detailQueueAdapter);

        // 3D 视觉 Overlay
        layoutVisualizerOverlay = (FrameLayout) findViewById(R.id.layout_visualizer_overlay);
        viewVisualizer3D = (Visualizer3DView) findViewById(R.id.view_visualizer_3d);
        ivVisualizerSilhouette = (ImageView) findViewById(R.id.iv_visualizer_silhouette);
        btnCloseVisualizer = (Button) findViewById(R.id.btn_close_visualizer);
        btnVisualizerNavMode = (Button) findViewById(R.id.btn_visualizer_nav_mode);
        btnResetVisualizerAngle = (Button) findViewById(R.id.btn_reset_visualizer_angle);
        tcVisualizerClock = (TextClock) findViewById(R.id.tc_visualizer_clock);
        tvVisualizerCurrentLyric = (TextView) findViewById(R.id.tv_visualizer_current_lyric);
        tvVisualizerNextLyric = (TextView) findViewById(R.id.tv_visualizer_next_lyric);

        // 3D 胶囊控制栏
        ivCapsuleCover = (ImageView) findViewById(R.id.iv_capsule_cover);
        tvCapsuleTitle = (TextView) findViewById(R.id.tv_capsule_title);
        tvCapsuleArtist = (TextView) findViewById(R.id.tv_capsule_artist);
        tvCapsuleCurrentTime = (TextView) findViewById(R.id.tv_capsule_current_time);
        tvCapsuleTotalTime = (TextView) findViewById(R.id.tv_capsule_total_time);
        capsuleSeekBar = (SeekBar) findViewById(R.id.capsule_seek_bar);
        btnCapsuleMode = (ImageView) findViewById(R.id.btn_capsule_mode);
        btnCapsulePrev = (ImageView) findViewById(R.id.btn_capsule_prev);
        btnCapsulePlayPause = (ImageView) findViewById(R.id.btn_capsule_play_pause);
        btnCapsuleNext = (ImageView) findViewById(R.id.btn_capsule_next);
        btnCapsuleQueue = (ImageView) findViewById(R.id.btn_capsule_queue);

        // 3D 队列抽屉
        layoutVisualizerQueuePanel = (LinearLayout) findViewById(R.id.layout_visualizer_queue_panel);
        btnCloseVisualizerQueue = (Button) findViewById(R.id.btn_close_visualizer_queue);
        lvVisualizerQueue = (ListView) findViewById(R.id.lv_visualizer_queue);
        visualizerQueueAdapter = new SimpleAdapter(this, queueData, android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvVisualizerQueue.setAdapter(visualizerQueueAdapter);
    }

    // ================= 3D 视效全屏触控与手势交互接入 =================
    private void setupVisualizerGestures() {
        visualizerGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (layoutVisualizerOverlay == null || layoutVisualizerOverlay.getVisibility() != View.VISIBLE) {
                    return false;
                }
                int width = layoutVisualizerOverlay.getWidth();
                if (width <= 0) width = getResources().getDisplayMetrics().widthPixels;
                float x = e.getX();

                if (x < width * 0.33f) {
                    // 左侧双击：上一首
                    startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_PREV));
                    Toast.makeText(MainActivity.this, "上一首", Toast.LENGTH_SHORT).show();
                } else if (x > width * 0.67f) {
                    // 右侧双击：下一首
                    startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_NEXT));
                    Toast.makeText(MainActivity.this, "下一首", Toast.LENGTH_SHORT).show();
                } else {
                    // 中间双击：播放 / 暂停
                    if (DlnaManager.isCasting()) {
                        if (isCurrentSongPlaying) DlnaManager.pause();
                        else DlnaManager.resume();
                    }
                    startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_TOGGLE));
                    Toast.makeText(MainActivity.this, isCurrentSongPlaying ? "暂停" : "播放", Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                // 上下滑动调整仰角：向上滑动（distanceY > 0）仰角增加，向下滑动仰角减小
                if (viewVisualizer3D != null && layoutVisualizerOverlay != null && layoutVisualizerOverlay.getVisibility() == View.VISIBLE) {
                    float deltaAngle = distanceY * 0.12f;
                    viewVisualizer3D.adjustPitch(deltaAngle);
                    return true;
                }
                return false;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });

        View.OnTouchListener touchListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (layoutVisualizerOverlay != null && layoutVisualizerOverlay.getVisibility() == View.VISIBLE) {
                    return visualizerGestureDetector.onTouchEvent(event);
                }
                return false;
            }
        };

        if (layoutVisualizerOverlay != null) layoutVisualizerOverlay.setOnTouchListener(touchListener);
        if (viewVisualizer3D != null) viewVisualizer3D.setOnTouchListener(touchListener);
    }

    private void updatePlazaViewModeState() {
        if (isBrowsingPlazaSongs) {
            gvPlazaPlaylists.setVisibility(View.GONE);
            lvPlazaPlaylists.setVisibility(View.VISIBLE);
            btnPlazaViewMode.setVisibility(View.GONE);
            return;
        }
        btnPlazaViewMode.setVisibility(View.VISIBLE);
        if (isPlazaGridMode) {
            gvPlazaPlaylists.setVisibility(View.VISIBLE);
            lvPlazaPlaylists.setVisibility(View.GONE);
            btnPlazaViewMode.setText("网格");
        } else {
            gvPlazaPlaylists.setVisibility(View.GONE);
            lvPlazaPlaylists.setVisibility(View.VISIBLE);
            btnPlazaViewMode.setText("列表");
        }
    }

    private void toggleHotSearchBox() {
        isHotSearchCollapsed = !isHotSearchCollapsed;
        View parentScroll = findViewById(R.id.scroll_hot_search);
        if (parentScroll == null && layoutHotSearchTags != null) {
            parentScroll = (View) layoutHotSearchTags.getParent();
        }
        if (parentScroll != null) {
            parentScroll.setVisibility(isHotSearchCollapsed ? View.GONE : View.VISIBLE);
        }
        if (btnToggleHotSearch != null) {
            btnToggleHotSearch.setText(isHotSearchCollapsed ? "展开" : "收起");
        }
        if (tvHotSearchTitle != null) {
            tvHotSearchTitle.setText(isHotSearchCollapsed ? "热门搜索 [已收起]" : "热门搜索");
        }
        Toast.makeText(MainActivity.this, isHotSearchCollapsed ? "已收起热搜" : "已展开热搜", Toast.LENGTH_SHORT).show();
    }

    private void setupNavigation() {
        View.OnClickListener navClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int id = v.getId();
                if (id == R.id.btn_nav_search) switchPage(PAGE_SEARCH);
                else if (id == R.id.btn_nav_plaza) switchPage(PAGE_PLAZA);
                else if (id == R.id.btn_nav_ranking) switchPage(PAGE_RANKING);
                else if (id == R.id.btn_nav_fav) switchPage(PAGE_FAV);
                else if (id == R.id.btn_nav_local) switchPage(PAGE_LOCAL);
                else if (id == R.id.btn_nav_settings) switchPage(PAGE_SETTINGS);
            }
        };
        btnNavSearch.setOnClickListener(navClick);
        btnNavPlaza.setOnClickListener(navClick);
        btnNavRanking.setOnClickListener(navClick);
        btnNavFav.setOnClickListener(navClick);
        btnNavLocal.setOnClickListener(navClick);
        btnNavSettings.setOnClickListener(navClick);
    }

    private void switchPage(int page) {
        currentPage = page;
        btnNavSearch.setTextColor(page == PAGE_SEARCH ? 0xFF00E5FF : 0xFFA0A5B5);
        btnNavPlaza.setTextColor(page == PAGE_PLAZA ? 0xFF00E5FF : 0xFFA0A5B5);
        btnNavRanking.setTextColor(page == PAGE_RANKING ? 0xFF00E5FF : 0xFFA0A5B5);
        btnNavFav.setTextColor(page == PAGE_FAV ? 0xFF00E5FF : 0xFFA0A5B5);
        btnNavLocal.setTextColor(page == PAGE_LOCAL ? 0xFF00E5FF : 0xFFA0A5B5);
        btnNavSettings.setTextColor(page == PAGE_SETTINGS ? 0xFF00E5FF : 0xFFA0A5B5);

        layoutPageSearch.setVisibility(page == PAGE_SEARCH ? View.VISIBLE : View.GONE);
        layoutPagePlaza.setVisibility(page == PAGE_PLAZA ? View.VISIBLE : View.GONE);
        layoutPageRanking.setVisibility(page == PAGE_RANKING ? View.VISIBLE : View.GONE);
        layoutPageFav.setVisibility(page == PAGE_FAV ? View.VISIBLE : View.GONE);
        layoutPageLocal.setVisibility(page == PAGE_LOCAL ? View.VISIBLE : View.GONE);
        layoutPageSettings.setVisibility(page == PAGE_SETTINGS ? View.VISIBLE : View.GONE);

        if (page == PAGE_SEARCH) {
            fetchHotSearchForCurrentPlatform();
            if (etSearchKeyword != null) etSearchKeyword.clearFocus();
        } else if (page == PAGE_PLAZA) {
            setupPlazaSortButtons();
            loadPlazaSonglists(false);
        } else if (page == PAGE_RANKING) {
            loadLeaderboardBoards();
        } else if (page == PAGE_FAV) {
            showFavAndCustomPlaylists();
            syncFavoritesAndPlaylists(false);
        } else if (page == PAGE_LOCAL) {
            scanLocalMusicFiles();
        } else if (page == PAGE_SETTINGS) {
            updateCacheSizeDisplay();
        }
    }

    private void setupPlazaSortButtons() {
        int pos = spinnerPlazaPlatform.getSelectedItemPosition();
        final String code = LxApiHelper.PLAZA_PLATFORM_CODES[pos >= 0 ? pos : 0];
        final String[] sorts = LxApiHelper.getPlatformSorts(code);
        layoutPlazaSortContainer.removeAllViews();
        boolean currentSortValid = false;
        for (String s : sorts) {
            if (s.equals(currentPlazaSort)) {
                currentSortValid = true;
                break;
            }
        }
        if (!currentSortValid) currentPlazaSort = sorts[0];
        float density = getResources().getDisplayMetrics().density;
        for (final String sName : sorts) {
            final Button sBtn = new Button(this);
            sBtn.setText(sName);
            sBtn.setTextSize(10);
            boolean isSelected = sName.equals(currentPlazaSort);
            sBtn.setTextColor(isSelected ? 0xFF00E5FF : 0xFF94A3B8);
            sBtn.setBackgroundResource(isSelected ? R.drawable.bg_btn_accent : R.drawable.bg_btn_default);
            sBtn.setPadding((int) (8 * density), 0, (int) (8 * density), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (24 * density));
            lp.leftMargin = (int) (4 * density);
            sBtn.setLayoutParams(lp);
            sBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!sName.equals(currentPlazaSort)) {
                        currentPlazaSort = sName;
                        setupPlazaSortButtons();
                        isLoadingPlaza = false;
                        loadPlazaSonglists(true);
                    }
                }
            });
            layoutPlazaSortContainer.addView(sBtn);
        }
    }

    private void fetchHotSearchForCurrentPlatform() {
        int pos = spinnerSearchPlatform.getSelectedItemPosition();
        if (pos < 0 || pos >= LxApiHelper.PLATFORM_CODES.length) pos = 0;
        final String code = LxApiHelper.PLATFORM_CODES[pos];
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<String> hotWords = LxApiHelper.fetchHotSearch(code);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        layoutHotSearchTags.removeAllViews();
                        float density = getResources().getDisplayMetrics().density;
                        for (final String word : hotWords) {
                            Button tagBtn = new Button(MainActivity.this);
                            tagBtn.setText(word);
                            tagBtn.setTextColor(0xFFCBD5E1);
                            tagBtn.setTextSize(11);
                            tagBtn.setBackgroundResource(R.drawable.bg_btn_pill);
                            tagBtn.setPadding((int) (10 * density), 0, (int) (10 * density), 0);
                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT, (int) (28 * density));
                            lp.rightMargin = (int) (6 * density);
                            tagBtn.setLayoutParams(lp);
                            tagBtn.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    etSearchKeyword.setText(word);
                                    performSearch(word);
                                }
                            });
                            layoutHotSearchTags.addView(tagBtn);
                        }
                    }
                });
            }
        }).start();
    }

    private void performSearch(final String keyword) {
        if (keyword == null || keyword.trim().length() == 0) return;
        isBrowsingArtistOrAlbum = false;
        if (btnSearchBack != null) btnSearchBack.setVisibility(View.GONE);
        int pPos = spinnerSearchPlatform.getSelectedItemPosition();
        final String source = LxApiHelper.PLATFORM_CODES[pPos >= 0 ? pPos : 0];
        final int typePos = spinnerSearchType.getSelectedItemPosition();
        Toast.makeText(this, "搜索: " + keyword, Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String cleanKey = keyword.trim();
                    String encoded = URLEncoder.encode(cleanKey, "UTF-8");
                    String queryParams;
                    String sourceParam = ("all".equals(source)) ? "" : ("&source=" + source);
                    if (typePos == 1) {
                        queryParams = "search3.view?query=" + encoded + "&artistCount=100&albumCount=0&songCount=0" + sourceParam + "&" + getAuthParams();
                    } else if (typePos == 2) {
                        queryParams = "search3.view?query=" + encoded + "&albumCount=100&artistCount=0&songCount=0" + sourceParam + "&" + getAuthParams();
                    } else {
                        queryParams = "search3.view?query=" + encoded + "&songCount=500&artistCount=0&albumCount=0" + sourceParam + "&" + getAuthParams();
                    }
                    final String jsonStr = requestApi(queryParams);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            handleSearchResultsJson(jsonStr, cleanKey, typePos);
                        }
                    });
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void handleSearchResultsJson(String jsonStr, String query, int typePos) {
        if (jsonStr == null) {
            Toast.makeText(this, "网络连接失败", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
            JSONObject result = root.optJSONObject("searchResult3");
            searchResultsList.clear();
            searchResultsData.clear();
            rawSearchSongResults.clear();
            if (result != null) {
                if (typePos == 1 && result.has("artist")) {
                    Object artObj = result.get("artist");
                    if (artObj instanceof JSONArray) {
                        JSONArray arr = (JSONArray) artObj;
                        for (int i = 0; i < arr.length(); i++) addArtistRow(arr.getJSONObject(i), searchResultsList, searchResultsData);
                    } else if (artObj instanceof JSONObject) {
                        addArtistRow((JSONObject) artObj, searchResultsList, searchResultsData);
                    }
                    tvSearchResultTitle.setText("搜索歌手: " + query + " (点击查看专辑)");
                } else if (typePos == 2 && result.has("album")) {
                    Object albObj = result.get("album");
                    if (albObj instanceof JSONArray) {
                        JSONArray arr = (JSONArray) albObj;
                        for (int i = 0; i < arr.length(); i++) addAlbumRow(arr.getJSONObject(i), searchResultsList, searchResultsData);
                    } else if (albObj instanceof JSONObject) {
                        addAlbumRow((JSONObject) albObj, searchResultsList, searchResultsData);
                    }
                    tvSearchResultTitle.setText("搜索专辑: " + query + " (点击查看歌曲)");
                } else if (result.has("song")) {
                    Object sObj = result.get("song");
                    ArrayList<Map<String, String>> dummy = new ArrayList<Map<String, String>>();
                    if (sObj instanceof JSONArray) {
                        JSONArray arr = (JSONArray) sObj;
                        for (int i = 0; i < arr.length(); i++) addSongRow(arr.getJSONObject(i), rawSearchSongResults, dummy);
                    } else if (sObj instanceof JSONObject) {
                        addSongRow((JSONObject) sObj, rawSearchSongResults, dummy);
                    }
                    applySongDeduplication(cbDedupSongs != null && cbDedupSongs.isChecked(), query);
                }
            }
            searchResultsAdapter.notifyDataSetChanged();
        } catch (Exception ignored) {}
    }

    private void applySongDeduplication(boolean dedup, String query) {
        searchResultsList.clear();
        searchResultsData.clear();
        if (!dedup) {
            for (DisplayEntry e : rawSearchSongResults) {
                searchResultsList.add(e);
                Map<String, String> row = new HashMap<String, String>();
                row.put("title", e.title);
                row.put("subtitle", e.artist + " [" + e.quality + "]");
                searchResultsData.add(row);
            }
        } else {
            LinkedHashMap<String, DisplayEntry> map = new LinkedHashMap<String, DisplayEntry>();
            for (DisplayEntry song : rawSearchSongResults) {
                String key = (song.title + "_" + song.artist).toLowerCase().trim();
                if (!map.containsKey(key) || song.bitRateNumeric > map.get(key).bitRateNumeric) {
                    map.put(key, song);
                }
            }
            for (DisplayEntry bestSong : map.values()) {
                searchResultsList.add(bestSong);
                Map<String, String> row = new HashMap<String, String>();
                row.put("title", bestSong.title);
                row.put("subtitle", bestSong.artist + " [" + bestSong.quality + "]");
                searchResultsData.add(row);
            }
        }
        tvSearchResultTitle.setText("搜索歌曲: " + query + " (共 " + searchResultsList.size() + " 首)");
        searchResultsAdapter.notifyDataSetChanged();
    }

    private void saveSearchStateToBackup() {
        backupSearchList.clear();
        backupSearchList.addAll(searchResultsList);
        backupSearchData.clear();
        backupSearchData.addAll(searchResultsData);
        backupSearchTitle = tvSearchResultTitle.getText().toString();
    }

    private void restoreSearchBackup() {
        isBrowsingArtistOrAlbum = false;
        searchResultsList.clear();
        searchResultsList.addAll(backupSearchList);
        searchResultsData.clear();
        searchResultsData.addAll(backupSearchData);
        tvSearchResultTitle.setText(backupSearchTitle);
        if (btnSearchBack != null) btnSearchBack.setVisibility(View.GONE);
        searchResultsAdapter.notifyDataSetChanged();
    }

    private void fetchArtistAlbums(final String artistId, final String artistName) {
        saveSearchStateToBackup();
        Toast.makeText(this, "正在获取 " + artistName + " 的专辑...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String jsonStr = requestApi("getArtist.view?id=" + URLEncoder.encode(artistId) + "&" + getAuthParams());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (jsonStr == null) return;
                        try {
                            JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
                            JSONObject artistObj = root.optJSONObject("artist");
                            if (artistObj != null && artistObj.has("album")) {
                                isBrowsingArtistOrAlbum = true;
                                searchResultsList.clear();
                                searchResultsData.clear();
                                Object albObj = artistObj.get("album");
                                if (albObj instanceof JSONArray) {
                                    JSONArray arr = (JSONArray) albObj;
                                    for (int i = 0; i < arr.length(); i++) addAlbumRow(arr.getJSONObject(i), searchResultsList, searchResultsData);
                                } else if (albObj instanceof JSONObject) {
                                    addAlbumRow((JSONObject) albObj, searchResultsList, searchResultsData);
                                }
                                tvSearchResultTitle.setText("歌手: " + artistName);
                                if (btnSearchBack != null) btnSearchBack.setVisibility(View.VISIBLE);
                                searchResultsAdapter.notifyDataSetChanged();
                            }
                        } catch (Exception ignored) {}
                    }
                });
            }
        }).start();
    }

    private void fetchAlbumSongs(final String albumId, final String albumName) {
        if (!isBrowsingArtistOrAlbum) saveSearchStateToBackup();
        Toast.makeText(this, "正在获取专辑曲目...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String jsonStr = requestApi("getAlbum.view?id=" + URLEncoder.encode(albumId) + "&" + getAuthParams());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (jsonStr == null) return;
                        try {
                            JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
                            JSONObject album = root.optJSONObject("album");
                            if (album != null && album.has("song")) {
                                isBrowsingArtistOrAlbum = true;
                                searchResultsList.clear();
                                searchResultsData.clear();
                                Object songObj = album.get("song");
                                if (songObj instanceof JSONArray) {
                                    JSONArray arr = (JSONArray) songObj;
                                    for (int i = 0; i < arr.length(); i++) addSongRow(arr.getJSONObject(i), searchResultsList, searchResultsData);
                                } else if (songObj instanceof JSONObject) {
                                    addSongRow((JSONObject) songObj, searchResultsList, searchResultsData);
                                }
                                tvSearchResultTitle.setText("专辑: " + albumName + " (共 " + searchResultsList.size() + " 首)");
                                if (btnSearchBack != null) btnSearchBack.setVisibility(View.VISIBLE);
                                searchResultsAdapter.notifyDataSetChanged();
                            }
                        } catch (Exception ignored) {}
                    }
                });
            }
        }).start();
    }

    private void loadPlazaSonglists(boolean isRefresh) {
        if (isLoadingPlaza) return;
        isBrowsingPlazaSongs = false;
        btnPlazaBack.setVisibility(View.GONE);
        isPlazaSearchMode = false;
        updatePlazaViewModeState();
        int pos = spinnerPlazaPlatform.getSelectedItemPosition();
        final String code = LxApiHelper.PLAZA_PLATFORM_CODES[pos >= 0 ? pos : 0];
        final String tagId = currentPlazaTagId;
        final String tagName = currentPlazaTagName;
        final String snapshotKey = code + "_" + tagId + "_" + currentPlazaSort;

        if (!isRefresh && currentPlazaPage == 1 && PLAZA_SNAPSHOT_CACHE.containsKey(snapshotKey)) {
            ArrayList<DisplayEntry> cachedSnapshot = PLAZA_SNAPSHOT_CACHE.get(snapshotKey);
            if (cachedSnapshot != null && !cachedSnapshot.isEmpty()) {
                plazaPlaylistsList.clear();
                plazaPlaylistsList.addAll(cachedSnapshot);
                plazaPlaylistsData.clear();
                for (DisplayEntry info : plazaPlaylistsList) {
                    Map<String, String> row = new HashMap<String, String>();
                    row.put("title", info.title);
                    row.put("subtitle", (info.artist.length() > 0 ? (info.artist + " · ") : "") + info.subtitle);
                    plazaPlaylistsData.add(row);
                }
                plazaPlaylistsAdapter.notifyDataSetChanged();
                plazaGridAdapter.notifyDataSetChanged();
                tvPlazaCurrentTag.setText("歌单: " + (tagName.length() > 0 ? tagName : "全部") + " · " + currentPlazaSort + " (快照)");
                currentPlazaPage = (plazaPlaylistsList.size() / 30) + 1;
                return;
            }
        }

        final int reqId = ++currentPlazaRequestId;
        if (isRefresh) {
            PLAZA_SNAPSHOT_CACHE.remove(snapshotKey);
            currentPlazaPage = 1;
            hasMorePlaza = true;
            plazaPlaylistsList.clear();
            plazaPlaylistsData.clear();
            plazaPlaylistsAdapter.notifyDataSetChanged();
            plazaGridAdapter.notifyDataSetChanged();
            footerPlazaLoading.setText("加载中...");
        } else {
            if (!hasMorePlaza) return;
            footerPlazaLoading.setText("加载第 " + currentPlazaPage + " 页...");
        }

        tvPlazaCurrentTag.setText("歌单: " + (tagName.length() > 0 ? tagName : "全部") + " · " + currentPlazaSort + " (第 " + currentPlazaPage + " 页)");
        isLoadingPlaza = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<LxApiHelper.PlaylistInfo> list = LxApiHelper.fetchPlaylists(code, tagId, tagName, currentPlazaSort, currentPlazaPage);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (reqId != currentPlazaRequestId) return;
                        isLoadingPlaza = false;
                        if (list != null && !list.isEmpty()) {
                            for (LxApiHelper.PlaylistInfo info : list) {
                                String playCountFmt = formatPlayCount(info.playCount);
                                DisplayEntry entry = new DisplayEntry(info.id, info.name, info.author,
                                        playCountFmt, info.coverImg, "歌单", false);
                                plazaPlaylistsList.add(entry);
                                Map<String, String> row = new HashMap<String, String>();
                                row.put("title", info.name);
                                row.put("subtitle", (info.author.length() > 0 ? (info.author + " · ") : "") + playCountFmt);
                                plazaPlaylistsData.add(row);
                            }
                            PLAZA_SNAPSHOT_CACHE.put(snapshotKey, new ArrayList<DisplayEntry>(plazaPlaylistsList));
                            plazaPlaylistsAdapter.notifyDataSetChanged();
                            plazaGridAdapter.notifyDataSetChanged();
                            if (list.size() < 25) {
                                hasMorePlaza = false;
                                footerPlazaLoading.setText("没有更多歌单了");
                            } else {
                                currentPlazaPage++;
                                footerPlazaLoading.setText("点击加载更多...");
                            }
                        } else {
                            if (plazaPlaylistsList.isEmpty()) {
                                footerPlazaLoading.setText("暂无歌单");
                            } else {
                                hasMorePlaza = false;
                                footerPlazaLoading.setText("没有更多歌单了");
                            }
                        }
                    }
                });
            }
        }).start();
    }

    private String formatPlayCount(String countStr) {
        try {
            long c = Long.parseLong(countStr.replaceAll("[^0-9]", ""));
            if (c > 100000000) return String.format(Locale.US, "%.1f 亿", c / 100000000.0);
            if (c > 10000) return String.format(Locale.US, "%.1f 万", c / 10000.0);
            return String.valueOf(c);
        } catch (Exception e) {
            return countStr != null && countStr.length() > 0 ? countStr : "0";
        }
    }

    private void performPlazaSearch(final String keyword, boolean isRefresh) {
        if (keyword == null || keyword.trim().length() == 0) return;
        if (isLoadingPlaza) return;
        isBrowsingPlazaSongs = false;
        btnPlazaBack.setVisibility(View.VISIBLE);
        isPlazaSearchMode = true;
        currentPlazaSearchKeyword = keyword.trim();
        int pos = spinnerPlazaPlatform.getSelectedItemPosition();
        final String code = LxApiHelper.PLAZA_PLATFORM_CODES[pos >= 0 ? pos : 0];
        final int reqId = ++currentPlazaRequestId;

        if (isRefresh) {
            currentPlazaPage = 1;
            hasMorePlaza = true;
            plazaPlaylistsList.clear();
            plazaPlaylistsData.clear();
            plazaPlaylistsAdapter.notifyDataSetChanged();
            plazaGridAdapter.notifyDataSetChanged();
            footerPlazaLoading.setText("加载中...");
        } else {
            if (!hasMorePlaza) return;
            footerPlazaLoading.setText("加载第 " + currentPlazaPage + " 页...");
        }

        tvPlazaCurrentTag.setText("搜索: " + currentPlazaSearchKeyword + " (第 " + currentPlazaPage + " 页)");
        isLoadingPlaza = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<LxApiHelper.PlaylistInfo> list = LxApiHelper.searchPlaylists(code, currentPlazaSearchKeyword, currentPlazaPage);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (reqId != currentPlazaRequestId) return;
                        isLoadingPlaza = false;
                        if (list != null && !list.isEmpty()) {
                            for (LxApiHelper.PlaylistInfo info : list) {
                                String playCountFmt = formatPlayCount(info.playCount);
                                DisplayEntry entry = new DisplayEntry(info.id, info.name, info.author,
                                        playCountFmt, info.coverImg, "歌单", false);
                                plazaPlaylistsList.add(entry);
                                Map<String, String> row = new HashMap<String, String>();
                                row.put("title", info.name);
                                row.put("subtitle", (info.author.length() > 0 ? (info.author + " · ") : "") + playCountFmt);
                                plazaPlaylistsData.add(row);
                            }
                            plazaPlaylistsAdapter.notifyDataSetChanged();
                            plazaGridAdapter.notifyDataSetChanged();
                            if (list.size() < 25) {
                                hasMorePlaza = false;
                                footerPlazaLoading.setText("没有更多歌单了");
                            } else {
                                currentPlazaPage++;
                                footerPlazaLoading.setText("点击加载更多...");
                            }
                        } else {
                            if (plazaPlaylistsList.isEmpty()) {
                                footerPlazaLoading.setText("未搜索到相关歌单");
                            } else {
                                hasMorePlaza = false;
                                footerPlazaLoading.setText("没有更多歌单了");
                            }
                        }
                    }
                });
            }
        }).start();
    }

    private void showCategoryDialog() {
        int pos = spinnerPlazaPlatform.getSelectedItemPosition();
        final String code = LxApiHelper.PLAZA_PLATFORM_CODES[pos >= 0 ? pos : 0];
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_category_picker, null);
        dialog.setContentView(dialogView);

        TextView tvTitle = (TextView) dialogView.findViewById(R.id.tv_category_dialog_title);
        tvTitle.setText("歌单分类 (" + LxApiHelper.PLAZA_PLATFORM_NAMES[pos] + ")");
        Button btnClose = (Button) dialogView.findViewById(R.id.btn_category_dialog_close);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });

        final LinearLayout layoutContent = (LinearLayout) dialogView.findViewById(R.id.layout_category_content);
        layoutContent.removeAllViews();
        final float density = getResources().getDisplayMetrics().density;
        final TextView tvLoading = new TextView(this);
        tvLoading.setText("加载分类中...");
        tvLoading.setTextColor(0xFF00E5FF);
        tvLoading.setTextSize(13);
        tvLoading.setPadding(0, (int) (20 * density), 0, (int) (20 * density));
        tvLoading.setGravity(Gravity.CENTER);
        layoutContent.addView(tvLoading);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout((int) (520 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final Map<String, ArrayList<LxApiHelper.CategoryTag>> categories = LxApiHelper.fetchDynamicCategories(code);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!dialog.isShowing()) return;
                        layoutContent.removeAllViews();
                        if (categories == null || categories.isEmpty()) {
                            TextView tvEmpty = new TextView(MainActivity.this);
                            tvEmpty.setText("分类获取失败，请重试");
                            tvEmpty.setTextColor(0xFFFF5252);
                            tvEmpty.setPadding(0, 30, 0, 30);
                            tvEmpty.setGravity(Gravity.CENTER);
                            layoutContent.addView(tvEmpty);
                            return;
                        }

                        Button btnAll = new Button(MainActivity.this);
                        btnAll.setText("全部歌单");
                        btnAll.setTextSize(12);
                        boolean isAllSelected = (currentPlazaTagId == null || currentPlazaTagId.length() == 0 || "全部".equals(currentPlazaTagId));
                        btnAll.setTextColor(isAllSelected ? 0xFF10141A : 0xFFE0E0E0);
                        btnAll.setBackgroundResource(isAllSelected ? R.drawable.bg_category_tag_selected : R.drawable.bg_category_tag_normal);
                        btnAll.setPadding((int) (14 * density), 0, (int) (14 * density), 0);
                        LinearLayout.LayoutParams allLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (30 * density));
                        btnAll.setLayoutParams(allLp);
                        btnAll.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                currentPlazaTagId = "";
                                currentPlazaTagName = "";
                                btnPlazaCategory.setText("全部分类");
                                dialog.dismiss();
                                isLoadingPlaza = false;
                                loadPlazaSonglists(true);
                            }
                        });
                        layoutContent.addView(btnAll);

                        for (Map.Entry<String, ArrayList<LxApiHelper.CategoryTag>> entry : categories.entrySet()) {
                            TextView tvCatName = new TextView(MainActivity.this);
                            tvCatName.setText(entry.getKey());
                            tvCatName.setTextColor(0xFF00E5FF);
                            tvCatName.setTextSize(13);
                            tvCatName.setTypeface(null, Typeface.BOLD);
                            tvCatName.setPadding(0, (int) (12 * density), 0, (int) (6 * density));
                            layoutContent.addView(tvCatName);

                            ArrayList<LxApiHelper.CategoryTag> tags = entry.getValue();
                            LinearLayout rowLayout = null;
                            int countInRow = 0;
                            for (final LxApiHelper.CategoryTag tag : tags) {
                                if (countInRow % 5 == 0) {
                                    rowLayout = new LinearLayout(MainActivity.this);
                                    rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                                    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                                    rowLp.bottomMargin = (int) (6 * density);
                                    rowLayout.setLayoutParams(rowLp);
                                    layoutContent.addView(rowLayout);
                                }
                                final Button tBtn = new Button(MainActivity.this);
                                tBtn.setText(tag.name);
                                tBtn.setTextSize(11);
                                boolean isSelected = (tag.id != null && tag.id.equals(currentPlazaTagId)) || tag.name.equals(currentPlazaTagName);
                                tBtn.setTextColor(isSelected ? 0xFF10141A : 0xFFCBD5E1);
                                tBtn.setBackgroundResource(isSelected ? R.drawable.bg_category_tag_selected : R.drawable.bg_category_tag_normal);
                                tBtn.setPadding((int) (10 * density), 0, (int) (10 * density), 0);
                                LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, (int) (28 * density), 1.0f);
                                tLp.rightMargin = (int) (4 * density);
                                tBtn.setLayoutParams(tLp);
                                tBtn.setOnClickListener(new View.OnClickListener() {
                                    @Override public void onClick(View v) {
                                        currentPlazaTagId = tag.id;
                                        currentPlazaTagName = tag.name;
                                        btnPlazaCategory.setText(tag.name);
                                        dialog.dismiss();
                                        isLoadingPlaza = false;
                                        loadPlazaSonglists(true);
                                    }
                                });
                                if (rowLayout != null) rowLayout.addView(tBtn);
                                countInRow++;
                            }
                        }
                    }
                });
            }
        }).start();
    }

    private void promptImportPlaylist() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(20, 10, 20, 10);
        final Spinner spSource = new Spinner(this);
        spSource.setAdapter(new SimpleDarkAdapter(LxApiHelper.PLAZA_PLATFORM_NAMES));
        box.addView(spSource);
        final EditText input = new EditText(this);
        input.setHint("输入外部歌单 ID...");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF777777);
        box.addView(input);
        new AlertDialog.Builder(this)
                .setTitle("导入歌单")
                .setView(box)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String text = input.getText().toString().trim();
                        int srcIdx = spSource.getSelectedItemPosition();
                        String srcCode = LxApiHelper.PLAZA_PLATFORM_CODES[srcIdx];
                        if (text.length() > 0) {
                            String playlistId = text;
                            if (text.contains("id=")) {
                                int s = text.indexOf("id=") + 3;
                                int e = text.indexOf("&", s);
                                playlistId = e > s ? text.substring(s, e) : text.substring(s);
                            }
                            DisplayEntry entry = new DisplayEntry(srcCode + "_" + playlistId, "导入歌单", "外部", "", null, "歌单", false);
                            openSonglistDetails(entry);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openSonglistDetails(final DisplayEntry playlistEntry) {
        if (!isBrowsingPlazaSongs) {
            backupPlazaList.clear();
            backupPlazaList.addAll(plazaPlaylistsList);
            backupPlazaData.clear();
            backupPlazaData.addAll(plazaPlaylistsData);
            backupPlazaTitle = tvPlazaCurrentTag.getText().toString();
        }
        isBrowsingPlazaSongs = true;
        btnPlazaBack.setVisibility(View.VISIBLE);
        updatePlazaViewModeState();
        tvPlazaCurrentTag.setText("歌单: " + playlistEntry.title);
        plazaPlaylistsList.clear();
        plazaPlaylistsData.clear();
        plazaPlaylistsAdapter.notifyDataSetChanged();
        footerPlazaLoading.setText("正在加载曲目...");
        final String targetQuality = getPlazaEffectiveBitrateLabel();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<DisplayEntry> songs = LxApiHelper.fetchPlaylistSongs(playlistEntry.id, targetQuality);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isBrowsingPlazaSongs) return;
                        plazaPlaylistsList.clear();
                        plazaPlaylistsData.clear();
                        for (DisplayEntry s : songs) {
                            plazaPlaylistsList.add(s);
                            Map<String, String> row = new HashMap<String, String>();
                            row.put("title", s.title);
                            row.put("subtitle", s.artist + " [" + s.quality + "]");
                            plazaPlaylistsData.add(row);
                        }
                        plazaPlaylistsAdapter.notifyDataSetChanged();
                        footerPlazaLoading.setText("已加载完成 (共 " + plazaPlaylistsList.size() + " 首)");
                    }
                });
            }
        }).start();
    }

    private void exitPlazaSongsToPlaylists() {
        isBrowsingPlazaSongs = false;
        btnPlazaBack.setVisibility(View.GONE);
        plazaPlaylistsList.clear();
        plazaPlaylistsList.addAll(backupPlazaList);
        plazaPlaylistsData.clear();
        plazaPlaylistsData.addAll(backupPlazaData);
        tvPlazaCurrentTag.setText(backupPlazaTitle);
        updatePlazaViewModeState();
        plazaPlaylistsAdapter.notifyDataSetChanged();
        plazaGridAdapter.notifyDataSetChanged();
    }

    // ================= [第一部分 结束 / 第二部分 从此处衔接] =================
    private void loadLeaderboardBoards() {
        int pos = spinnerRankingPlatform.getSelectedItemPosition();
        final String code = LxApiHelper.PLAZA_PLATFORM_CODES[pos >= 0 ? pos : 0];
        tvRankingBoardTitle.setText("正在加载榜单...");
        rankingBoardsList.clear();
        rankingBoardsData.clear();
        rankingBoardsAdapter.notifyDataSetChanged();
        rankingSongsList.clear();
        rankingSongsData.clear();
        rankingSongsAdapter.notifyDataSetChanged();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<LxApiHelper.LeaderboardInfo> boards = LxApiHelper.fetchDynamicLeaderboards(code);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        rankingBoardsList.clear();
                        rankingBoardsData.clear();
                        if (boards != null && !boards.isEmpty()) {
                            for (LxApiHelper.LeaderboardInfo b : boards) {
                                rankingBoardsList.add(new DisplayEntry(b.id, b.name, "", "榜单", b.coverImg, "榜单", false));
                                Map<String, String> row = new HashMap<String, String>();
                                row.put("title", b.name);
                                rankingBoardsData.add(row);
                            }
                            rankingBoardsAdapter.notifyDataSetChanged();
                            loadLeaderboardSongs(rankingBoardsList.get(0));
                        } else {
                            tvRankingBoardTitle.setText("暂无榜单数据");
                            rankingBoardsAdapter.notifyDataSetChanged();
                        }
                    }
                });
            }
        }).start();
    }

    private void loadLeaderboardSongs(final DisplayEntry board) {
        tvRankingBoardTitle.setText(board.title);
        rankingSongsList.clear();
        rankingSongsData.clear();
        rankingSongsAdapter.notifyDataSetChanged();
        int pos = spinnerRankingPlatform.getSelectedItemPosition();
        final String code = LxApiHelper.PLAZA_PLATFORM_CODES[pos >= 0 ? pos : 0];
        final String targetQuality = getPlazaEffectiveBitrateLabel();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<DisplayEntry> songs = LxApiHelper.fetchLeaderboardSongs(code, board.id, targetQuality);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        rankingSongsList.clear();
                        rankingSongsData.clear();
                        for (DisplayEntry s : songs) {
                            rankingSongsList.add(s);
                            Map<String, String> row = new HashMap<String, String>();
                            row.put("title", s.title);
                            row.put("subtitle", s.artist + " [" + s.quality + "]");
                            rankingSongsData.add(row);
                        }
                        rankingSongsAdapter.notifyDataSetChanged();
                        tvRankingBoardTitle.setText(board.title + " (共 " + rankingSongsList.size() + " 首)");
                    }
                });
            }
        }).start();
    }

    private void showFavAndCustomPlaylists() {
        btnFavBack.setVisibility(View.GONE);
        tvFavTitle.setText("我的歌单与收藏");
        currentActiveFavPlaylistId = null;
        favPlaylistsList.clear();
        favPlaylistsData.clear();

        favPlaylistsList.add(new DisplayEntry("fav_entry", "特别收藏", "本地", "共 " + favSongIds.size() + " 首歌曲", null, "歌单", false));
        Map<String, String> favRow = new HashMap<String, String>();
        favRow.put("title", "特别收藏");
        favRow.put("subtitle", "共 " + favSongIds.size() + " 首歌曲");
        favPlaylistsData.add(favRow);

        favPlaylistsList.add(new DisplayEntry("local_featured", "精选专区", "本地", "共 " + featuredSongs.size() + " 首歌曲", null, "歌单", false));
        Map<String, String> featRow = new HashMap<String, String>();
        featRow.put("title", "精选专区");
        featRow.put("subtitle", "共 " + featuredSongs.size() + " 首歌曲");
        favPlaylistsData.add(featRow);

        favPlaylistsList.add(new DisplayEntry("local_car", "车载专区", "本地", "共 " + carSongs.size() + " 首歌曲", null, "歌单", false));
        Map<String, String> carRow = new HashMap<String, String>();
        carRow.put("title", "车载专区");
        carRow.put("subtitle", "共 " + carSongs.size() + " 首歌曲");
        favPlaylistsData.add(carRow);

        for (DisplayEntry e : rawServerUserPlaylists) {
            favPlaylistsList.add(e);
            Map<String, String> row = new HashMap<String, String>();
            row.put("title", "自建: " + e.title);
            row.put("subtitle", e.quality + " (服务器)");
            favPlaylistsData.add(row);
        }
        favPlaylistsAdapter.notifyDataSetChanged();
    }

    private void syncFavoritesAndPlaylists(final boolean showToast) {
        if (showToast) Toast.makeText(this, "正在同步云端收藏...", Toast.LENGTH_SHORT).show();
        final String currentUser = prefs.getString("user", "admin").trim();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final Set<String> updatedFavIds = new HashSet<String>();
                String starredJson = requestApi("getStarred2.view?" + getAuthParams());
                if (starredJson == null || !starredJson.contains("\"song\"")) {
                    starredJson = requestApi("getStarred.view?" + getAuthParams());
                }
                if (starredJson != null) {
                    try {
                        JSONObject root = new JSONObject(starredJson).getJSONObject("subsonic-response");
                        JSONObject starred = root.optJSONObject("starred2");
                        if (starred == null) starred = root.optJSONObject("starred");
                        if (starred != null && starred.has("song")) {
                            Object songObj = starred.get("song");
                            if (songObj instanceof JSONArray) {
                                JSONArray arr = (JSONArray) songObj;
                                for (int i = 0; i < arr.length(); i++) updatedFavIds.add(arr.getJSONObject(i).getString("id"));
                            } else if (songObj instanceof JSONObject) {
                                updatedFavIds.add(((JSONObject) songObj).getString("id"));
                            }
                        }
                    } catch (Exception ignored) {}
                }

                final ArrayList<DisplayEntry> updatedPlaylists = new ArrayList<DisplayEntry>();
                String plJson = requestApi("getPlaylists.view?" + getAuthParams());
                if (plJson != null) {
                    try {
                        JSONObject root = new JSONObject(plJson).getJSONObject("subsonic-response");
                        JSONObject playlistsObj = root.optJSONObject("playlists");
                        if (playlistsObj != null && playlistsObj.has("playlist")) {
                            Object plObj = playlistsObj.get("playlist");
                            if (plObj instanceof JSONArray) {
                                JSONArray arr = (JSONArray) plObj;
                                for (int i = 0; i < arr.length(); i++) parseUserPlaylistItem(arr.getJSONObject(i), currentUser, updatedPlaylists);
                            } else if (plObj instanceof JSONObject) {
                                parseUserPlaylistItem((JSONObject) plObj, currentUser, updatedPlaylists);
                            }
                        }
                    } catch (Exception ignored) {}
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!updatedFavIds.isEmpty()) {
                            favSongIds.clear();
                            favSongIds.addAll(updatedFavIds);
                            saveFavSet();
                        }
                        rawServerUserPlaylists.clear();
                        rawServerUserPlaylists.addAll(updatedPlaylists);
                        if (currentPage == PAGE_FAV && currentActiveFavPlaylistId == null) {
                            showFavAndCustomPlaylists();
                        }
                        if (showToast) Toast.makeText(MainActivity.this, "同步成功", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private void parseUserPlaylistItem(JSONObject p, String currentUser, ArrayList<DisplayEntry> sink) throws Exception {
        String name = p.getString("name");
        int count = p.optInt("songCount", 0);
        String id = p.getString("id");
        String nLower = name.trim().toLowerCase();
        if ("收藏".equals(name) || "starred".equals(nLower) || "favorites".equals(nLower) || "favourite".equals(nLower)) return;
        if (name.contains("精选") || name.startsWith("Top") || name.startsWith("TOP") || name.endsWith("榜") || name.contains("推荐") || name.contains("车载")) return;
        if (p.has("owner") && currentUser.length() > 0 && !currentUser.equalsIgnoreCase(p.optString("owner", "").trim())) return;
        if (p.optBoolean("public", false) && p.has("owner") && currentUser.length() > 0 && !currentUser.equalsIgnoreCase(p.optString("owner", "").trim())) return;
        sink.add(new DisplayEntry(id, name, "", "曲目: " + count + " 首", null, count + " 首", false));
    }

    private void openFavPlaylistSongs(final DisplayEntry playlistEntry) {
        currentActiveFavPlaylistId = playlistEntry.id;
        btnFavBack.setVisibility(View.VISIBLE);
        tvFavTitle.setText(playlistEntry.title);
        favPlaylistsList.clear();
        favPlaylistsData.clear();
        favPlaylistsAdapter.notifyDataSetChanged();

        if ("fav_entry".equals(playlistEntry.id)) {
            fetchServerFavoriteSongs();
            return;
        }
        if ("local_featured".equals(playlistEntry.id)) {
            for (DisplayEntry e : featuredSongs) {
                favPlaylistsList.add(e);
                Map<String, String> row = new HashMap<String, String>();
                row.put("title", e.title);
                row.put("subtitle", e.artist + " [" + e.quality + "]");
                favPlaylistsData.add(row);
            }
            favPlaylistsAdapter.notifyDataSetChanged();
            return;
        }
        if ("local_car".equals(playlistEntry.id)) {
            for (DisplayEntry e : carSongs) {
                favPlaylistsList.add(e);
                Map<String, String> row = new HashMap<String, String>();
                row.put("title", e.title);
                row.put("subtitle", e.artist + " [" + e.quality + "]");
                favPlaylistsData.add(row);
            }
            favPlaylistsAdapter.notifyDataSetChanged();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<DisplayEntry> parsedList = new ArrayList<DisplayEntry>();
                final ArrayList<Map<String, String>> parsedData = new ArrayList<Map<String, String>>();
                String res = requestApi("getPlaylist.view?id=" + URLEncoder.encode(playlistEntry.id) + "&" + getAuthParams());
                if (res != null) {
                    try {
                        JSONObject root = new JSONObject(res).getJSONObject("subsonic-response");
                        JSONObject pl = root.optJSONObject("playlist");
                        if (pl != null && pl.has("entry")) {
                            Object obj = pl.get("entry");
                            if (obj instanceof JSONArray) {
                                JSONArray arr = (JSONArray) obj;
                                for (int i = 0; i < arr.length(); i++) addSongRow(arr.getJSONObject(i), parsedList, parsedData);
                            } else if (obj instanceof JSONObject) {
                                addSongRow((JSONObject) obj, parsedList, parsedData);
                            }
                        }
                    } catch (Exception ignored) {}
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        favPlaylistsList.clear();
                        favPlaylistsList.addAll(parsedList);
                        favPlaylistsData.clear();
                        favPlaylistsData.addAll(parsedData);
                        favPlaylistsAdapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    private void fetchServerFavoriteSongs() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<DisplayEntry> parsedList = new ArrayList<DisplayEntry>();
                final ArrayList<Map<String, String>> parsedData = new ArrayList<Map<String, String>>();
                String jsonStr = requestApi("getStarred2.view?" + getAuthParams());
                if (jsonStr == null || !jsonStr.contains("\"song\"")) {
                    jsonStr = requestApi("getStarred.view?" + getAuthParams());
                }
                if (jsonStr != null) {
                    try {
                        JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
                        JSONObject starred = root.optJSONObject("starred2");
                        if (starred == null) starred = root.optJSONObject("starred");
                        if (starred != null && starred.has("song")) {
                            Object songObj = starred.get("song");
                            if (songObj instanceof JSONArray) {
                                JSONArray arr = (JSONArray) songObj;
                                for (int i = 0; i < arr.length(); i++) addSongRow(arr.getJSONObject(i), parsedList, parsedData);
                            } else if (songObj instanceof JSONObject) {
                                addSongRow((JSONObject) songObj, parsedList, parsedData);
                            }
                        }
                    } catch (Exception ignored) {}
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        favPlaylistsList.clear();
                        favPlaylistsList.addAll(parsedList);
                        favPlaylistsData.clear();
                        favPlaylistsData.addAll(parsedData);
                        favPlaylistsAdapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    private void scanLocalMusicFiles() {
        String customPath = prefs.getString("download_path", getDefaultDownloadPath());
        tvLocalPathStatus.setText("扫描路径: " + customPath);
        localMusicList.clear();
        localMusicData.clear();
        localMusicAdapter.notifyDataSetChanged();

        File dir = new File(customPath);
        if (!dir.exists() || !dir.isDirectory()) dir.mkdirs();
        File[] files = dir.listFiles();
        if (files != null) {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            for (File f : files) {
                if (f.isFile()) {
                    String name = f.getName().toLowerCase();
                    if (name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".wav")
                            || name.endsWith(".ogg") || name.endsWith(".aac") || name.endsWith(".m4a")) {
                        String title = f.getName();
                        String artist = "本地";
                        try {
                            mmr.setDataSource(f.getAbsolutePath());
                            String metaTitle = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                            String metaArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                            if (metaTitle != null && metaTitle.trim().length() > 0) title = metaTitle.trim();
                            if (metaArtist != null && metaArtist.trim().length() > 0) artist = metaArtist.trim();
                        } catch (Exception ignored) {}
                        double mb = f.length() / (1024.0 * 1024.0);
                        DisplayEntry entry = new DisplayEntry("local_file:" + f.getAbsolutePath(),
                                title, artist, artist + String.format(Locale.US, " (%.1f MB)", mb), null, "本地", true, 320, f.getAbsolutePath());
                        localMusicList.add(entry);
                        Map<String, String> row = new HashMap<String, String>();
                        row.put("title", title);
                        row.put("subtitle", artist + String.format(Locale.US, " [%.1f MB]", mb));
                        localMusicData.add(row);
                    }
                }
            }
            try { mmr.release(); } catch (Throwable ignored) {}
        }
        localMusicAdapter.notifyDataSetChanged();
        Toast.makeText(this, "扫描完成，共 " + localMusicList.size() + " 首歌曲", Toast.LENGTH_SHORT).show();
    }

    private void setupBitrateSpinners() {
        BitrateSpinnerAdapter adapterConfig = new BitrateSpinnerAdapter(BITRATE_LABELS);
        BitrateSpinnerAdapter adapterDetail = new BitrateSpinnerAdapter(BITRATE_LABELS);
        spinnerConfigBitrate.setAdapter(adapterConfig);
        spinnerDetailBitrate.setAdapter(adapterDetail);

        int initialIndex = getBitrateIndex(getSavedBitrate());
        spinnerConfigBitrate.setSelection(initialIndex);
        spinnerDetailBitrate.setSelection(initialIndex);

        if (spinnerPlazaBitrate != null) {
            BitrateSpinnerAdapter adapterPlaza = new BitrateSpinnerAdapter(PLAZA_BITRATE_LABELS);
            spinnerPlazaBitrate.setAdapter(adapterPlaza);
            int plazaIdx = getPlazaBitrateIndex(prefs.getString("plaza_bitrate", "follow"));
            spinnerPlazaBitrate.setSelection(plazaIdx);
            spinnerPlazaBitrate.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (isSpinnersInitializing) return;
                    String val = PLAZA_BITRATE_VALUES[position];
                    prefs.edit().putString("plaza_bitrate", val).apply();
                    Toast.makeText(MainActivity.this, "已设置广场音质: " + PLAZA_BITRATE_LABELS[position], Toast.LENGTH_SHORT).show();
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isSpinnersInitializing) return;
                String newBitrate = BITRATE_VALUES[position];
                String oldBitrate = getSavedBitrate();
                if (!newBitrate.equals(oldBitrate)) {
                    prefs.edit().putString("default_bitrate", newBitrate).apply();
                    if (parent == spinnerConfigBitrate) {
                        spinnerDetailBitrate.setSelection(position);
                    } else {
                        spinnerConfigBitrate.setSelection(position);
                    }
                    ArrayList<MusicService.SongItem> queue = MusicService.getPlaylist();
                    int curIdx = MusicService.getCurrentIndex();
                    String origQuality = "";
                    if (queue != null && curIdx >= 0 && curIdx < queue.size()) {
                        origQuality = queue.get(curIdx).quality;
                    }
                    tvDetailQuality.setText(getBitrateDisplay(newBitrate, origQuality));
                    onBitrateChanged(newBitrate, BITRATE_LABELS[position]);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };
        spinnerConfigBitrate.setOnItemSelectedListener(listener);
        spinnerDetailBitrate.setOnItemSelectedListener(listener);

        new Handler().postDelayed(new Runnable() {
            @Override public void run() { isSpinnersInitializing = false; }
        }, 500);
    }

    private void onBitrateChanged(String newBitrate, String label) {
        Toast.makeText(this, "正在切换音质: " + label, Toast.LENGTH_SHORT).show();
        ArrayList<MusicService.SongItem> queue = MusicService.getPlaylist();
        int curIdx = MusicService.getCurrentIndex();
        if (queue != null && curIdx >= 0 && curIdx < queue.size()) {
            MusicService.SongItem currentSong = queue.get(curIdx);
            File cachedFile = CacheManager.getSongFile(MainActivity.this, currentSong.id);
            if (cachedFile.exists()) cachedFile.delete();
            currentSong.streamUrl = buildStreamUrl(currentSong.id, newBitrate);
            currentSong.quality = getBitrateDisplay(newBitrate, currentSong.quality);
            Intent intent = new Intent(MainActivity.this, MusicService.class);
            intent.setAction(MusicService.ACTION_PLAY_INDEX);
            intent.putExtra("target_index", curIdx);
            startService(intent);
        }
    }

    private String getPlazaEffectiveBitrateLabel() {
        String plazaBitrate = prefs.getString("plaza_bitrate", "follow");
        if ("follow".equalsIgnoreCase(plazaBitrate)) {
            String streamBitrate = getSavedBitrate();
            if ("auto".equalsIgnoreCase(streamBitrate)) return "320K MP3";
            return streamBitrate.toUpperCase(Locale.US) + (streamBitrate.matches("\\d+") ? "K" : "");
        }
        return plazaBitrate.toUpperCase(Locale.US) + (plazaBitrate.matches("\\d+") ? "K" : "");
    }

    private void playSongInList(ArrayList<DisplayEntry> list, DisplayEntry entry) {
        ArrayList<MusicService.SongItem> queue = new ArrayList<MusicService.SongItem>();
        int clickedSongIndex = 0;
        String defaultQuality = getPlazaEffectiveBitrateLabel();

        for (int i = 0; i < list.size(); i++) {
            DisplayEntry item = list.get(i);
            if (item.isSong) {
                if (item.id.equals(entry.id)) {
                    clickedSongIndex = queue.size();
                }
                String playUrl;
                if (item.localPath != null && item.localPath.length() > 0) {
                    playUrl = "file://" + item.localPath;
                } else if (item.id != null && item.id.startsWith("local_file:")) {
                    playUrl = "file://" + item.id.substring(11);
                } else {
                    playUrl = buildStreamUrl(item.id, getSavedBitrate());
                }
                String qStr = (item.quality != null && item.quality.length() > 0) ? item.quality : defaultQuality;
                queue.add(new MusicService.SongItem(item.id, item.title, item.artist, playUrl, item.coverArt, qStr));
            }
        }

        MusicService.setQueue(queue, clickedSongIndex, MainActivity.this);
        refreshQueueList();
        scrollToCurrentPlayingInQueue();
        Toast.makeText(this, "正在播放: " + entry.title, Toast.LENGTH_SHORT).show();
    }

    private void setupSpinners() {
        spinnerSearchPlatform.setAdapter(new SimpleDarkAdapter(LxApiHelper.PLATFORM_NAMES));
        spinnerSearchPlatform.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                fetchHotSearchForCurrentPlatform();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerSearchType.setAdapter(new SimpleDarkAdapter(SEARCH_TYPES));

        spinnerPlazaPlatform.setAdapter(new SimpleDarkAdapter(LxApiHelper.PLAZA_PLATFORM_NAMES));
        spinnerPlazaPlatform.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isSpinnersInitializing) return;
                currentPlazaTagId = "";
                currentPlazaTagName = "";
                btnPlazaCategory.setText("全部分类");
                setupPlazaSortButtons();
                isLoadingPlaza = false;
                loadPlazaSonglists(true);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerRankingPlatform.setAdapter(new SimpleDarkAdapter(LxApiHelper.PLAZA_PLATFORM_NAMES));
        spinnerRankingPlatform.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                loadLeaderboardBoards();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // ================= 3D 页面全屏三模式切换与应用 =================
    private void cycleNavBarMode() {
        currentNavMode = (currentNavMode + 1) % 3;
        prefs.edit().putInt("visualizer_nav_mode", currentNavMode).apply();
        applyNavBarMode();
    }

    private void applyNavBarMode() {
        if (layoutVisualizerOverlay == null || layoutVisualizerOverlay.getVisibility() != View.VISIBLE) {
            return;
        }
        navBarHandler.removeCallbacks(navBarHideRunnable);
        View decorView = getWindow().getDecorView();

        if (currentNavMode == NAV_MODE_LOW_PROFILE) {
            if (btnVisualizerNavMode != null) btnVisualizerNavMode.setText("全屏:灭灯");
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LOW_PROFILE);
            Toast.makeText(this, "已切换: 全屏灭灯", Toast.LENGTH_SHORT).show();
        } else if (currentNavMode == NAV_MODE_HIDE) {
            if (btnVisualizerNavMode != null) btnVisualizerNavMode.setText("全屏:强隐");
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LOW_PROFILE);
            Toast.makeText(this, "已切换: 全屏强隐", Toast.LENGTH_SHORT).show();
        } else {
            if (btnVisualizerNavMode != null) btnVisualizerNavMode.setText("全屏:常驻");
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            Toast.makeText(this, "已切换: 全屏常驻", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupListeners() {
        View.OnClickListener exitListener = new View.OnClickListener() {
            @Override public void onClick(View v) { performAppExit(); }
        };
        btnExitApp.setOnClickListener(exitListener);
        btnDetailExitApp.setOnClickListener(exitListener);

        btnSearchSubmit.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                performSearch(etSearchKeyword.getText().toString().trim());
            }
        });

        btnSearchBack = (Button) findViewById(R.id.btn_search_back);
        if (btnSearchBack != null) {
            btnSearchBack.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { restoreSearchBackup(); }
            });
        }

        View.OnClickListener hotToggleListener = new View.OnClickListener() {
            @Override public void onClick(View v) { toggleHotSearchBox(); }
        };
        if (btnToggleHotSearch != null) btnToggleHotSearch.setOnClickListener(hotToggleListener);
        if (tvHotSearchTitle != null) tvHotSearchTitle.setOnClickListener(hotToggleListener);

        tvSearchResultTitle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (isBrowsingArtistOrAlbum) restoreSearchBackup();
            }
        });

        lvSearchResults.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= searchResultsList.size()) return;
                DisplayEntry entry = searchResultsList.get(position);
                if (!entry.isSong) {
                    if (entry.id.startsWith("album_")) {
                        fetchAlbumSongs(entry.id.substring(6), entry.title);
                    } else if (entry.id.startsWith("artist_")) {
                        fetchArtistAlbums(entry.id.substring(7), entry.title);
                    }
                } else {
                    playSongInList(searchResultsList, entry);
                }
            }
        });

        lvSearchResults.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < searchResultsList.size()) {
                    DisplayEntry entry = searchResultsList.get(position);
                    if (entry.isSong) {
                        showSongLongClickMenu(entry, position);
                    } else {
                        showArtistOrAlbumLongClickMenu(entry);
                    }
                    return true;
                }
                return false;
            }
        });

        btnPlazaCategory.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showCategoryDialog(); }
        });

        btnPlazaViewMode.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                isPlazaGridMode = !isPlazaGridMode;
                prefs.edit().putBoolean("is_plaza_grid_mode", isPlazaGridMode).apply();
                updatePlazaViewModeState();
            }
        });

        btnPlazaRefresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int pos = spinnerPlazaPlatform.getSelectedItemPosition();
                String code = LxApiHelper.PLAZA_PLATFORM_CODES[pos >= 0 ? pos : 0];
                LxApiHelper.clearCategoryCache(code);
                if (isPlazaSearchMode) performPlazaSearch(currentPlazaSearchKeyword, true);
                else loadPlazaSonglists(true);
            }
        });

        btnPlazaImport.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { promptImportPlaylist(); }
        });

        btnPlazaBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                exitPlazaSongsToPlaylists();
            }
        });

        btnPlazaSearchSubmit.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                performPlazaSearch(etPlazaSearch.getText().toString().trim(), true);
            }
        });

        lvPlazaPlaylists.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= plazaPlaylistsList.size()) return;
                DisplayEntry entry = plazaPlaylistsList.get(position);
                if (!entry.isSong) openSonglistDetails(entry);
                else playSongInList(plazaPlaylistsList, entry);
            }
        });

        lvPlazaPlaylists.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < plazaPlaylistsList.size()) {
                    DisplayEntry entry = plazaPlaylistsList.get(position);
                    if (entry.isSong) showSongLongClickMenu(entry, position);
                    else showPlaylistLongClickMenu(entry);
                    return true;
                }
                return false;
            }
        });

        gvPlazaPlaylists.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= plazaPlaylistsList.size()) return;
                DisplayEntry entry = plazaPlaylistsList.get(position);
                if (!entry.isSong) openSonglistDetails(entry);
                else playSongInList(plazaPlaylistsList, entry);
            }
        });

        gvPlazaPlaylists.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < plazaPlaylistsList.size()) {
                    DisplayEntry entry = plazaPlaylistsList.get(position);
                    if (entry.isSong) showSongLongClickMenu(entry, position);
                    else showPlaylistLongClickMenu(entry);
                    return true;
                }
                return false;
            }
        });

        footerPlazaLoading.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (hasMorePlaza && !isLoadingPlaza) {
                    if (isPlazaSearchMode) performPlazaSearch(currentPlazaSearchKeyword, false);
                    else loadPlazaSonglists(false);
                }
            }
        });

        AbsListView.OnScrollListener autoScrollLoader = new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_FLING) {
                    isGridFlinging = true;
                } else {
                    if (isGridFlinging) {
                        isGridFlinging = false;
                        plazaGridAdapter.notifyDataSetChanged();
                    }
                }
            }
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (!isBrowsingArtistOrAlbum && !isBrowsingPlazaSongs && btnPlazaBack.getVisibility() != View.VISIBLE) {
                    if (firstVisibleItem + visibleItemCount >= totalItemCount - 2 && totalItemCount > 5) {
                        if (hasMorePlaza && !isLoadingPlaza) {
                            if (isPlazaSearchMode) performPlazaSearch(currentPlazaSearchKeyword, false);
                            else loadPlazaSonglists(false);
                        }
                    }
                }
            }
        };
        lvPlazaPlaylists.setOnScrollListener(autoScrollLoader);
        gvPlazaPlaylists.setOnScrollListener(autoScrollLoader);

        btnRankingRefresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int pos = spinnerRankingPlatform.getSelectedItemPosition();
                String code = LxApiHelper.PLAZA_PLATFORM_CODES[pos >= 0 ? pos : 0];
                LxApiHelper.clearLeaderboardCache(code);
                loadLeaderboardBoards();
            }
        });

        lvRankingBoards.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < rankingBoardsList.size()) {
                    loadLeaderboardSongs(rankingBoardsList.get(position));
                }
            }
        });

        lvRankingSongs.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < rankingSongsList.size()) {
                    playSongInList(rankingSongsList, rankingSongsList.get(position));
                }
            }
        });

        lvRankingSongs.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < rankingSongsList.size()) {
                    showSongLongClickMenu(rankingSongsList.get(position), position);
                    return true;
                }
                return false;
            }
        });

        if (btnFavRefresh != null) {
            btnFavRefresh.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (currentActiveFavPlaylistId != null) {
                        Toast.makeText(MainActivity.this, "刷新歌单曲目中...", Toast.LENGTH_SHORT).show();
                        if ("fav_entry".equals(currentActiveFavPlaylistId)) {
                            fetchServerFavoriteSongs();
                        } else if ("local_featured".equals(currentActiveFavPlaylistId) || "local_car".equals(currentActiveFavPlaylistId)) {
                            loadLocalPlaylists();
                            openFavPlaylistSongs(new DisplayEntry(currentActiveFavPlaylistId, tvFavTitle.getText().toString(), "", "", null, "", false));
                        } else {
                            openFavPlaylistSongs(new DisplayEntry(currentActiveFavPlaylistId, tvFavTitle.getText().toString(), "", "", null, "", false));
                        }
                    } else {
                        syncFavoritesAndPlaylists(true);
                    }
                }
            });
        }

        btnCreatePlaylist.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { promptCreatePlaylist(); }
        });

        btnFavBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showFavAndCustomPlaylists(); }
        });

        lvFavPlaylists.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= favPlaylistsList.size()) return;
                DisplayEntry entry = favPlaylistsList.get(position);
                if (!entry.isSong) openFavPlaylistSongs(entry);
                else playSongInList(favPlaylistsList, entry);
            }
        });

        lvFavPlaylists.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < favPlaylistsList.size()) {
                    DisplayEntry entry = favPlaylistsList.get(position);
                    if (entry.isSong) showSongLongClickMenu(entry, position);
                    else showPlaylistLongClickMenu(entry);
                    return true;
                }
                return false;
            }
        });

        btnScanLocalMusic.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { scanLocalMusicFiles(); }
        });

        lvLocalMusic.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < localMusicList.size()) {
                    playSongInList(localMusicList, localMusicList.get(position));
                }
            }
        });

        lvLocalMusic.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < localMusicList.size()) {
                    showSongLongClickMenu(localMusicList.get(position), position);
                    return true;
                }
                return false;
            }
        });

        btnSaveSettings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveAndTestSettings(); }
        });

        btnClearCache.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                CacheManager.clearAllCache(MainActivity.this);
                clearDiskCovers();
                updateCacheSizeDisplay();
                Toast.makeText(MainActivity.this, "已清除全部音频缓存与封面", Toast.LENGTH_SHORT).show();
            }
        });

        // ================= 3D 页面交互事件绑定 =================
        if (btnDetailVisualizer != null) {
            btnDetailVisualizer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openVisualizerOverlay();
                }
            });
        }

        if (btnCloseVisualizer != null) {
            btnCloseVisualizer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeVisualizerOverlay();
                }
            });
        }

        if (btnVisualizerNavMode != null) {
            btnVisualizerNavMode.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cycleNavBarMode();
                }
            });
        }

        // 视觉复位按钮：平滑重置视角至 45°
        if (btnResetVisualizerAngle != null) {
            btnResetVisualizerAngle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (viewVisualizer3D != null) {
                        viewVisualizer3D.resetPitchAngle();
                        Toast.makeText(MainActivity.this, "视觉已复位 (45°)", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        // 胶囊栏封面点击：弹出/关闭歌曲列表抽屉
        if (ivCapsuleCover != null) {
            ivCapsuleCover.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleVisualizerQueuePanel();
                }
            });
        }

        // 全屏监听回调：在强隐或灭灯模式下虚拟按键弹出后延时自动重隐
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                if (layoutVisualizerOverlay != null && layoutVisualizerOverlay.getVisibility() == View.VISIBLE) {
                    if (currentNavMode == NAV_MODE_HIDE && (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
                        navBarHandler.removeCallbacks(navBarHideRunnable);
                        navBarHandler.postDelayed(navBarHideRunnable, 2500);
                    } else if (currentNavMode == NAV_MODE_LOW_PROFILE && (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                        navBarHandler.removeCallbacks(navBarHideRunnable);
                        navBarHandler.postDelayed(navBarHideRunnable, 2500);
                    }
                }
            }
        });

        if (btnCapsulePlayPause != null) {
            btnCapsulePlayPause.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (DlnaManager.isCasting()) {
                        if (isCurrentSongPlaying) DlnaManager.pause();
                        else DlnaManager.resume();
                    }
                    startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_TOGGLE));
                    Toast.makeText(MainActivity.this, isCurrentSongPlaying ? "暂停" : "播放", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnCapsuleNext != null) {
            btnCapsuleNext.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_NEXT));
                    Toast.makeText(MainActivity.this, "下一首", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnCapsulePrev != null) {
            btnCapsulePrev.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_PREV));
                    Toast.makeText(MainActivity.this, "上一首", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnCapsuleMode != null) {
            btnCapsuleMode.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_CYCLE_MODE));
                    int nextMode = (MusicService.getCurrentMode() + 1) % 3;
                    String mName = (nextMode == MusicService.MODE_SHUFFLE) ? "随机播放" : (nextMode == MusicService.MODE_SINGLE ? "单曲循环" : "顺序循环");
                    Toast.makeText(MainActivity.this, "模式: " + mName, Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnCapsuleQueue != null) {
            btnCapsuleQueue.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleVisualizerQueuePanel();
                }
            });
        }

        if (btnCloseVisualizerQueue != null) {
            btnCloseVisualizerQueue.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (layoutVisualizerQueuePanel != null) {
                        layoutVisualizerQueuePanel.setVisibility(View.GONE);
                    }
                }
            });
        }

        if (lvVisualizerQueue != null) {
            lvVisualizerQueue.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    ArrayList<MusicService.SongItem> queue = MusicService.getPlaylist();
                    if (position >= 0 && position < queue.size()) {
                        Intent intent = new Intent(MainActivity.this, MusicService.class);
                        intent.setAction(MusicService.ACTION_PLAY_INDEX);
                        intent.putExtra("target_index", position);
                        startService(intent);
                        refreshQueueList();
                        scrollToCurrentPlayingInQueue();
                    }
                }
            });
        }

        if (capsuleSeekBar != null) {
            capsuleSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                    if (fromUser && tvCapsuleCurrentTime != null) {
                        tvCapsuleCurrentTime.setText(formatTime(progress));
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar sb) { isUserSeeking = true; }
                @Override
                public void onStopTrackingTouch(SeekBar sb) {
                    isUserSeeking = false;
                    lastValidProgressMs = sb.getProgress();
                    if (DlnaManager.isCasting()) DlnaManager.seek(sb.getProgress());
                    Intent intent = new Intent(MainActivity.this, MusicService.class);
                    intent.setAction(MusicService.ACTION_SEEK);
                    intent.putExtra("position", sb.getProgress());
                    startService(intent);
                }
            });
        }

        setupPlaybackControls();
    }

    private void toggleVisualizerQueuePanel() {
        if (layoutVisualizerQueuePanel == null) return;
        if (layoutVisualizerQueuePanel.getVisibility() == View.VISIBLE) {
            layoutVisualizerQueuePanel.setVisibility(View.GONE);
        } else {
            refreshQueueList();
            layoutVisualizerQueuePanel.setVisibility(View.VISIBLE);
            scrollToCurrentPlayingInQueue();
        }
    }

    private void openVisualizerOverlay() {
        if (layoutVisualizerOverlay == null) return;
        layoutVisualizerOverlay.setVisibility(View.VISIBLE);
        applyNavBarMode();

        if (viewVisualizer3D != null) {
            viewVisualizer3D.onResume();
            viewVisualizer3D.setPlaying(isCurrentSongPlaying);
        }
        int sessionId = AudioEffectsManager.getInstance().getAudioSessionId();
        if (audioVisualizerHelper != null) {
            audioVisualizerHelper.setPlaying(isCurrentSongPlaying);
            if (sessionId > 0) {
                audioVisualizerHelper.start(sessionId);
            }
        }
        if (currentRawCoverBitmap != null && ivVisualizerSilhouette != null) {
            ivVisualizerSilhouette.setImageBitmap(currentRawCoverBitmap);
        }
        ArrayList<MusicService.SongItem> q = MusicService.getPlaylist();
        int idx = MusicService.getCurrentIndex();
        if (q != null && idx >= 0 && idx < q.size()) {
            MusicService.SongItem s = q.get(idx);
            if (tvCapsuleTitle != null) tvCapsuleTitle.setText(s.title);
            if (tvCapsuleArtist != null) tvCapsuleArtist.setText(s.artist);
            if (currentCircularCoverBitmap != null && ivCapsuleCover != null) {
                ivCapsuleCover.setImageBitmap(currentCircularCoverBitmap);
            }
        }
        if (currentLyricIndex >= 0 && currentLyricIndex < lyricRows.size()) {
            updateLyricPosition(lastValidProgressMs);
        }
    }

    private void closeVisualizerOverlay() {
        if (layoutVisualizerOverlay == null) return;
        layoutVisualizerOverlay.setVisibility(View.GONE);
        navBarHandler.removeCallbacks(navBarHideRunnable);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);

        if (viewVisualizer3D != null) {
            viewVisualizer3D.onPause();
        }
        if (audioVisualizerHelper != null) {
            audioVisualizerHelper.stop();
        }
        if (layoutVisualizerQueuePanel != null) {
            layoutVisualizerQueuePanel.setVisibility(View.GONE);
        }
    }

    private void setupPlaybackControls() {
        View.OnClickListener toggleListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DlnaManager.isCasting()) {
                    if (isCurrentSongPlaying) DlnaManager.pause();
                    else DlnaManager.resume();
                }
                startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_TOGGLE));
            }
        };
        btnPlayPause.setOnClickListener(toggleListener);
        btnDetailPlayPause.setOnClickListener(toggleListener);

        View.OnClickListener prevListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_PREV));
            }
        };
        btnPrev.setOnClickListener(prevListener);
        btnDetailPrev.setOnClickListener(prevListener);

        View.OnClickListener nextListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_NEXT));
            }
        };
        btnNext.setOnClickListener(nextListener);
        btnDetailNext.setOnClickListener(nextListener);

        View.OnClickListener modeListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_CYCLE_MODE));
            }
        };
        btnMode.setOnClickListener(modeListener);
        btnDetailMode.setOnClickListener(modeListener);

        View.OnClickListener eqListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new EqualizerDialog(MainActivity.this).show();
            }
        };
        btnOpenEq.setOnClickListener(eqListener);
        btnDetailEq.setOnClickListener(eqListener);

        SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    String timeStr = formatTime(progress) + " / " + formatTime(sb.getMax());
                    tvTime.setText(timeStr);
                    tvDetailTime.setText(timeStr);
                    if (tvCapsuleCurrentTime != null) tvCapsuleCurrentTime.setText(formatTime(progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { isUserSeeking = true; }
            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                isUserSeeking = false;
                lastValidProgressMs = sb.getProgress();
                if (DlnaManager.isCasting()) DlnaManager.seek(sb.getProgress());
                Intent intent = new Intent(MainActivity.this, MusicService.class);
                intent.setAction(MusicService.ACTION_SEEK);
                intent.putExtra("position", sb.getProgress());
                startService(intent);
            }
        };
        seekBar.setOnSeekBarChangeListener(seekListener);
        detailSeekBar.setOnSeekBarChangeListener(seekListener);

        ivBottomCover.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openDetailOverlay(); }
        });
        layoutBottomPlayer.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openDetailOverlay(); }
        });

        btnCloseDetail.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closeDetailOverlay(); }
        });
        layoutDetailBottomBlank.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closeDetailOverlay(); }
        });

        btnToggleQueue.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleMainQueueDrawer(); }
        });
        btnCloseQueue.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { layoutQueuePanel.setVisibility(View.GONE); }
        });

        btnDetailQueue.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleDetailQueueView(); }
        });

        btnDetailKeepScreen.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                isKeepScreenOn = !isKeepScreenOn;
                if (isKeepScreenOn) {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    btnDetailKeepScreen.setText("取消常亮");
                    btnDetailKeepScreen.setTextColor(0xFF00E5FF);
                } else {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    btnDetailKeepScreen.setText("保持常亮");
                    btnDetailKeepScreen.setTextColor(0xFFE0E0E0);
                }
            }
        });

        btnDetailDlna.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showDlnaDialog(); }
        });

        btnDetailDownload.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { downloadCurrentPlayingSong(); }
        });

        View.OnClickListener favToggle = new View.OnClickListener() {
            @Override public void onClick(View v) { toggleFavoriteCurrentSong(); }
        };
        btnBottomFav.setOnClickListener(favToggle);
        btnDetailFav.setOnClickListener(favToggle);

        flVinylDisc.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleCoverDisplayMode(); }
        });
        ivSquareCover.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleCoverDisplayMode(); }
        });

        btnLyricDelay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                manualLyricOffsetMs -= 500;
                updateLyricOffsetStatusView();
            }
        });
        btnLyricReset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                manualLyricOffsetMs = 0;
                updateLyricOffsetStatusView();
            }
        });
        btnLyricAdvance.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                manualLyricOffsetMs += 500;
                updateLyricOffsetStatusView();
            }
        });
        btnLyricDec.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (lyricBaseFontSize > 11) {
                    lyricBaseFontSize -= 2;
                    prefs.edit().putInt("lyric_font_size", lyricBaseFontSize).apply();
                    rebuildLyricsViews();
                }
            }
        });
        btnLyricInc.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (lyricBaseFontSize < 25) {
                    lyricBaseFontSize += 2;
                    prefs.edit().putInt("lyric_font_size", lyricBaseFontSize).apply();
                    rebuildLyricsViews();
                }
            }
        });
        btnSwitchLyric.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showLyricPickerDialog(); }
        });

        AdapterView.OnItemClickListener queueItemClick = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ArrayList<MusicService.SongItem> queue = MusicService.getPlaylist();
                if (position >= 0 && position < queue.size()) {
                    Intent intent = new Intent(MainActivity.this, MusicService.class);
                    intent.setAction(MusicService.ACTION_PLAY_INDEX);
                    intent.putExtra("target_index", position);
                    startService(intent);
                    refreshQueueList();
                    scrollToCurrentPlayingInQueue();
                }
            }
        };
        lvQueue.setOnItemClickListener(queueItemClick);
        lvDetailQueue.setOnItemClickListener(queueItemClick);
    }

    private void openDetailOverlay() {
        layoutDetailOverlay.setVisibility(View.VISIBLE);
        scrollToCurrentPlayingInQueue();
    }

    private void closeDetailOverlay() {
        layoutDetailOverlay.setVisibility(View.GONE);
    }

    private void toggleMainQueueDrawer() {
        if (layoutQueuePanel.getVisibility() == View.VISIBLE) {
            layoutQueuePanel.setVisibility(View.GONE);
        } else {
            refreshQueueList();
            layoutQueuePanel.setVisibility(View.VISIBLE);
            scrollToCurrentPlayingInQueue();
        }
    }

    private void toggleDetailQueueView() {
        if (layoutDetailQueueView.getVisibility() == View.VISIBLE) {
            layoutDetailQueueView.setVisibility(View.GONE);
            layoutDetailLyricsView.setVisibility(View.VISIBLE);
            btnDetailQueue.setTextColor(0xFFE0E0E0);
        } else {
            refreshQueueList();
            layoutDetailLyricsView.setVisibility(View.GONE);
            layoutDetailQueueView.setVisibility(View.VISIBLE);
            btnDetailQueue.setTextColor(0xFF00E5FF);
            scrollToCurrentPlayingInQueue();
        }
    }

    private void updateControlIcon(ImageView iv, String iconName, int fallbackRes) {
        if (iv == null) return;
        int resId = getResources().getIdentifier(iconName, "drawable", getPackageName());
        if (resId != 0) {
            iv.setImageResource(resId);
        } else {
            iv.setImageResource(fallbackRes);
        }
    }

    private void setupControlIcons() {
        updateControlIcon(btnExitApp, "ic_exit", android.R.drawable.ic_lock_power_off);
        updateControlIcon(btnDetailExitApp, "ic_exit", android.R.drawable.ic_lock_power_off);
        updateControlIcon(btnPrev, "ic_prev", android.R.drawable.ic_media_previous);
        updateControlIcon(btnDetailPrev, "ic_prev", android.R.drawable.ic_media_previous);
        updateControlIcon(btnCapsulePrev, "ic_prev", android.R.drawable.ic_media_previous);
        updateControlIcon(btnNext, "ic_next", android.R.drawable.ic_media_next);
        updateControlIcon(btnDetailNext, "ic_next", android.R.drawable.ic_media_next);
        updateControlIcon(btnCapsuleNext, "ic_next", android.R.drawable.ic_media_next);
        updateControlIcon(btnOpenEq, "ic_eq", android.R.drawable.ic_menu_preferences);
        updateControlIcon(btnDetailEq, "ic_eq", android.R.drawable.ic_menu_preferences);
        updateControlIcon(btnCapsuleQueue, "ic_queue", android.R.drawable.ic_menu_sort_by_size);
    }

    private void updatePlayPauseIcons(boolean isPlaying) {
        String iconName = isPlaying ? "ic_pause" : "ic_play";
        int fallback = isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        updateControlIcon(btnPlayPause, iconName, fallback);
        updateControlIcon(btnDetailPlayPause, iconName, fallback);
        updateControlIcon(btnCapsulePlayPause, iconName, fallback);
    }

    private void updateModeIcons(int mode) {
        String iconName = "ic_loop_all";
        int fallback = android.R.drawable.ic_menu_rotate;
        if (mode == MusicService.MODE_SHUFFLE) {
            iconName = "ic_shuffle";
            fallback = android.R.drawable.ic_menu_share;
        } else if (mode == MusicService.MODE_SINGLE) {
            iconName = "ic_single";
            fallback = android.R.drawable.ic_menu_always_landscape_portrait;
        }
        updateControlIcon(btnMode, iconName, fallback);
        updateControlIcon(btnDetailMode, iconName, fallback);
        updateControlIcon(btnCapsuleMode, iconName, fallback);
    }

    private void setupVinylAnimation() {
        vinylRotateAnim = new RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        vinylRotateAnim.setDuration(18000);
        vinylRotateAnim.setRepeatCount(Animation.INFINITE);
        vinylRotateAnim.setInterpolator(new LinearInterpolator());
    }

    private void updateVinylAnimationState() {
        if (flVinylDisc == null || viewTonearm == null) return;
        if (isCurrentSongPlaying) {
            if (flVinylDisc.getAnimation() == null) {
                flVinylDisc.startAnimation(vinylRotateAnim);
            }
            viewTonearm.setPlaying(true);
        } else {
            flVinylDisc.clearAnimation();
            viewTonearm.setPlaying(false);
        }
    }

    private void toggleCoverDisplayMode() {
        isVinylDisplayMode = !isVinylDisplayMode;
        prefs.edit().putBoolean("is_vinyl_display_mode", isVinylDisplayMode).apply();
        updateCoverDisplayMode();
    }

    private void updateCoverDisplayMode() {
        if (isVinylDisplayMode) {
            layoutVinylContainer.setVisibility(View.VISIBLE);
            ivSquareCover.setVisibility(View.GONE);
        } else {
            layoutVinylContainer.setVisibility(View.GONE);
            ivSquareCover.setVisibility(View.VISIBLE);
        }
    }

    private void loadCoverArt(final String coverArtId) {
        if (coverArtId == null || coverArtId.length() == 0) {
            setDefaultCover();
            return;
        }
        Bitmap memBmp = imageMemoryCache.get(coverArtId);
        if (memBmp != null) {
            applyCoverBitmaps(memBmp);
            return;
        }
        imageLoadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = null;
                try {
                    if (coverArtId.startsWith("http://") || coverArtId.startsWith("https://")) {
                        bmp = downloadBitmap(coverArtId);
                    } else if (coverArtId.startsWith("kg_hash:")) {
                        String realUrl = LxApiHelper.fetchKugouSongCover(coverArtId.substring(8));
                        if (realUrl != null) bmp = downloadBitmap(realUrl);
                    } else {
                        String coverUrl = buildCoverArtUrl(coverArtId);
                        bmp = downloadBitmap(coverUrl);
                    }
                } catch (Exception ignored) {}

                final Bitmap finalBmp = bmp;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (finalBmp != null) {
                            imageMemoryCache.put(coverArtId, finalBmp);
                            applyCoverBitmaps(finalBmp);
                        } else {
                            setDefaultCover();
                        }
                    }
                });
            }
        });
    }

    private void applyCoverBitmaps(Bitmap raw) {
        currentRawCoverBitmap = raw;
        currentCircularCoverBitmap = getCircularBitmap(raw);
        ivBottomCover.setImageBitmap(currentCircularCoverBitmap);
        ivVinylCircularCover.setImageBitmap(currentCircularCoverBitmap);
        ivSquareCover.setImageBitmap(raw);
        if (ivVisualizerSilhouette != null) ivVisualizerSilhouette.setImageBitmap(raw);
        if (ivCapsuleCover != null) ivCapsuleCover.setImageBitmap(currentCircularCoverBitmap);
    }

    private void setDefaultCover() {
        ivBottomCover.setImageResource(R.drawable.ic_launcher);
        ivVinylCircularCover.setImageResource(android.R.drawable.ic_menu_report_image);
        ivSquareCover.setImageResource(android.R.drawable.ic_menu_report_image);
        if (ivVisualizerSilhouette != null) ivVisualizerSilhouette.setImageDrawable(null);
        if (ivCapsuleCover != null) ivCapsuleCover.setImageResource(R.drawable.ic_launcher);
        currentRawCoverBitmap = null;
        currentCircularCoverBitmap = null;
    }

    private Bitmap getCircularBitmap(Bitmap bitmap) {
        int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        Rect rect = new Rect((bitmap.getWidth() - size) / 2, (bitmap.getHeight() - size) / 2,
                (bitmap.getWidth() + size) / 2, (bitmap.getHeight() + size) / 2);
        Rect dest = new Rect(0, 0, size, size);
        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, dest, paint);
        return output;
    }

    private Bitmap downloadBitmap(String urlStr) {
        HttpURLConnection conn = null;
        InputStream is = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            if (conn instanceof HttpsURLConnection) {
                ((HttpsURLConnection) conn).setSSLSocketFactory(new TLSSocketFactory());
            }
            conn.connect();
            if (conn.getResponseCode() == 200) {
                is = conn.getInputStream();
                return BitmapFactory.decodeStream(is);
            }
        } catch (Exception ignored) {
        } finally {
            try { if (is != null) is.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private void loadLyrics(final String songId, final String artist, final String title) {
        currentSongIdForLyric = songId;
        currentArtistForLyric = artist;
        currentTitleForLyric = title;
        currentLyricIndex = -1;
        layoutLyricsContainer.removeAllViews();
        lyricRows.clear();

        TextView tvLoading = new TextView(this);
        tvLoading.setText("歌词加载中...");
        tvLoading.setTextColor(0xFF888C99);
        tvLoading.setTextSize(lyricBaseFontSize);
        layoutLyricsContainer.addView(tvLoading);

        new Thread(new Runnable() {
            @Override
            public void run() {
                String lrc = null;
                try {
                    String query = "getLyrics.view?artist=" + URLEncoder.encode(artist, "UTF-8")
                            + "&title=" + URLEncoder.encode(title, "UTF-8") + "&" + getAuthParams();
                    String jsonStr = requestApi(query);
                    if (jsonStr != null) {
                        JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
                        JSONObject lyrObj = root.optJSONObject("lyrics");
                        if (lyrObj != null) lrc = lyrObj.optString("content", null);
                    }
                } catch (Exception ignored) {}

                if (lrc == null || lrc.trim().length() == 0) {
                    try {
                        ArrayList<LxApiHelper.LyricCandidate> candidates = LxApiHelper.searchLyricCandidates(title + " " + artist);
                        if (!candidates.isEmpty()) {
                            lrc = LxApiHelper.fetchLyricFromCandidate(candidates.get(0));
                        }
                    } catch (Exception ignored) {}
                }

                final String finalLrc = lrc;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        currentLoadedRawLyrics = finalLrc;
                        parseLrc(finalLrc);
                    }
                });
            }
        }).start();
    }

    private void parseLrc(String lrcText) {
        lyricRows.clear();
        layoutLyricsContainer.removeAllViews();
        currentLyricIndex = -1;

        if (lrcText == null || lrcText.trim().length() == 0) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("暂无歌词");
            tvEmpty.setTextColor(0xFF888C99);
            tvEmpty.setTextSize(lyricBaseFontSize);
            layoutLyricsContainer.addView(tvEmpty);
            if (tvVisualizerCurrentLyric != null) tvVisualizerCurrentLyric.setText("纯音乐，请欣赏");
            if (tvVisualizerNextLyric != null) tvVisualizerNextLyric.setText("");
            return;
        }

        String[] lines = lrcText.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("[") && line.contains("]")) {
                int endIdx = line.indexOf(']');
                String timeStr = line.substring(1, endIdx);
                String text = line.substring(endIdx + 1).trim();
                long ms = parseTimeToMs(timeStr);
                if (ms >= 0 && text.length() > 0) {
                    lyricRows.add(new LyricRow(ms, text));
                }
            }
        }

        Collections.sort(lyricRows, new Comparator<LyricRow>() {
            @Override public int compare(LyricRow o1, LyricRow o2) {
                return Long.valueOf(o1.timeMs).compareTo(o2.timeMs);
            }
        });

        rebuildLyricsViews();
    }

    private void rebuildLyricsViews() {
        layoutLyricsContainer.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < lyricRows.size(); i++) {
            final LyricRow row = lyricRows.get(i);
            TextView tv = new TextView(this);
            tv.setText(row.text);
            tv.setTextColor(0xFF888C99);
            tv.setTextSize(lyricBaseFontSize);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, (int) (6 * density), 0, (int) (6 * density));
            row.view = tv;
            final int seekPos = (int) row.timeMs;
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (DlnaManager.isCasting()) DlnaManager.seek(seekPos);
                    Intent intent = new Intent(MainActivity.this, MusicService.class);
                    intent.setAction(MusicService.ACTION_SEEK);
                    intent.putExtra("position", seekPos);
                    startService(intent);
                }
            });
            layoutLyricsContainer.addView(tv);
        }
        if (currentLyricIndex >= 0 && currentLyricIndex < lyricRows.size()) {
            highlightLyric(currentLyricIndex);
        }
    }

    private void updateLyricPosition(int positionMs) {
        if (lyricRows.isEmpty()) return;
        long targetMs = positionMs + manualLyricOffsetMs + dlnaGlobalLyricOffsetMs;
        int activeIdx = -1;
        for (int i = 0; i < lyricRows.size(); i++) {
            if (targetMs >= lyricRows.get(i).timeMs) {
                activeIdx = i;
            } else {
                break;
            }
        }

        if (activeIdx != currentLyricIndex && activeIdx >= 0) {
            highlightLyric(activeIdx);
            currentLyricIndex = activeIdx;
        }

        // 同步刷新 3D 页面悬浮歌词
        if (layoutVisualizerOverlay != null && layoutVisualizerOverlay.getVisibility() == View.VISIBLE && activeIdx >= 0) {
            if (tvVisualizerCurrentLyric != null) {
                tvVisualizerCurrentLyric.setText(lyricRows.get(activeIdx).text);
            }
            if (tvVisualizerNextLyric != null) {
                if (activeIdx + 1 < lyricRows.size()) {
                    tvVisualizerNextLyric.setText(lyricRows.get(activeIdx + 1).text);
                } else {
                    tvVisualizerNextLyric.setText("");
                }
            }
        }
    }

    private void highlightLyric(int index) {
        for (int i = 0; i < lyricRows.size(); i++) {
            TextView v = lyricRows.get(i).view;
            if (v != null) {
                if (i == index) {
                    v.setTextColor(0xFF00E5FF);
                    v.setTextSize(lyricBaseFontSize + 2);
                    v.setTypeface(null, Typeface.BOLD);
                } else {
                    v.setTextColor(0xFF888C99);
                    v.setTextSize(lyricBaseFontSize);
                    v.setTypeface(null, Typeface.NORMAL);
                }
            }
        }

        if (scrollLyrics != null && index >= 0 && index < lyricRows.size() && !isUserTouchingLyrics) {
            TextView targetView = lyricRows.get(index).view;
            if (targetView != null) {
                int scrollY = targetView.getTop() - (scrollLyrics.getHeight() / 2) + (targetView.getHeight() / 2);
                scrollLyrics.smoothScrollTo(0, Math.max(0, scrollY));
            }
        }
    }

    private void updateLyricOffsetStatusView() {
        if (tvLyricOffsetStatus != null) {
            double sec = manualLyricOffsetMs / 1000.0;
            tvLyricOffsetStatus.setText(String.format(Locale.US, "%+.1fs", sec));
        }
    }

    private void showLyricPickerDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_lyric_picker, null);
        dialog.setContentView(v);

        final EditText etQuery = (EditText) v.findViewById(R.id.et_lyric_search_query);
        Button btnSubmit = (Button) v.findViewById(R.id.btn_lyric_search_submit);
        Button btnClose = (Button) v.findViewById(R.id.btn_lyric_picker_close);
        final ListView lvCandidates = (ListView) v.findViewById(R.id.lv_lyric_candidates);

        etQuery.setText(currentTitleForLyric + " " + currentArtistForLyric);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });

        final ArrayList<LxApiHelper.LyricCandidate> candList = new ArrayList<LxApiHelper.LyricCandidate>();
        final ArrayAdapter<LxApiHelper.LyricCandidate> candAdapter = new ArrayAdapter<LxApiHelper.LyricCandidate>(
                this, android.R.layout.simple_list_item_1, candList);
        lvCandidates.setAdapter(candAdapter);

        final Runnable doSearch = new Runnable() {
            @Override
            public void run() {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final ArrayList<LxApiHelper.LyricCandidate> res = LxApiHelper.searchLyricCandidates(etQuery.getText().toString().trim());
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                candList.clear();
                                candList.addAll(res);
                                candAdapter.notifyDataSetChanged();
                            }
                        });
                    }
                }).start();
            }
        };

        btnSubmit.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doSearch.run(); }
        });

        lvCandidates.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < candList.size()) {
                    final LxApiHelper.LyricCandidate chosen = candList.get(position);
                    Toast.makeText(MainActivity.this, "正在应用歌词...", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            final String lrc = LxApiHelper.fetchLyricFromCandidate(chosen);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (lrc != null && lrc.length() > 0) {
                                        currentLoadedRawLyrics = lrc;
                                        manualLyricOffsetMs = 0;
                                        updateLyricOffsetStatusView();
                                        parseLrc(lrc);
                                        Toast.makeText(MainActivity.this, "歌词替换成功", Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(MainActivity.this, "歌词内容获取失败", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                        }
                    }).start();
                }
            }
        });

        float density = getResources().getDisplayMetrics().density;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout((int) (480 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
        doSearch.run();
    }

    private void refreshQueueList() {
        queueData.clear();
        ArrayList<MusicService.SongItem> list = MusicService.getPlaylist();
        int curIdx = MusicService.getCurrentIndex();
        for (int i = 0; i < list.size(); i++) {
            MusicService.SongItem s = list.get(i);
            Map<String, String> row = new HashMap<String, String>();
            row.put("title", (i == curIdx ? "▶ " : "") + (i + 1) + ". " + s.title);
            row.put("subtitle", s.artist + " [" + s.quality + "]");
            queueData.add(row);
        }
        queueAdapter.notifyDataSetChanged();
        detailQueueAdapter.notifyDataSetChanged();
        visualizerQueueAdapter.notifyDataSetChanged();
    }

    private void scrollToCurrentPlayingInQueue() {
        int idx = MusicService.getCurrentIndex();
        if (idx >= 0) {
            if (lvQueue != null) lvQueue.setSelection(idx);
            if (lvDetailQueue != null) lvDetailQueue.setSelection(idx);
            if (lvVisualizerQueue != null) lvVisualizerQueue.setSelection(idx);
        }
    }

    private void updateFavButtonState(String songId) {
        boolean isFav = (songId != null && favSongIds.contains(songId));
        String heart = isFav ? "♥" : "♡";
        int color = isFav ? 0xFFFF4081 : 0xFFCBD5E1;
        btnBottomFav.setText(heart);
        btnBottomFav.setTextColor(color);
        btnDetailFav.setText(heart);
        btnDetailFav.setTextColor(color);
    }

    private void toggleFavoriteCurrentSong() {
        ArrayList<MusicService.SongItem> queue = MusicService.getPlaylist();
        int curIdx = MusicService.getCurrentIndex();
        if (queue == null || curIdx < 0 || curIdx >= queue.size()) return;
        final MusicService.SongItem curSong = queue.get(curIdx);
        final boolean isNowFav = !favSongIds.contains(curSong.id);

        if (isNowFav) {
            favSongIds.add(curSong.id);
        } else {
            favSongIds.remove(curSong.id);
        }
        saveFavSet();
        updateFavButtonState(curSong.id);
        Toast.makeText(this, isNowFav ? "已添加到特别收藏" : "已从特别收藏移除", Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                String action = isNowFav ? "star.view" : "unstar.view";
                requestApi(action + "?id=" + URLEncoder.encode(curSong.id) + "&" + getAuthParams());
            }
        }).start();
    }

    private void loadFavSet() {
        favSongIds = new HashSet<String>(prefs.getStringSet("fav_songs_set", new HashSet<String>()));
    }

    private void saveFavSet() {
        prefs.edit().putStringSet("fav_songs_set", favSongIds).apply();
    }

    private void loadLocalPlaylists() {
        featuredSongs.clear();
        carSongs.clear();
        String featJson = prefs.getString("local_playlist_featured", "[]");
        String carJson = prefs.getString("local_playlist_car", "[]");
        try {
            JSONArray fArr = new JSONArray(featJson);
            for (int i = 0; i < fArr.length(); i++) {
                JSONObject o = fArr.getJSONObject(i);
                featuredSongs.add(new DisplayEntry(o.getString("id"), o.getString("title"), o.getString("artist"),
                        o.optString("sub", ""), o.optString("cov", null), o.optString("q", "320K"), true));
            }
            JSONArray cArr = new JSONArray(carJson);
            for (int i = 0; i < cArr.length(); i++) {
                JSONObject o = cArr.getJSONObject(i);
                carSongs.add(new DisplayEntry(o.getString("id"), o.getString("title"), o.getString("artist"),
                        o.optString("sub", ""), o.optString("cov", null), o.optString("q", "320K"), true));
            }
        } catch (Exception ignored) {}
    }

    private void saveLocalPlaylists() {
        try {
            JSONArray fArr = new JSONArray();
            for (DisplayEntry e : featuredSongs) {
                JSONObject o = new JSONObject();
                o.put("id", e.id); o.put("title", e.title); o.put("artist", e.artist);
                o.put("sub", e.subtitle); o.put("cov", e.coverArt); o.put("q", e.quality);
                fArr.put(o);
            }
            JSONArray cArr = new JSONArray();
            for (DisplayEntry e : carSongs) {
                JSONObject o = new JSONObject();
                o.put("id", e.id); o.put("title", e.title); o.put("artist", e.artist);
                o.put("sub", e.subtitle); o.put("cov", e.coverArt); o.put("q", e.quality);
                cArr.put(o);
            }
            prefs.edit().putString("local_playlist_featured", fArr.toString())
                    .putString("local_playlist_car", cArr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void showSongLongClickMenu(final DisplayEntry song, final int listPos) {
        String[] items = new String[]{"加入特别收藏", "加入精选专区", "加入车载专区", "下载歌曲", "删除本地缓存"};
        new AlertDialog.Builder(this)
                .setTitle(song.title + " - " + song.artist)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            favSongIds.add(song.id);
                            saveFavSet();
                            updateFavButtonState(song.id);
                            Toast.makeText(MainActivity.this, "已加入特别收藏", Toast.LENGTH_SHORT).show();
                        } else if (which == 1) {
                            featuredSongs.add(song);
                            saveLocalPlaylists();
                            Toast.makeText(MainActivity.this, "已加入精选专区", Toast.LENGTH_SHORT).show();
                        } else if (which == 2) {
                            carSongs.add(song);
                            saveLocalPlaylists();
                            Toast.makeText(MainActivity.this, "已加入车载专区", Toast.LENGTH_SHORT).show();
                        } else if (which == 3) {
                            downloadSong(song);
                        } else if (which == 4) {
                            File f = CacheManager.getSongFile(MainActivity.this, song.id);
                            if (f.exists()) f.delete();
                            updateCacheSizeDisplay();
                            Toast.makeText(MainActivity.this, "缓存已清理", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }

    private void showPlaylistLongClickMenu(final DisplayEntry playlist) {
        new AlertDialog.Builder(this)
                .setTitle(playlist.title)
                .setMessage("是否将该歌单添加到自建歌单？")
                .setPositiveButton("确定", null)
                .setNegativeButton("取消", null)
                .show();
    }

    private void showArtistOrAlbumLongClickMenu(final DisplayEntry entry) {
        Toast.makeText(this, entry.title, Toast.LENGTH_SHORT).show();
    }

    private void promptCreatePlaylist() {
        final EditText et = new EditText(this);
        et.setHint("输入歌单名称...");
        et.setTextColor(0xFFFFFFFF);
        et.setHintTextColor(0xFF777777);
        new AlertDialog.Builder(this)
                .setTitle("新建歌单")
                .setView(et)
                .setPositiveButton("创建", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String name = et.getText().toString().trim();
                        if (name.length() > 0) {
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    requestApi("createPlaylist.view?name=" + URLEncoder.encode(name) + "&" + getAuthParams());
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            syncFavoritesAndPlaylists(false);
                                        }
                                    });
                                }
                            }).start();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void downloadSong(DisplayEntry song) {
        Toast.makeText(this, "开始下载: " + song.title, Toast.LENGTH_SHORT).show();
    }

    private void downloadCurrentPlayingSong() {
        ArrayList<MusicService.SongItem> queue = MusicService.getPlaylist();
        int curIdx = MusicService.getCurrentIndex();
        if (queue != null && curIdx >= 0 && curIdx < queue.size()) {
            MusicService.SongItem s = queue.get(curIdx);
            Toast.makeText(this, "正在缓存当前歌曲...", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDlnaDialog() {
        Toast.makeText(this, "正在搜索局域网 DLNA 设备...", Toast.LENGTH_SHORT).show();
        DlnaManager.searchDevices(this, new DlnaManager.DiscoveryCallback() {
            @Override
            public void onDeviceFound(final DlnaManager.Device device) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("投播到设备")
                        .setMessage(device.name)
                        .setPositiveButton("投播", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ArrayList<MusicService.SongItem> queue = MusicService.getPlaylist();
                                int curIdx = MusicService.getCurrentIndex();
                                if (queue != null && curIdx >= 0 && curIdx < queue.size()) {
                                    MusicService.SongItem s = queue.get(curIdx);
                                    DlnaManager.playUrl(device, s.streamUrl, s.title, s.artist, lastValidProgressMs);
                                    Toast.makeText(MainActivity.this, "已投播至: " + device.name, Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setNegativeButton("断开", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                DlnaManager.disconnect();
                                Toast.makeText(MainActivity.this, "已断开投播", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .show();
            }
        });
    }

    private void updateCacheSizeDisplay() {
        if (tvCacheUsed != null) {
            long bytes = CacheManager.getUsedCacheBytes(this);
            double mb = bytes / (1024.0 * 1024.0);
            tvCacheUsed.setText(String.format(Locale.US, "(已用 %.1f MB)", mb));
        }
    }

    private void clearDiskCovers() {
        imageMemoryCache.evictAll();
    }

    private void loadSavedConfig() {
        etServer.setText(prefs.getString("server_url", ""));
        etUsername.setText(prefs.getString("user", "admin"));
        etPassword.setText(prefs.getString("password", ""));
        etTimeoutSec.setText(prefs.getString("timeout_sec", "30"));
        etRetryCount.setText(prefs.getString("retry_count", "3"));
        etDownloadPath.setText(prefs.getString("download_path", getDefaultDownloadPath()));
        etCacheSize.setText(prefs.getString("cache_size_mb", "500"));
    }

    private void saveAndTestSettings() {
        String server = etServer.getText().toString().trim();
        String user = etUsername.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        String timeout = etTimeoutSec.getText().toString().trim();
        String retry = etRetryCount.getText().toString().trim();
        String dlPath = etDownloadPath.getText().toString().trim();
        String cacheMb = etCacheSize.getText().toString().trim();

        if (server.endsWith("/")) server = server.substring(0, server.length() - 1);

        prefs.edit()
                .putString("server_url", server)
                .putString("user", user)
                .putString("password", pass)
                .putString("timeout_sec", timeout)
                .putString("retry_count", retry)
                .putString("download_path", dlPath)
                .putString("cache_size_mb", cacheMb)
                .apply();

        Toast.makeText(this, "正在测试服务器连接...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String pingRes = requestApi("ping.view?" + getAuthParams());
                final boolean ok = (pingRes != null && pingRes.contains("ok"));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, ok ? "连接成功！配置已生效" : "连接失败，请检查地址或网络", Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private String getDefaultDownloadPath() {
        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/Music";
    }

    private String getSavedBitrate() {
        return prefs.getString("default_bitrate", "auto");
    }

    private int getBitrateIndex(String val) {
        for (int i = 0; i < BITRATE_VALUES.length; i++) {
            if (BITRATE_VALUES[i].equalsIgnoreCase(val)) return i;
        }
        return 0;
    }

    private int getPlazaBitrateIndex(String val) {
        for (int i = 0; i < PLAZA_BITRATE_VALUES.length; i++) {
            if (PLAZA_BITRATE_VALUES[i].equalsIgnoreCase(val)) return i;
        }
        return 0;
    }

    private String getBitrateDisplay(String bitrateVal, String originalQuality) {
        if ("flac".equalsIgnoreCase(bitrateVal)) return "无损 FLAC";
        if ("320".equals(bitrateVal)) return "极高 320K";
        if ("192".equals(bitrateVal)) return "高 192K";
        if ("128".equals(bitrateVal)) return "标准 128K";
        return (originalQuality != null && originalQuality.length() > 0) ? originalQuality : "标准 320K";
    }

    private String buildStreamUrl(String songId, String bitrate) {
        String server = prefs.getString("server_url", "");
        if (server.length() == 0) return "";
        String base = server + "/rest/stream.view?id=" + URLEncoder.encode(songId) + "&" + getAuthParams();
        if (!"auto".equalsIgnoreCase(bitrate)) {
            base += "&maxBitRate=" + bitrate;
        }
        return base;
    }

    private String buildCoverArtUrl(String id) {
        String server = prefs.getString("server_url", "");
        return server + "/rest/getCoverArt.view?id=" + URLEncoder.encode(id) + "&size=400&" + getAuthParams();
    }

    private String getAuthParams() {
        String u = prefs.getString("user", "admin");
        String p = prefs.getString("password", "");
        String salt = String.valueOf(System.currentTimeMillis());
        String token = md5(p + salt);
        return "u=" + URLEncoder.encode(u) + "&t=" + token + "&s=" + salt + "&v=1.16.1&c=RetroMusic&f=json";
    }

    private String md5(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(s.getBytes("UTF-8"));
            byte[] messageDigest = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte aMessageDigest : messageDigest) {
                String h = Integer.toHexString(0xFF & aMessageDigest);
                while (h.length() < 2) h = "0" + h;
                hexString.append(h);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String requestApi(String query) {
        String server = prefs.getString("server_url", "");
        if (server.length() == 0) return null;
        String fullUrl = server + "/rest/" + query;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(fullUrl);
            conn = (HttpURLConnection) url.openConnection();
            int timeoutSec = 30;
            try {
                timeoutSec = Integer.parseInt(prefs.getString("timeout_sec", "30"));
            } catch (Exception ignored) {}
            conn.setConnectTimeout(timeoutSec * 1000);
            conn.setReadTimeout(timeoutSec * 1000);
            if (conn instanceof HttpsURLConnection) {
                ((HttpsURLConnection) conn).setSSLSocketFactory(new TLSSocketFactory());
            }
            conn.connect();
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                return sb.toString();
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private void addSongRow(JSONObject s, ArrayList<DisplayEntry> sinkList, ArrayList<Map<String, String>> sinkData) {
        String id = s.optString("id");
        String title = s.optString("title", "未知标题");
        String artist = s.optString("artist", "未知歌手");
        String cover = s.optString("coverArt", id);
        int bitRate = s.optInt("bitRate", 320);
        String qStr = bitRate >= 999 ? "无损" : (bitRate + "K");
        sinkList.add(new DisplayEntry(id, title, artist, artist + " [" + qStr + "]", cover, qStr, true, bitRate));
        Map<String, String> row = new HashMap<String, String>();
        row.put("title", title);
        row.put("subtitle", artist + " [" + qStr + "]");
        sinkData.add(row);
    }

    private void addAlbumRow(JSONObject a, ArrayList<DisplayEntry> sinkList, ArrayList<Map<String, String>> sinkData) {
        String id = "album_" + a.optString("id");
        String name = a.optString("name", a.optString("title", "专辑"));
        String artist = a.optString("artist", "");
        sinkList.add(new DisplayEntry(id, name, artist, "专辑 · " + artist, a.optString("coverArt", null), "专辑", false));
        Map<String, String> row = new HashMap<String, String>();
        row.put("title", "[专辑] " + name);
        row.put("subtitle", artist);
        sinkData.add(row);
    }

    private void addArtistRow(JSONObject a, ArrayList<DisplayEntry> sinkList, ArrayList<Map<String, String>> sinkData) {
        String id = "artist_" + a.optString("id");
        String name = a.optString("name", "歌手");
        sinkList.add(new DisplayEntry(id, name, "", "歌手", null, "歌手", false));
        Map<String, String> row = new HashMap<String, String>();
        row.put("title", "[歌手] " + name);
        row.put("subtitle", "点击查看所有专辑");
        sinkData.add(row);
    }

    private long parseTimeToMs(String timeStr) {
        try {
            String[] parts = timeStr.split(":");
            if (parts.length == 2) {
                long m = Long.parseLong(parts[0]);
                float s = Float.parseFloat(parts[1]);
                return (long) (m * 60000 + s * 1000);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private String formatTime(int ms) {
        int totalSec = ms / 1000;
        int m = totalSec / 60;
        int s = totalSec % 60;
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    private void performAppExit() {
        new AlertDialog.Builder(this)
                .setTitle("退出程序")
                .setMessage("确定要停止播放并退出 RetroSubsonic 吗？")
                .setPositiveButton("退出", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        DlnaManager.disconnect();
                        stopService(new Intent(MainActivity.this, MusicService.class));
                        finish();
                        System.exit(0);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (layoutVisualizerOverlay != null && layoutVisualizerOverlay.getVisibility() == View.VISIBLE) {
                closeVisualizerOverlay();
                return true;
            }
            if (layoutDetailOverlay != null && layoutDetailOverlay.getVisibility() == View.VISIBLE) {
                closeDetailOverlay();
                return true;
            }
            if (layoutQueuePanel != null && layoutQueuePanel.getVisibility() == View.VISIBLE) {
                layoutQueuePanel.setVisibility(View.GONE);
                return true;
            }
            if (currentPage == PAGE_PLAZA && isBrowsingPlazaSongs) {
                exitPlazaSongsToPlaylists();
                return true;
            }
            if (currentPage == PAGE_SEARCH && isBrowsingArtistOrAlbum) {
                restoreSearchBackup();
                return true;
            }
            if (currentPage == PAGE_FAV && currentActiveFavPlaylistId != null) {
                showFavAndCustomPlaylists();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(statusReceiver);
        } catch (Exception ignored) {}
        if (audioVisualizerHelper != null) {
            audioVisualizerHelper.stop();
        }
        dlnaSyncHandler.removeCallbacks(dlnaSyncRunnable);
        navBarHandler.removeCallbacks(navBarHideRunnable);
    }

    // ================= 内部适配器类定义 =================
    private class SimpleDarkAdapter extends BaseAdapter {
        private String[] items;
        SimpleDarkAdapter(String[] items) { this.items = items; }
        @Override public int getCount() { return items.length; }
        @Override public Object getItem(int position) { return items[position]; }
        @Override public long getItemId(int position) { return position; }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv = (convertView instanceof TextView) ? (TextView) convertView : new TextView(MainActivity.this);
            tv.setTextSize(12);
            tv.setTextColor(0xFF00E5FF);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(8, 4, 8, 4);
            tv.setText(items[position] + " ▼");
            return tv;
        }
        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            TextView tv = (convertView instanceof TextView) ? (TextView) convertView : new TextView(MainActivity.this);
            tv.setTextSize(13);
            tv.setTextColor(0xFFE0E0E0);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setPadding(20, 16, 20, 16);
            tv.setBackgroundColor(0xFF1E222B);
            tv.setText(items[position]);
            return tv;
        }
    }

    private class BitrateSpinnerAdapter extends BaseAdapter {
        private String[] items;
        BitrateSpinnerAdapter(String[] items) { this.items = items; }
        @Override public int getCount() { return items.length; }
        @Override public Object getItem(int position) { return items[position]; }
        @Override public long getItemId(int position) { return position; }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv = (convertView instanceof TextView) ? (TextView) convertView : new TextView(MainActivity.this);
            tv.setTextSize(12);
            tv.setTextColor(0xFF00E5FF);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(8, 4, 8, 4);
            tv.setText(items[position] + " ▼");
            return tv;
        }
        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            TextView tv = (convertView instanceof TextView) ? (TextView) convertView : new TextView(MainActivity.this);
            tv.setTextSize(13);
            tv.setTextColor(0xFFE0E0E0);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setPadding(20, 16, 20, 16);
            tv.setBackgroundColor(0xFF1E222B);
            tv.setText(items[position]);
            return tv;
        }
    }

    private class PlazaGridAdapter extends BaseAdapter {
        @Override public int getCount() { return plazaPlaylistsList.size(); }
        @Override public Object getItem(int position) { return plazaPlaylistsList.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_plaza_grid, parent, false);
            }
            DisplayEntry entry = plazaPlaylistsList.get(position);
            TextView tvTitle = (TextView) convertView.findViewById(R.id.tv_grid_title);
            TextView tvAuthor = (TextView) convertView.findViewById(R.id.tv_grid_author);
            TextView tvCount = (TextView) convertView.findViewById(R.id.tv_grid_playcount);
            final ImageView ivCover = (ImageView) convertView.findViewById(R.id.iv_grid_cover);

            tvTitle.setText(entry.title);
            tvAuthor.setText(entry.artist);
            tvCount.setText(entry.subtitle);

            if (entry.coverArt != null && entry.coverArt.length() > 0) {
                ivCover.setTag(entry.coverArt);
                Bitmap memBmp = imageMemoryCache.get(entry.coverArt);
                if (memBmp != null) {
                    ivCover.setImageBitmap(memBmp);
                } else {
                    ivCover.setImageResource(R.drawable.ic_launcher);
                    final String coverUrl = entry.coverArt;
                    imageLoadExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            final Bitmap bmp = downloadBitmap(coverUrl);
                            if (bmp != null) {
                                imageMemoryCache.put(coverUrl, bmp);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (coverUrl.equals(ivCover.getTag())) {
                                            ivCover.setImageBitmap(bmp);
                                        }
                                    }
                                });
                            }
                        }
                    });
                }
            } else {
                ivCover.setImageResource(R.drawable.ic_launcher);
            }
            return convertView;
        }
    }
}
