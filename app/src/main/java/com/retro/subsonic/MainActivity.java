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
    private static final String[] PLAZA_BITRATE_LABELS = new String[]{"跟随串流音质", "128K", "192K", "320K", "FLAC"};
    private static final String[] PLAZA_BITRATE_VALUES = new String[]{"follow", "128", "192", "320", "flac"};
    private static final String[] SEARCH_TYPES = new String[]{"歌曲", "歌手", "专辑"};

    // 侧边栏导航
    private Button btnNavSearch, btnNavPlaza, btnNavRanking, btnNavFav, btnNavLocal, btnNavSettings;
    private LinearLayout layoutPageSearch, layoutPagePlaza, layoutPageRanking, layoutPageFav, layoutPageLocal;
    private ScrollView layoutPageSettings;

    // 搜索页
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

    // 歌单广场数据备份栈与状态控制
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

    // 我的收藏与歌单
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

    // 设置
    private EditText etServer, etUsername, etPassword, etTimeoutSec, etRetryCount, etDownloadPath, etCacheSize;
    private Spinner spinnerConfigBitrate, spinnerDetailBitrate, spinnerPlazaBitrate;
    private boolean isSpinnersInitializing = true;
    private Button btnClearCache, btnSaveSettings;
    private TextView tvCacheUsed;

    // 底部播放控制器与全屏详情 Overlay
    private ImageView btnExitApp, btnDetailExitApp;
    private ImageView ivBottomCover, btnMode, btnDetailMode, btnPrev, btnDetailPrev, btnPlayPause, btnDetailPlayPause, btnNext, btnDetailNext, btnOpenEq, btnDetailEq;
    private TextView tvCurrentSong, tvBottomBuffer, tvTime, tvDetailTitle, tvDetailArtist, tvDetailQuality, tvDetailBuffer, tvDetailTime, tvLyricOffsetStatus;
    private SeekBar seekBar, detailSeekBar;
    private Button btnBottomFav, btnDetailFav, btnToggleQueue, btnCloseQueue, btnCloseDetail, btnDetailDownload, btnDetailDlna, btnDetailKeepScreen, btnDetailQueue;
    private Button btnLyricDelay, btnLyricReset, btnLyricAdvance, btnLyricDec, btnLyricInc;
    private LinearLayout layoutBottomPlayer, layoutDetailOverlay, layoutQueuePanel, layoutCoverContainer, layoutDetailSeekBox, layoutDetailControls, layoutDetailBottomBlank;
    private LinearLayout layoutDetailLyricsView, layoutDetailQueueView, layoutLyricsContainer;
    private ListView lvQueue, lvDetailQueue;
    private FrameLayout layoutVinylContainer, flVinylDisc;
    private ImageView ivVinylCircularCover, ivSquareCover;
    private TonearmView viewTonearm;
    private ScrollView scrollLyrics;

    private ArrayList<Map<String, String>> queueData = new ArrayList<Map<String, String>>();
    private SimpleAdapter queueAdapter, detailQueueAdapter;

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
    private Bitmap currentRawCoverBitmap, currentCircularCoverBitmap, currentBottomCoverBitmap;
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
                                int totalDur = durationMs > 0 ? durationMs : (seekBar != null ? seekBar.getMax() : 0);
                                if (totalDur > 5000 && positionMs >= totalDur - 1000 && lastValidProgressMs < totalDur * 0.70) return;
                                lastValidProgressMs = positionMs;
                                if (durationMs > 0) {
                                    seekBar.setMax(durationMs);
                                    detailSeekBar.setMax(durationMs);
                                    String timeStr = formatTime(positionMs) + " / " + formatTime(durationMs);
                                    tvTime.setText(timeStr);
                                    tvDetailTime.setText(timeStr);
                                }
                                seekBar.setProgress(positionMs);
                                detailSeekBar.setProgress(positionMs);
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

                    if (songId != null && !songId.equals(lastLoadedSongId)) {
                        lastLoadedSongId = songId;
                        lastValidProgressMs = 0;
                        if (!DlnaManager.isCasting()) manualLyricOffsetMs = 0;
                        updateLyricOffsetStatusView();
                        seekBar.setProgress(0);
                        detailSeekBar.setProgress(0);
                        tvTime.setText("00:00 / 00:00");
                        tvDetailTime.setText("00:00 / 00:00");
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
                if (!DlnaManager.isCasting() && !isUserSeeking && duration > 0) {
                    lastValidProgressMs = position;
                    seekBar.setMax(duration);
                    seekBar.setProgress(position);
                    detailSeekBar.setMax(duration);
                    detailSeekBar.setProgress(position);
                    String timeStr = formatTime(position) + " / " + formatTime(duration);
                    tvTime.setText(timeStr);
                    tvDetailTime.setText(timeStr);
                    updateLyricPosition(position);
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

        // 搜索页
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

        // 歌单广场
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
        footerPlazaLoading.setText("点击或滑动加载下一页...");
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

        // 排行榜
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

        // 我的收藏
        tvFavTitle = (TextView) findViewById(R.id.tv_fav_title);
        btnFavRefresh = (Button) findViewById(R.id.btn_fav_refresh);
        btnCreatePlaylist = (Button) findViewById(R.id.btn_create_playlist);
        btnFavBack = (Button) findViewById(R.id.btn_fav_back);
        lvFavPlaylists = (ListView) findViewById(R.id.lv_fav_playlists);

        if (btnFavRefresh == null && btnCreatePlaylist != null && btnCreatePlaylist.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) btnCreatePlaylist.getParent();
            btnFavRefresh = new Button(this);
            btnFavRefresh.setText("刷新");
            btnFavRefresh.setTextSize(12);
            btnFavRefresh.setTextColor(0xFFE0E0E0);
            btnFavRefresh.setBackgroundResource(R.drawable.bg_btn_default);
            float density = getResources().getDisplayMetrics().density;
            btnFavRefresh.setPadding((int) (10 * density), 0, (int) (10 * density), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (32 * density));
            lp.rightMargin = (int) (6 * density);
            parent.addView(btnFavRefresh, 0, lp);
        }

        favPlaylistsAdapter = new SimpleAdapter(this, favPlaylistsData, android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvFavPlaylists.setAdapter(favPlaylistsAdapter);

        // 本地音乐
        tvLocalPathStatus = (TextView) findViewById(R.id.tv_local_path_status);
        btnScanLocalMusic = (Button) findViewById(R.id.btn_scan_local_music);
        lvLocalMusic = (ListView) findViewById(R.id.lv_local_music);

        localMusicAdapter = new SimpleAdapter(this, localMusicData, android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvLocalMusic.setAdapter(localMusicAdapter);

        // 设置
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

        // 底部播放条
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

        queueAdapter = new SimpleAdapter(this, queueData, android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvQueue.setAdapter(queueAdapter);
        detailQueueAdapter = new SimpleAdapter(this, queueData, android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvDetailQueue.setAdapter(detailQueueAdapter);
    }

    // 彻底解耦视图模式：如果是歌曲列表则强制列表呈现，歌单广场则严格遵循用户选择
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
            tvHotSearchTitle.setText(isHotSearchCollapsed ? "平台热门搜索 [展开]" : "平台热门搜索");
        }
        Toast.makeText(MainActivity.this, isHotSearchCollapsed ? "已收起热门搜索" : "已展开热门搜索", Toast.LENGTH_SHORT).show();
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
            syncFavoritesAndPlaylists(false); // 切页无感后台校验保新
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
                        
                        // 关键优化：强制解除锁定状态，点击排序即刻加载
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

        Toast.makeText(this, "正在搜索: " + keyword, Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "搜索连接超时或未响应", Toast.LENGTH_SHORT).show();
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
                    tvSearchResultTitle.setText("歌手结果: " + query + " (长按操作/点击查看专辑)");
                } else if (typePos == 2 && result.has("album")) {
                    Object albObj = result.get("album");
                    if (albObj instanceof JSONArray) {
                        JSONArray arr = (JSONArray) albObj;
                        for (int i = 0; i < arr.length(); i++) addAlbumRow(arr.getJSONObject(i), searchResultsList, searchResultsData);
                    } else if (albObj instanceof JSONObject) {
                        addAlbumRow((JSONObject) albObj, searchResultsList, searchResultsData);
                    }
                    tvSearchResultTitle.setText("专辑结果: " + query + " (长按操作/点击查看歌曲)");
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
        tvSearchResultTitle.setText("歌曲结果: " + query + " (共 " + searchResultsList.size() + " 首，长按可操作)");
        searchResultsAdapter.notifyDataSetChanged();
    }

    private void saveSearchStateToBackup() {
        backupSearchList.clear();
        backupSearchList.addAll(searchResultsList);
        backupSearchData.clear();
        backupSearchData.addAll(searchResultsData);
        backupSearchTitle = tvSearchResultTitle.getText().toString();
    }

    private void fetchArtistAlbums(final String artistId, final String artistName) {
        saveSearchStateToBackup();
        Toast.makeText(this, "正在加载 " + artistName + " 的专辑...", Toast.LENGTH_SHORT).show();
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
                                tvSearchResultTitle.setText("⬅ [返回搜索] " + artistName + " 的专辑列表 (长按可操作)");
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
        Toast.makeText(this, "正在加载专辑歌曲...", Toast.LENGTH_SHORT).show();
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
                                tvSearchResultTitle.setText("⬅ [返回] 专辑: " + albumName + " (共 " + searchResultsList.size() + " 首)");
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

        // 如果不是刷新且有快照缓存，瞬间展现
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
                tvPlazaCurrentTag.setText("当前分类: " + (tagName.length() > 0 ? tagName : "全部分类") + " · " + currentPlazaSort + " (快照)");
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
            footerPlazaLoading.setText("正在刷新第 1 页歌单...");
        } else {
            if (!hasMorePlaza) return;
            footerPlazaLoading.setText("正在加载第 " + currentPlazaPage + " 页...");
        }

        tvPlazaCurrentTag.setText("当前分类: " + (tagName.length() > 0 ? tagName : "全部分类") + " · " + currentPlazaSort + " (第 " + currentPlazaPage + " 页)");
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
                                footerPlazaLoading.setText("已加载全部歌单");
                            } else {
                                currentPlazaPage++;
                                footerPlazaLoading.setText("点击或滑动加载下一页 (第 " + currentPlazaPage + " 页)...");
                            }
                        } else {
                            if (plazaPlaylistsList.isEmpty()) {
                                footerPlazaLoading.setText("暂无歌单数据，点击重试");
                            } else {
                                hasMorePlaza = false;
                                footerPlazaLoading.setText("已加载全部歌单");
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
            if (c > 100000000) return String.format("%.1f亿", c / 100000000.0);
            if (c > 10000) return String.format("%.1f万", c / 10000.0);
            return String.valueOf(c);
        } catch (Exception e) {
            return countStr != null && countStr.length() > 0 ? countStr : "热门";
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
            footerPlazaLoading.setText("正在搜索第 1 页歌单...");
        } else {
            if (!hasMorePlaza) return;
            footerPlazaLoading.setText("正在加载第 " + currentPlazaPage + " 页搜索结果...");
        }

        tvPlazaCurrentTag.setText("搜索歌单: " + currentPlazaSearchKeyword + " (第 " + currentPlazaPage + " 页)");
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
                                footerPlazaLoading.setText("已加载全部搜索结果");
                            } else {
                                currentPlazaPage++;
                                footerPlazaLoading.setText("点击或滑动加载下一页 (第 " + currentPlazaPage + " 页)...");
                            }
                        } else {
                            if (plazaPlaylistsList.isEmpty()) {
                                footerPlazaLoading.setText("未搜到相关歌单");
                            } else {
                                hasMorePlaza = false;
                                footerPlazaLoading.setText("已加载全部搜索结果");
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
        tvTitle.setText("选择分类 (" + LxApiHelper.PLAZA_PLATFORM_NAMES[pos] + ")");

        Button btnClose = (Button) dialogView.findViewById(R.id.btn_category_dialog_close);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });

        final LinearLayout layoutContent = (LinearLayout) dialogView.findViewById(R.id.layout_category_content);
        layoutContent.removeAllViews();
        final float density = getResources().getDisplayMetrics().density;

        final TextView tvLoading = new TextView(this);
        tvLoading.setText("正在联网同步官方最新分类目录...");
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
                            tvEmpty.setText("分类列表联网拉取失败，请检查网络后重试");
                            tvEmpty.setTextColor(0xFFFF5252);
                            tvEmpty.setPadding(0, 30, 0, 30);
                            tvEmpty.setGravity(Gravity.CENTER);
                            layoutContent.addView(tvEmpty);
                            return;
                        }

                        Button btnAll = new Button(MainActivity.this);
                        btnAll.setText("全部分类");
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
                                btnPlazaCategory.setText("全部分类 ▾");
                                dialog.dismiss();
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
                                        btnPlazaCategory.setText(tag.name + " ▾");
                                        dialog.dismiss();
                                        
                                        // 关键优化：强制解除锁定状态，选择分类后立刻加载
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
        input.setHint("输入外部歌单分享链接或歌单ID...");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF777777);
        box.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("导入指定平台歌单")
                .setView(box)
                .setPositiveButton("导入", new DialogInterface.OnClickListener() {
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
                            DisplayEntry entry = new DisplayEntry(srcCode + "_" + playlistId, "导入的歌单", "外部导入", "", null, "歌单", false);
                            openSonglistDetails(entry);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 核心修复点 1：进入歌单曲目列表前深拷贝备份数据，并隐藏模式切换按钮
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
        updatePlazaViewModeState(); // 自动隐藏模式切换按钮并强制列表显示

        tvPlazaCurrentTag.setText("歌单: " + playlistEntry.title);
        plazaPlaylistsList.clear();
        plazaPlaylistsData.clear();
        plazaPlaylistsAdapter.notifyDataSetChanged();
        footerPlazaLoading.setText("正在加载歌单内歌曲...");

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
                        footerPlazaLoading.setText("共 " + plazaPlaylistsList.size() + " 首歌曲 (已全部加载)");
                    }
                });
            }
        }).start();
    }

    // 核心修复点 1：安全退出歌曲列表，瞬间恢复广场快照，绝无网络多余请求与错乱
    private void exitPlazaSongsToPlaylists() {
        isBrowsingPlazaSongs = false;
        btnPlazaBack.setVisibility(View.GONE);

        plazaPlaylistsList.clear();
        plazaPlaylistsList.addAll(backupPlazaList);
        plazaPlaylistsData.clear();
        plazaPlaylistsData.addAll(backupPlazaData);

        tvPlazaCurrentTag.setText(backupPlazaTitle);
        updatePlazaViewModeState(); // 恢复用户偏好的网格/列表

        plazaPlaylistsAdapter.notifyDataSetChanged();
        plazaGridAdapter.notifyDataSetChanged();
    }

    private void loadLeaderboardBoards() {
        int pos = spinnerRankingPlatform.getSelectedItemPosition();
        final String code = LxApiHelper.PLAZA_PLATFORM_CODES[pos >= 0 ? pos : 0];
        tvRankingBoardTitle.setText("正在联网获取官方榜单列表...");

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
                                rankingBoardsList.add(new DisplayEntry(b.id, b.name, "", "榜单", b.coverImg, "排行榜", false));
                                Map<String, String> row = new HashMap<String, String>();
                                row.put("title", b.name);
                                rankingBoardsData.add(row);
                            }
                            rankingBoardsAdapter.notifyDataSetChanged();
                            loadLeaderboardSongs(rankingBoardsList.get(0));
                        } else {
                            tvRankingBoardTitle.setText("官方榜单暂不可用，请稍后刷新重试");
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
                        tvRankingBoardTitle.setText(board.title + " (前 " + rankingSongsList.size() + " 首)");
                    }
                });
            }
        }).start();
    }

    private void showFavAndCustomPlaylists() {
        btnFavBack.setVisibility(View.GONE);
        tvFavTitle.setText("我的收藏与歌单");
        currentActiveFavPlaylistId = null;

        favPlaylistsList.clear();
        favPlaylistsData.clear();

        favPlaylistsList.add(new DisplayEntry("fav_entry", "★ 我喜欢的音乐", "本地及云端", "共 " + favSongIds.size() + " 首", null, "收藏", false));
        Map<String, String> favRow = new HashMap<String, String>();
        favRow.put("title", "★ 我喜欢的音乐");
        favRow.put("subtitle", "共 " + favSongIds.size() + " 首");
        favPlaylistsData.add(favRow);

        favPlaylistsList.add(new DisplayEntry("local_featured", "📁 精选本地歌单", "本地", "共 " + featuredSongs.size() + " 首", null, "本地", false));
        Map<String, String> featRow = new HashMap<String, String>();
        featRow.put("title", "📁 精选本地歌单");
        featRow.put("subtitle", "共 " + featuredSongs.size() + " 首");
        favPlaylistsData.add(featRow);

        favPlaylistsList.add(new DisplayEntry("local_car", "🚗 车载音乐歌单", "本地", "共 " + carSongs.size() + " 首", null, "本地", false));
        Map<String, String> carRow = new HashMap<String, String>();
        carRow.put("title", "🚗 车载音乐歌单");
        carRow.put("subtitle", "共 " + carSongs.size() + " 首");
        favPlaylistsData.add(carRow);

        for (DisplayEntry e : rawServerUserPlaylists) {
            favPlaylistsList.add(e);
            Map<String, String> row = new HashMap<String, String>();
            row.put("title", "☁ " + e.title);
            row.put("subtitle", e.quality + " (自建)");
            favPlaylistsData.add(row);
        }

        favPlaylistsAdapter.notifyDataSetChanged();
    }

    // 核心修复点 4：协调链式同步机制，彻底解决需要多次点击刷新才更新的问题
    private void syncFavoritesAndPlaylists(final boolean showToast) {
        if (showToast) Toast.makeText(this, "正在同步云端收藏与歌单...", Toast.LENGTH_SHORT).show();
        final String currentUser = prefs.getString("user", "admin").trim();

        new Thread(new Runnable() {
            @Override
            public void run() {
                // 1. 同步喜欢歌曲 ID
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

                // 2. 同步自建歌单列表
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

                // 3. 在主线程原子性提交并刷新 UI，一次搞定
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
                        if (showToast) Toast.makeText(MainActivity.this, "收藏与歌单同步完成", Toast.LENGTH_SHORT).show();
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

        if ("我喜欢的音乐".equals(name) || "starred".equals(nLower) || "favorites".equals(nLower) || "favourite".equals(nLower)) return;
        if (name.contains("榜") || name.startsWith("Top") || name.startsWith("TOP") || name.endsWith("榜") || name.contains("热搜") || name.contains("指数")) return;
        if (p.has("owner") && currentUser.length() > 0 && !currentUser.equalsIgnoreCase(p.optString("owner", "").trim())) return;
        if (p.optBoolean("public", false) && p.has("owner") && currentUser.length() > 0 && !currentUser.equalsIgnoreCase(p.optString("owner", "").trim())) return;

        sink.add(new DisplayEntry(id, name, "", "歌曲数: " + count + " 首", null, count + " 首", false));
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
        tvLocalPathStatus.setText("本地目录: " + customPath);
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
                        String artist = "未知歌手";
                        try {
                            mmr.setDataSource(f.getAbsolutePath());
                            String metaTitle = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                            String metaArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                            if (metaTitle != null && metaTitle.trim().length() > 0) title = metaTitle.trim();
                            if (metaArtist != null && metaArtist.trim().length() > 0) artist = metaArtist.trim();
                        } catch (Exception ignored) {}

                        double mb = f.length() / (1024.0 * 1024.0);
                        DisplayEntry entry = new DisplayEntry("local_file:" + f.getAbsolutePath(),
                                title, artist, artist + String.format(" (%.1f MB)", mb), null, "本地", true, 320, f.getAbsolutePath());
                        localMusicList.add(entry);
                        Map<String, String> row = new HashMap<String, String>();
                        row.put("title", title);
                        row.put("subtitle", artist + String.format(" [%.1f MB]", mb));
                        localMusicData.add(row);
                    }
                }
            }
            try { mmr.release(); } catch (Throwable ignored) {}
        }
        localMusicAdapter.notifyDataSetChanged();
        Toast.makeText(this, "扫描完成，共找到 " + localMusicList.size() + " 首本地歌曲", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(MainActivity.this, "歌单广场音质已设为: " + PLAZA_BITRATE_LABELS[position], Toast.LENGTH_SHORT).show();
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
        Toast.makeText(this, "音质已切换为: " + label, Toast.LENGTH_SHORT).show();
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
            return streamBitrate.toUpperCase() + (streamBitrate.matches("\\d+") ? "K" : "");
        }
        return plazaBitrate.toUpperCase() + (plazaBitrate.matches("\\d+") ? "K" : "");
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
                btnPlazaCategory.setText("全部分类 ▾");
                setupPlazaSortButtons();
                
                // 关键优化：强制解除锁定状态，确保切平台秒加载
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

        // 仅在歌单广场封面界面切换模式，且即刻持久化
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

        // 返回按钮修复：直接调用快照还原，恢复原本的模式并消除混乱
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

        // 绑定收藏界面的真实同步刷新监听
        if (btnFavRefresh != null) {
            btnFavRefresh.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (currentActiveFavPlaylistId != null) {
                        Toast.makeText(MainActivity.this, "正在刷新歌单...", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(MainActivity.this, "本地缓存已清空", Toast.LENGTH_SHORT).show();
            }
        });

        setupPlaybackControls();
    }

    private void showArtistOrAlbumLongClickMenu(final DisplayEntry entry) {
        ArrayList<String> optList = new ArrayList<String>();
        optList.add("▶ 展开查看内含歌曲");
        optList.add("▶ 播放全部歌曲");
        optList.add("＋ 添加全部到播放队列");
        final String[] options = optList.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle((entry.id.startsWith("artist_") ? "歌手: " : "专辑: ") + entry.title)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            if (entry.id.startsWith("album_")) fetchAlbumSongs(entry.id.substring(6), entry.title);
                            else if (entry.id.startsWith("artist_")) fetchArtistAlbums(entry.id.substring(7), entry.title);
                        } else if (which == 1) {
                            playAllFromPlaylist(entry);
                        } else if (which == 2) {
                            addAllToQueueFromPlaylist(entry);
                        }
                    }
                })
                .show();
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

    private void setupPlaybackControls() {
        View.OnClickListener favClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<MusicService.SongItem> q = MusicService.getPlaylist();
                int idx = MusicService.getCurrentIndex();
                if (q != null && idx >= 0 && idx < q.size()) {
                    String sid = q.get(idx).id;
                    serverStarSong(sid, !isFav(sid));
                } else {
                    Toast.makeText(MainActivity.this, "当前未播放任何歌曲", Toast.LENGTH_SHORT).show();
                }
            }
        };
        btnBottomFav.setOnClickListener(favClickListener);
        btnDetailFav.setOnClickListener(favClickListener);

        if (btnDetailDlna != null) {
            btnDetailDlna.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (DlnaManager.isCasting()) {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("DLNA 投播中")
                                .setMessage("当前设备: " + DlnaManager.getCurrentDevice().name + "\n\n是否断开投播？")
                                .setPositiveButton("断开", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        DlnaManager.disconnect();
                                        dlnaSyncHandler.removeCallbacks(dlnaSyncRunnable);
                                        btnDetailDlna.setText("DLNA");
                                        btnDetailDlna.setTextColor(0xFF00E5FF);
                                        dlnaGlobalLyricOffsetMs = 0;
                                        updateLyricOffsetStatusView();
                                        rebuildActiveLyricsView();
                                        Intent muteIntent = new Intent(MainActivity.this, MusicService.class);
                                        muteIntent.setAction(MusicService.ACTION_SET_MUTE);
                                        muteIntent.putExtra("is_muted", false);
                                        startService(muteIntent);
                                        Toast.makeText(MainActivity.this, "已断开 DLNA 投播", Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    } else {
                        Toast.makeText(MainActivity.this, "正在搜索局域网 DLNA 设备...", Toast.LENGTH_SHORT).show();
                        final ArrayList<DlnaManager.Device> foundDevices = new ArrayList<DlnaManager.Device>();
                        final ArrayList<String> deviceNames = new ArrayList<String>();
                        final ArrayAdapter<String> devAdapter = new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_list_item_1, deviceNames);
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("选择投播设备 (DLNA)")
                                .setAdapter(devAdapter, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int which) {
                                        if (which >= 0 && which < foundDevices.size()) {
                                            startDlnaCast(foundDevices.get(which));
                                        }
                                    }
                                })
                                .setNegativeButton("取消", null)
                                .show();

                        DlnaManager.searchDevices(MainActivity.this, new DlnaManager.DiscoveryCallback() {
                            @Override
                            public void onDeviceFound(DlnaManager.Device device) {
                                for (DlnaManager.Device d : foundDevices) {
                                    if (d.location.equals(device.location)) return;
                                }
                                foundDevices.add(device);
                                deviceNames.add("▶ " + device.name);
                                devAdapter.notifyDataSetChanged();
                            }
                        });
                    }
                }
            });
        }

        View.OnClickListener toggleListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DlnaManager.isCasting()) {
                    if (isCurrentSongPlaying) DlnaManager.pause();
                    else DlnaManager.resume();
                }
                startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_TOGGLE));
                Toast.makeText(MainActivity.this, isCurrentSongPlaying ? "已暂停" : "继续播放", Toast.LENGTH_SHORT).show();
            }
        };
        btnPlayPause.setOnClickListener(toggleListener);
        btnDetailPlayPause.setOnClickListener(toggleListener);

        View.OnClickListener nextListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_NEXT));
                Toast.makeText(MainActivity.this, "下一首", Toast.LENGTH_SHORT).show();
            }
        };
        btnNext.setOnClickListener(nextListener);
        btnDetailNext.setOnClickListener(nextListener);

        View.OnClickListener prevListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_PREV));
                Toast.makeText(MainActivity.this, "上一首", Toast.LENGTH_SHORT).show();
            }
        };
        btnPrev.setOnClickListener(prevListener);
        btnDetailPrev.setOnClickListener(prevListener);

        View.OnClickListener modeListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_CYCLE_MODE));
                int nextMode = (MusicService.getCurrentMode() + 1) % 3;
                String mName = (nextMode == MusicService.MODE_SHUFFLE) ? "随机播放" : (nextMode == MusicService.MODE_SINGLE ? "单曲循环" : "列表循环");
                Toast.makeText(MainActivity.this, "播放模式: " + mName, Toast.LENGTH_SHORT).show();
            }
        };
        btnMode.setOnClickListener(modeListener);
        btnDetailMode.setOnClickListener(modeListener);

        View.OnClickListener eqListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) { new EqualizerDialog(MainActivity.this).show(); }
        };
        btnOpenEq.setOnClickListener(eqListener);
        btnDetailEq.setOnClickListener(eqListener);

        ivBottomCover.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { layoutDetailOverlay.setVisibility(View.VISIBLE); }
        });
        btnCloseDetail.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { layoutDetailOverlay.setVisibility(View.GONE); }
        });

        btnToggleQueue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (layoutQueuePanel.getVisibility() == View.VISIBLE) {
                    layoutQueuePanel.setVisibility(View.GONE);
                } else {
                    refreshQueueList();
                    layoutQueuePanel.setVisibility(View.VISIBLE);
                    scrollToCurrentPlayingInQueue();
                }
            }
        });
        btnCloseQueue.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { layoutQueuePanel.setVisibility(View.GONE); }
        });

        AdapterView.OnItemClickListener queueItemClickListener = new AdapterView.OnItemClickListener() {
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
        lvQueue.setOnItemClickListener(queueItemClickListener);
        lvDetailQueue.setOnItemClickListener(queueItemClickListener);

        SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    String t = formatTime(progress) + " / " + formatTime(sb.getMax());
                    tvTime.setText(t);
                    tvDetailTime.setText(t);
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

        View.OnClickListener coverToggleListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) { toggleCoverDisplayMode(); }
        };
        layoutVinylContainer.setOnClickListener(coverToggleListener);
        ivVinylCircularCover.setOnClickListener(coverToggleListener);
        viewTonearm.setOnClickListener(coverToggleListener);
        ivSquareCover.setOnClickListener(coverToggleListener);

        layoutDetailBottomBlank.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleDetailQueueView(); }
        });
        btnDetailQueue.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleDetailQueueView(); }
        });

        btnDetailKeepScreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isKeepScreenOn = !isKeepScreenOn;
                if (isKeepScreenOn) {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    btnDetailKeepScreen.setText("常亮:开");
                    Toast.makeText(MainActivity.this, "屏幕常亮已开启", Toast.LENGTH_SHORT).show();
                } else {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    btnDetailKeepScreen.setText("常亮:关");
                    Toast.makeText(MainActivity.this, "屏幕常亮已关闭", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnDetailDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<MusicService.SongItem> q = MusicService.getPlaylist();
                int idx = MusicService.getCurrentIndex();
                if (q != null && idx >= 0 && idx < q.size()) {
                    MusicService.SongItem cur = q.get(idx);
                    downloadSongItem(new DisplayEntry(cur.id, cur.title, cur.artist, "", cur.coverArtId, cur.quality, true));
                } else {
                    Toast.makeText(MainActivity.this, "当前无播放歌曲", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnLyricDec.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { applyLyricFontSize(-2); }
        });
        btnLyricInc.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { applyLyricFontSize(2); }
        });

        btnLyricDelay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { adjustLyricOffset(-500); }
        });
        btnLyricReset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { resetLyricOffset(); }
        });
        btnLyricAdvance.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { adjustLyricOffset(500); }
        });

        scrollLyrics.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                    isUserTouchingLyrics = true;
                    lyricHandler.removeCallbacksAndMessages(null);
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    lyricHandler.postDelayed(new Runnable() {
                        @Override public void run() { isUserTouchingLyrics = false; }
                    }, 3000);
                }
                return false;
            }
        });
    }

    private void startDlnaCast(DlnaManager.Device targetDev) {
        ArrayList<MusicService.SongItem> queue = MusicService.getPlaylist();
        int curIdx = MusicService.getCurrentIndex();
        if (queue != null && curIdx >= 0 && curIdx < queue.size()) {
            MusicService.SongItem current = queue.get(curIdx);
            int currentPos = seekBar != null ? seekBar.getProgress() : 0;
            DlnaManager.playUrl(targetDev, current.streamUrl, current.title, current.artist, currentPos);
            Intent muteIntent = new Intent(MainActivity.this, MusicService.class);
            muteIntent.setAction(MusicService.ACTION_SET_MUTE);
            muteIntent.putExtra("is_muted", true);
            startService(muteIntent);
            if (btnDetailDlna != null) {
                btnDetailDlna.setText("已投: " + (targetDev.name.length() > 5 ? targetDev.name.substring(0, 5) + ".." : targetDev.name));
                btnDetailDlna.setTextColor(0xFFFF4081);
            }
            updateLyricOffsetStatusView();
            rebuildActiveLyricsView();
            dlnaSyncHandler.removeCallbacks(dlnaSyncRunnable);
            dlnaSyncHandler.postDelayed(dlnaSyncRunnable, 1000);
            Toast.makeText(this, "正在投播至 " + targetDev.name, Toast.LENGTH_LONG).show();
        }
    }

    private void loadLyrics(final String songId, final String artist, final String title) {
        currentSongIdForLyric = songId;
        currentArtistForLyric = artist;
        currentTitleForLyric = title;
        lyricRows.clear();
        currentLyricIndex = -1;
        layoutLyricsContainer.removeAllViews();

        TextView loadingTv = new TextView(this);
        loadingTv.setText("正在加载歌词...");
        loadingTv.setTextColor(0xFF888888);
        loadingTv.setGravity(Gravity.CENTER);
        layoutLyricsContainer.addView(loadingTv);

        new Thread(new Runnable() {
            @Override
            public void run() {
                String lyricsText = null;
                if (songId != null && songId.length() > 0) {
                    try {
                        String res = requestApi("getLyricsBySongId.view?id=" + URLEncoder.encode(songId, "UTF-8") + "&" + getAuthParams());
                        lyricsText = parseLyricsFromJson(res);
                    } catch (Throwable ignored) {}
                }
                if (lyricsText == null && title != null && title.length() > 0) {
                    try {
                        String p = "artist=" + URLEncoder.encode(artist != null ? artist : "", "UTF-8")
                                + "&title=" + URLEncoder.encode(title, "UTF-8");
                        String res = requestApi("getLyrics.view?" + p + "&" + getAuthParams());
                        lyricsText = parseLyricsFromJson(res);
                    } catch (Throwable ignored) {}
                }
                if (lyricsText == null && title != null) {
                    String cleanTitle = title.replaceAll("\\([^)]*\\)", "").replaceAll("\\[[^\\]]*\\]", "").trim();
                    if (cleanTitle.length() > 0 && !cleanTitle.equals(title)) {
                        try {
                            String p = "artist=" + URLEncoder.encode(artist != null ? artist : "", "UTF-8")
                                    + "&title=" + URLEncoder.encode(cleanTitle, "UTF-8");
                            String res = requestApi("getLyrics.view?" + p + "&" + getAuthParams());
                            lyricsText = parseLyricsFromJson(res);
                        } catch (Throwable ignored) {}
                    }
                }

                final String finalLyrics = lyricsText;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        currentLoadedRawLyrics = finalLyrics;
                        if (finalLyrics != null && finalLyrics.trim().length() > 0) {
                            buildLyricsView(finalLyrics);
                        } else {
                            showSimpleLyric("暂无歌词");
                        }
                    }
                });
            }
        }).start();
    }

    private String parseLyricsFromJson(String jsonStr) {
        if (jsonStr == null || jsonStr.length() == 0) return null;
        try {
            JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
            if (root.has("status") && !"ok".equalsIgnoreCase(root.getString("status"))) return null;
            if (root.has("lyricsList")) {
                JSONObject list = root.optJSONObject("lyricsList");
                if (list != null && list.has("structuredLyrics")) {
                    Object slObj = list.get("structuredLyrics");
                    JSONObject targetSL = null;
                    if (slObj instanceof JSONArray) {
                        JSONArray arr = (JSONArray) slObj;
                        if (arr.length() > 0) targetSL = arr.getJSONObject(0);
                    } else if (slObj instanceof JSONObject) {
                        targetSL = (JSONObject) slObj;
                    }
                    if (targetSL != null && targetSL.has("line")) {
                        Object lineObj = targetSL.get("line");
                        StringBuilder lrcBuilder = new StringBuilder();
                        if (lineObj instanceof JSONArray) {
                            JSONArray lArr = (JSONArray) lineObj;
                            for (int i = 0; i < lArr.length(); i++) appendStructuredLrcLine(lrcBuilder, lArr.getJSONObject(i));
                        } else if (lineObj instanceof JSONObject) {
                            appendStructuredLrcLine(lrcBuilder, (JSONObject) lineObj);
                        }
                        String candidate = lrcBuilder.toString();
                        if (isValidLyrics(candidate)) return candidate;
                    }
                }
            }
            if (root.has("lyrics")) {
                Object lyricsObj = root.get("lyrics");
                String candidate = null;
                if (lyricsObj instanceof JSONObject) candidate = ((JSONObject) lyricsObj).optString("content", ((JSONObject) lyricsObj).optString("value", ""));
                else if (lyricsObj instanceof String) candidate = (String) lyricsObj;
                if (isValidLyrics(candidate)) return candidate;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void appendStructuredLrcLine(StringBuilder sb, JSONObject l) {
        long startMs = l.optLong("start", 0);
        String val = l.optString("value", "");
        int sec = (int) ((startMs / 1000) % 60);
        int min = (int) ((startMs / (1000 * 60)) % 60);
        int cs = (int) ((startMs % 1000) / 10);
        sb.append(String.format("[%02d:%02d.%02d]", min, sec, cs)).append(val).append("\n");
    }

    private boolean isValidLyrics(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase();
        return t.length() > 0 && !t.contains("lyrics not found") && !t.contains("no lyrics available") && !t.contains("error:");
    }

    private void showSimpleLyric(String msg) {
        layoutLyricsContainer.removeAllViews();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setTextColor(0xFF888888);
        tv.setTextSize(lyricBaseFontSize);
        box.addView(tv);

        Button btnRetryOnline = new Button(this);
        btnRetryOnline.setText("在线搜索歌词");
        btnRetryOnline.setTextColor(0xFF00E5FF);
        btnRetryOnline.setTextSize(12);
        btnRetryOnline.setBackgroundResource(R.drawable.bg_btn_default);
        btnRetryOnline.setPadding(20, 8, 20, 8);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 16;
        btnRetryOnline.setLayoutParams(lp);
        btnRetryOnline.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                fetchOnlineThirdPartyLyrics(currentTitleForLyric, currentArtistForLyric);
            }
        });
        box.addView(btnRetryOnline);
        layoutLyricsContainer.addView(box);
    }

    private void fetchOnlineThirdPartyLyrics(final String songTitle, final String artistName) {
        layoutLyricsContainer.removeAllViews();
        TextView loadingTv = new TextView(this);
        loadingTv.setText("正在在线检索歌词...");
        loadingTv.setTextColor(0xFF00E5FF);
        layoutLyricsContainer.addView(loadingTv);

        new Thread(new Runnable() {
            @Override
            public void run() {
                String fetchedLrc = null;
                try {
                    String query = (songTitle != null ? songTitle : "") + " " + (artistName != null ? artistName : "");
                    query = query.replaceAll("\\([^)]*\\)", "").replaceAll("\\[[^\\]]*\\]", "").trim();
                    String searchUrl = "https://music.163.com/api/search/get/web?s=" + URLEncoder.encode(query, "UTF-8") + "&type=1&offset=0&total=true&limit=1";
                    String searchRes = LxApiHelper.httpGet(searchUrl);
                    if (searchRes != null && searchRes.contains("\"songs\"")) {
                        JSONArray songsArr = new JSONObject(searchRes).getJSONObject("result").getJSONArray("songs");
                        if (songsArr.length() > 0) {
                            long neteaseSongId = songsArr.getJSONObject(0).getLong("id");
                            String lrcUrl = "https://music.163.com/api/song/lyric?os=pc&id=" + neteaseSongId + "&lv=-1&kv=-1&tv=-1";
                            String lrcRes = LxApiHelper.httpGet(lrcUrl);
                            if (lrcRes != null && lrcRes.contains("\"lrc\"")) {
                                String candidate = new JSONObject(lrcRes).getJSONObject("lrc").getString("lyric");
                                if (isValidLyrics(candidate)) fetchedLrc = candidate;
                            }
                        }
                    }
                } catch (Throwable ignored) {}

                final String finalLrc = fetchedLrc;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (finalLrc != null) {
                            currentLoadedRawLyrics = finalLrc;
                            buildLyricsView(finalLrc);
                            int curPos = detailSeekBar != null ? detailSeekBar.getProgress() : 0;
                            updateLyricPosition(curPos);
                            Toast.makeText(MainActivity.this, "在线歌词匹配成功", Toast.LENGTH_SHORT).show();
                        } else {
                            showSimpleLyric("未能匹配到在线歌词");
                        }
                    }
                });
            }
        }).start();
    }

    private void buildLyricsView(String rawText) {
        layoutLyricsContainer.removeAllViews();
        lyricRows.clear();
        currentLyricIndex = -1;
        long headerOffsetMs = 0;
        String[] lines = rawText.split("\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.length() == 0) continue;
            if (line.toLowerCase().startsWith("[offset:") && line.endsWith("]")) {
                try {
                    headerOffsetMs = Long.parseLong(line.substring(8, line.length() - 1).trim());
                } catch (Exception ignored) {}
                continue;
            }
            int closeBracket = line.indexOf(']');
            if (line.startsWith("[") && closeBracket > 1) {
                String timePart = line.substring(1, closeBracket);
                long timeMs = parseTime(timePart);
                if (timeMs >= 0) {
                    long finalCalculatedTime = timeMs + headerOffsetMs + getCurrentEffectiveOffsetMs();
                    if (finalCalculatedTime < 0) finalCalculatedTime = 0;
                    String content = line.substring(closeBracket + 1).trim();
                    if (content.length() == 0) content = " ";
                    lyricRows.add(new LyricRow(finalCalculatedTime, content));
                }
            }
        }
        if (lyricRows.isEmpty()) {
            for (String raw : lines) {
                if (raw.trim().length() == 0) continue;
                TextView tv = new TextView(this);
                tv.setText(raw.trim());
                tv.setTextColor(0xFFCCCCCC);
                tv.setTextSize(lyricBaseFontSize);
                tv.setGravity(Gravity.CENTER);
                tv.setPadding(0, 10, 0, 10);
                layoutLyricsContainer.addView(tv);
            }
            return;
        }
        Collections.sort(lyricRows, new Comparator<LyricRow>() {
            @Override public int compare(LyricRow a, LyricRow b) {
                return Long.valueOf(a.timeMs).compareTo(b.timeMs);
            }
        });
        for (LyricRow row : lyricRows) {
            TextView tv = new TextView(this);
            tv.setText(row.text);
            tv.setTextColor(0xFF777777);
            tv.setTextSize(lyricBaseFontSize);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, 12, 0, 12);
            row.view = tv;
            layoutLyricsContainer.addView(tv);
        }
    }

    private void updateLyricPosition(int currentPosMs) {
        if (lyricRows.isEmpty() || isUserTouchingLyrics) return;
        int targetIndex = -1;
        for (int i = 0; i < lyricRows.size(); i++) {
            if (currentPosMs >= lyricRows.get(i).timeMs) targetIndex = i;
            else break;
        }
        if (targetIndex != currentLyricIndex && targetIndex >= 0) {
            if (currentLyricIndex >= 0 && currentLyricIndex < lyricRows.size()) {
                LyricRow oldRow = lyricRows.get(currentLyricIndex);
                if (oldRow.view != null) {
                    oldRow.view.setTextColor(0xFF777777);
                    oldRow.view.setTextSize(lyricBaseFontSize);
                    oldRow.view.setTypeface(Typeface.DEFAULT);
                }
            }
            currentLyricIndex = targetIndex;
            final LyricRow curRow = lyricRows.get(currentLyricIndex);
            if (curRow.view != null) {
                curRow.view.setTextColor(0xFF00E5FF);
                curRow.view.setTextSize(lyricBaseFontSize + 5);
                curRow.view.setTypeface(Typeface.DEFAULT_BOLD);
                scrollLyrics.post(new Runnable() {
                    @Override
                    public void run() {
                        int scrollY = curRow.view.getTop() - (scrollLyrics.getHeight() / 2) + (curRow.view.getHeight() / 2);
                        if (scrollY < 0) scrollY = 0;
                        scrollLyrics.smoothScrollTo(0, scrollY);
                    }
                });
            }
        }
    }

    private long getCurrentEffectiveOffsetMs() {
        return DlnaManager.isCasting() ? dlnaGlobalLyricOffsetMs : manualLyricOffsetMs;
    }

    private void adjustLyricOffset(long deltaMs) {
        if (DlnaManager.isCasting()) dlnaGlobalLyricOffsetMs += deltaMs;
        else manualLyricOffsetMs += deltaMs;
        updateLyricOffsetStatusView();
        rebuildActiveLyricsView();
        double sec = getCurrentEffectiveOffsetMs() / 1000.0;
        Toast.makeText(this, "歌词微调: " + (sec >= 0 ? "+" : "") + sec + "s", Toast.LENGTH_SHORT).show();
    }

    private void resetLyricOffset() {
        if (DlnaManager.isCasting()) dlnaGlobalLyricOffsetMs = 0;
        else manualLyricOffsetMs = 0;
        updateLyricOffsetStatusView();
        rebuildActiveLyricsView();
        Toast.makeText(this, "歌词偏移已复位 (0.0s)", Toast.LENGTH_SHORT).show();
    }

    private void rebuildActiveLyricsView() {
        if (currentLoadedRawLyrics != null && currentLoadedRawLyrics.length() > 0) {
            buildLyricsView(currentLoadedRawLyrics);
            int curPos = detailSeekBar != null ? detailSeekBar.getProgress() : 0;
            updateLyricPosition(curPos);
        }
    }

    private void updateLyricOffsetStatusView() {
        if (tvLyricOffsetStatus != null) {
            double sec = getCurrentEffectiveOffsetMs() / 1000.0;
            tvLyricOffsetStatus.setText(String.format("%s%.1fs", (sec > 0 ? "+" : ""), sec));
        }
    }

    private void applyLyricFontSize(int delta) {
        lyricBaseFontSize = Math.max(11, Math.min(26, lyricBaseFontSize + delta));
        prefs.edit().putInt("lyric_font_size", lyricBaseFontSize).apply();
        for (int i = 0; i < lyricRows.size(); i++) {
            LyricRow row = lyricRows.get(i);
            if (row.view != null) {
                row.view.setTextSize(i == currentLyricIndex ? (lyricBaseFontSize + 5) : lyricBaseFontSize);
            }
        }
        Toast.makeText(this, "歌词字号: " + lyricBaseFontSize + "sp", Toast.LENGTH_SHORT).show();
    }

    private void loadCoverArt(final String coverId) {
        if (coverId == null || coverId.length() == 0) {
            ivVinylCircularCover.setImageResource(android.R.drawable.ic_menu_report_image);
            ivSquareCover.setImageResource(android.R.drawable.ic_menu_report_image);
            ivBottomCover.setImageResource(R.drawable.ic_launcher);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                String base = prefs.getString("server", "");
                if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
                String urlStr = coverId.startsWith("http://") || coverId.startsWith("https://") ? coverId
                        : (base + "/rest/getCoverArt.view?id=" + coverId + "&size=400&" + getAuthParams());
                try {
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);
                    if (conn instanceof HttpsURLConnection) {
                        ((HttpsURLConnection) conn).setSSLSocketFactory(new TLSSocketFactory());
                    }
                    InputStream is = conn.getInputStream();
                    final Bitmap bmp = BitmapFactory.decodeStream(is);
                    is.close();
                    conn.disconnect();
                    if (bmp != null) {
                        final Bitmap circular = getCircularBitmap(bmp, 240);
                        float density = getResources().getDisplayMetrics().density;
                        final Bitmap bottomRounded = getRoundedCornerBitmap(bmp, (int) (95 * density), 10 * density);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (currentRawCoverBitmap != null && !currentRawCoverBitmap.isRecycled()) currentRawCoverBitmap.recycle();
                                if (currentCircularCoverBitmap != null && !currentCircularCoverBitmap.isRecycled()) currentCircularCoverBitmap.recycle();
                                if (currentBottomCoverBitmap != null && !currentBottomCoverBitmap.isRecycled()) currentBottomCoverBitmap.recycle();

                                currentRawCoverBitmap = bmp;
                                currentCircularCoverBitmap = circular;
                                currentBottomCoverBitmap = bottomRounded;

                                ivVinylCircularCover.setImageBitmap(circular);
                                ivSquareCover.setImageBitmap(bmp);
                                ivBottomCover.setImageBitmap(bottomRounded);
                            }
                        });
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private Bitmap getCircularBitmap(Bitmap bitmap, int targetSize) {
        Bitmap output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        canvas.drawCircle(targetSize / 2f, targetSize / 2f, targetSize / 2f, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        int minEdge = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Rect src = new Rect((bitmap.getWidth() - minEdge) / 2, (bitmap.getHeight() - minEdge) / 2,
                (bitmap.getWidth() + minEdge) / 2, (bitmap.getHeight() + minEdge) / 2);
        canvas.drawBitmap(bitmap, src, new Rect(0, 0, targetSize, targetSize), paint);
        return output;
    }

    private Bitmap getRoundedCornerBitmap(Bitmap bitmap, int targetSize, float cornerRadiusPx) {
        Bitmap output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF rectF = new RectF(0, 0, targetSize, targetSize);
        canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        int minEdge = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Rect src = new Rect((bitmap.getWidth() - minEdge) / 2, (bitmap.getHeight() - minEdge) / 2,
                (bitmap.getWidth() + minEdge) / 2, (bitmap.getHeight() + minEdge) / 2);
        canvas.drawBitmap(bitmap, src, new Rect(0, 0, targetSize, targetSize), paint);
        return output;
    }

    private void showSongLongClickMenu(final DisplayEntry entry, final int position) {
        if (!entry.isSong) return;
        final boolean fav = isFav(entry.id);
        ArrayList<String> optList = new ArrayList<String>();
        optList.add(fav ? "♥ 取消收藏" : "♡ 收藏到我的音乐");
        optList.add("▶ 下一首播放");
        optList.add("＋ 添加到播放队列");
        optList.add("📁 收藏到歌单...");
        optList.add("⬇ 下载歌曲文件");
        if (currentPage == PAGE_FAV && currentActiveFavPlaylistId != null) {
            optList.add("✕ 从当前列表移除");
        }
        final String[] options = optList.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle(entry.title + " - " + entry.artist)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String opt = options[which];
                        if (opt.contains("收藏到我的音乐") || opt.contains("取消收藏")) {
                            serverStarSong(entry.id, !fav);
                        } else if (opt.contains("下一首播放")) {
                            insertNextToPlay(entry);
                        } else if (opt.contains("添加到播放队列")) {
                            addToPlayQueue(entry);
                        } else if (opt.contains("收藏到歌单")) {
                            showAddToPlaylistDialog(entry);
                        } else if (opt.contains("下载歌曲文件")) {
                            downloadSongItem(entry);
                        } else if (opt.contains("从当前列表移除")) {
                            removeFromCurrentView(position, entry);
                        }
                    }
                })
                .show();
    }

    private void insertNextToPlay(DisplayEntry entry) {
        ArrayList<MusicService.SongItem> queue = MusicService.getPlaylist();
        int curIdx = MusicService.getCurrentIndex();
        String playUrl;
        if (entry.localPath != null && entry.localPath.length() > 0) {
            playUrl = "file://" + entry.localPath;
        } else if (entry.id != null && entry.id.startsWith("local_file:")) {
            playUrl = "file://" + entry.id.substring(11);
        } else {
            playUrl = buildStreamUrl(entry.id, getSavedBitrate());
        }
        MusicService.SongItem item = new MusicService.SongItem(entry.id, entry.title, entry.artist, playUrl, entry.coverArt, entry.quality);
        if (queue.isEmpty() || curIdx < 0) {
            queue.add(item);
            MusicService.setQueue(queue, 0, this);
        } else {
            queue.add(curIdx + 1, item);
        }
        refreshQueueList();
        scrollToCurrentPlayingInQueue();
        Toast.makeText(this, "已加入下一首播放: " + entry.title, Toast.LENGTH_SHORT).show();
    }

    private void addToPlayQueue(DisplayEntry entry) {
        ArrayList<MusicService.SongItem> queue = MusicService.getPlaylist();
        String playUrl;
        if (entry.localPath != null && entry.localPath.length() > 0) {
            playUrl = "file://" + entry.localPath;
        } else if (entry.id != null && entry.id.startsWith("local_file:")) {
            playUrl = "file://" + entry.id.substring(11);
        } else {
            playUrl = buildStreamUrl(entry.id, getSavedBitrate());
        }
        MusicService.SongItem item = new MusicService.SongItem(entry.id, entry.title, entry.artist, playUrl, entry.coverArt, entry.quality);
        if (queue.isEmpty()) {
            queue.add(item);
            MusicService.setQueue(queue, 0, this);
        } else {
            queue.add(item);
        }
        refreshQueueList();
        scrollToCurrentPlayingInQueue();
        Toast.makeText(this, "已添加到播放队列末尾: " + entry.title, Toast.LENGTH_SHORT).show();
    }

    private void showPlaylistLongClickMenu(final DisplayEntry playlistEntry) {
        boolean isPlatformPlazaPlaylist = playlistEntry.id.startsWith("tx_") || playlistEntry.id.startsWith("wy_")
                || playlistEntry.id.startsWith("kg_") || playlistEntry.id.startsWith("kw_") || playlistEntry.id.startsWith("mg_");
        boolean isLocalFixedPlaylist = "fav_entry".equals(playlistEntry.id) || "local_featured".equals(playlistEntry.id)
                || "local_car".equals(playlistEntry.id);

        ArrayList<String> optList = new ArrayList<String>();
        optList.add("▶ 播放全部歌曲");
        optList.add("＋ 添加全部到播放队列");

        if (isPlatformPlazaPlaylist) {
            optList.add("⭐ 收藏歌单 (添加到我的歌单)");
        } else if (!isLocalFixedPlaylist) {
            optList.add("✏ 重命名歌单");
            optList.add("🗑 删除歌单");
        }

        final String[] options = optList.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("歌单操作: " + playlistEntry.title)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String opt = options[which];
                        if (opt.contains("播放全部歌曲")) {
                            playAllFromPlaylist(playlistEntry);
                        } else if (opt.contains("添加全部到播放队列")) {
                            addAllToQueueFromPlaylist(playlistEntry);
                        } else if (opt.contains("收藏歌单")) {
                            bookmarkPlazaPlaylist(playlistEntry);
                        } else if (opt.contains("重命名歌单")) {
                            promptRenamePlaylist(playlistEntry);
                        } else if (opt.contains("删除歌单")) {
                            promptDeletePlaylist(playlistEntry);
                        }
                    }
                })
                .show();
    }

    private void bookmarkPlazaPlaylist(final DisplayEntry playlistEntry) {
        Toast.makeText(this, "正在拉取全量歌曲并导入歌单...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final ArrayList<DisplayEntry> songs = LxApiHelper.fetchPlaylistSongs(playlistEntry.id, getPlazaEffectiveBitrateLabel());
                    String plName = "【收藏】" + playlistEntry.title;
                    String createParam = "name=" + URLEncoder.encode(plName, "UTF-8");
                    String res = requestApi("createPlaylist.view?" + createParam + "&" + getAuthParams());
                    String newPlId = null;
                    if (res != null) {
                        JSONObject root = new JSONObject(res).getJSONObject("subsonic-response");
                        JSONObject pl = root.optJSONObject("playlist");
                        if (pl != null) newPlId = pl.optString("id");
                    }
                    if (newPlId != null && !songs.isEmpty()) {
                        for (DisplayEntry s : songs) {
                            String addParam = "playlistId=" + URLEncoder.encode(newPlId, "UTF-8") + "&songIdToAdd=" + URLEncoder.encode(s.id, "UTF-8");
                            requestApi("updatePlaylist.view?" + addParam + "&" + getAuthParams());
                        }
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "已成功收藏到「我的收藏与歌单」！", Toast.LENGTH_SHORT).show();
                            syncFavoritesAndPlaylists(false);
                        }
                    });
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void playAllFromPlaylist(final DisplayEntry pl) {
        Toast.makeText(this, "正在载入全部歌曲...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<DisplayEntry> songs;
                if ("fav_entry".equals(pl.id)) {
                    songs = new ArrayList<DisplayEntry>();
                    for (DisplayEntry s : favPlaylistsList) if (s.isSong) songs.add(s);
                } else if (pl.id.startsWith("tx_") || pl.id.startsWith("wy_") || pl.id.startsWith("kg_") || pl.id.startsWith("kw_") || pl.id.startsWith("mg_")) {
                    songs = LxApiHelper.fetchPlaylistSongs(pl.id, getPlazaEffectiveBitrateLabel());
                } else if ("local_featured".equals(pl.id)) {
                    songs = featuredSongs;
                } else if ("local_car".equals(pl.id)) {
                    songs = carSongs;
                } else {
                    songs = fetchSongsForSubsonicPlaylistSync(pl.id);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (songs != null && !songs.isEmpty()) {
                            playSongInList(songs, songs.get(0));
                            Toast.makeText(MainActivity.this, "开始播放: " + pl.title, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "未能获取到歌曲列表", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void addAllToQueueFromPlaylist(final DisplayEntry pl) {
        Toast.makeText(this, "正在将歌曲加入队列...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<DisplayEntry> songs;
                if ("fav_entry".equals(pl.id)) {
                    songs = new ArrayList<DisplayEntry>();
                    for (DisplayEntry s : favPlaylistsList) if (s.isSong) songs.add(s);
                } else if (pl.id.startsWith("tx_") || pl.id.startsWith("wy_") || pl.id.startsWith("kg_") || pl.id.startsWith("kw_") || pl.id.startsWith("mg_")) {
                    songs = LxApiHelper.fetchPlaylistSongs(pl.id, getPlazaEffectiveBitrateLabel());
                } else if ("local_featured".equals(pl.id)) {
                    songs = featuredSongs;
                } else if ("local_car".equals(pl.id)) {
                    songs = carSongs;
                } else {
                    songs = fetchSongsForSubsonicPlaylistSync(pl.id);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (songs != null && !songs.isEmpty()) {
                            ArrayList<MusicService.SongItem> q = MusicService.getPlaylist();
                            for (DisplayEntry s : songs) {
                                String pUrl;
                                if (s.localPath != null && s.localPath.length() > 0) {
                                    pUrl = "file://" + s.localPath;
                                } else if (s.id != null && s.id.startsWith("local_file:")) {
                                    pUrl = "file://" + s.id.substring(11);
                                } else {
                                    pUrl = buildStreamUrl(s.id, getSavedBitrate());
                                }
                                q.add(new MusicService.SongItem(s.id, s.title, s.artist, pUrl, s.coverArt, s.quality));
                            }
                            refreshQueueList();
                            scrollToCurrentPlayingInQueue();
                            Toast.makeText(MainActivity.this, "已添加 " + songs.size() + " 首歌到播放队列", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    private ArrayList<DisplayEntry> fetchSongsForSubsonicPlaylistSync(String plId) {
        ArrayList<DisplayEntry> resList = new ArrayList<DisplayEntry>();
        ArrayList<Map<String, String>> dummy = new ArrayList<Map<String, String>>();
        try {
            boolean isAlbum = plId.startsWith("album_");
            String actualId = isAlbum ? plId.substring(6) : plId;
            String endpoint = isAlbum ? ("getAlbum.view?id=" + URLEncoder.encode(actualId, "UTF-8"))
                    : ("getPlaylist.view?id=" + URLEncoder.encode(actualId, "UTF-8"));
            String res = requestApi(endpoint + "&" + getAuthParams());
            if (res != null) {
                JSONObject root = new JSONObject(res).getJSONObject("subsonic-response");
                JSONObject pl = isAlbum ? root.optJSONObject("album") : root.optJSONObject("playlist");
                if (pl != null) {
                    String songKey = isAlbum ? "song" : "entry";
                    if (pl.has(songKey)) {
                        Object obj = pl.get(songKey);
                        if (obj instanceof JSONArray) {
                            JSONArray arr = (JSONArray) obj;
                            for (int i = 0; i < arr.length(); i++) addSongRow(arr.getJSONObject(i), resList, dummy);
                        } else if (obj instanceof JSONObject) {
                            addSongRow((JSONObject) obj, resList, dummy);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return resList;
    }

    private void promptCreatePlaylist() {
        final EditText input = new EditText(this);
        input.setHint("输入歌单名称...");
        input.setTextColor(0xFFFFFFFF);
        new AlertDialog.Builder(this)
                .setTitle("新建歌单")
                .setView(input)
                .setPositiveButton("创建", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (name.length() > 0) createNewServerPlaylist(name);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void createNewServerPlaylist(final String playlistName) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String param = "name=" + URLEncoder.encode(playlistName, "UTF-8");
                    requestApi("createPlaylist.view?" + param + "&" + getAuthParams());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "歌单创建成功", Toast.LENGTH_SHORT).show();
                            syncFavoritesAndPlaylists(false);
                        }
                    });
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void promptRenamePlaylist(final DisplayEntry playlistEntry) {
        final EditText input = new EditText(this);
        input.setText(playlistEntry.title);
        input.setTextColor(0xFFFFFFFF);
        new AlertDialog.Builder(this)
                .setTitle("重命名歌单")
                .setView(input)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String newName = input.getText().toString().trim();
                        if (newName.length() > 0) renameServerPlaylist(playlistEntry.id, newName);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void renameServerPlaylist(final String playlistId, final String newName) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String param = "playlistId=" + URLEncoder.encode(playlistId, "UTF-8") + "&name=" + URLEncoder.encode(newName, "UTF-8");
                    requestApi("updatePlaylist.view?" + param + "&" + getAuthParams());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() { syncFavoritesAndPlaylists(false); }
                    });
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void promptDeletePlaylist(final DisplayEntry playlistEntry) {
        new AlertDialog.Builder(this)
                .setTitle("确认删除歌单")
                .setMessage("是否确认删除歌单 \"" + playlistEntry.title + "\"？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteServerPlaylist(playlistEntry.id);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteServerPlaylist(final String playlistId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String param = "id=" + URLEncoder.encode(playlistId, "UTF-8");
                    requestApi("deletePlaylist.view?" + param + "&" + getAuthParams());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() { syncFavoritesAndPlaylists(false); }
                    });
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void showAddToPlaylistDialog(final DisplayEntry entry) {
        final ArrayList<String> names = new ArrayList<String>();
        final ArrayList<String> ids = new ArrayList<String>();
        names.add("★ 我喜欢的音乐"); ids.add("ACTION_FAV");
        names.add("📁 精选本地歌单"); ids.add("LOCAL_FEATURED");
        names.add("🚗 车载音乐歌单"); ids.add("LOCAL_CAR");
        for (DisplayEntry pl : rawServerUserPlaylists) {
            names.add("☁ " + pl.title); ids.add(pl.id);
        }
        new AlertDialog.Builder(this)
                .setTitle("选择要加入的歌单")
                .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String targetId = ids.get(which);
                        if ("ACTION_FAV".equals(targetId)) {
                            serverStarSong(entry.id, true);
                        } else if ("LOCAL_FEATURED".equals(targetId)) {
                            featuredSongs.add(entry); saveLocalPlaylists();
                            Toast.makeText(MainActivity.this, "已加入精选本地歌单", Toast.LENGTH_SHORT).show();
                        } else if ("LOCAL_CAR".equals(targetId)) {
                            carSongs.add(entry); saveLocalPlaylists();
                            Toast.makeText(MainActivity.this, "已加入车载歌单", Toast.LENGTH_SHORT).show();
                        } else {
                            addSongToServerPlaylist(targetId, entry.id, names.get(which));
                        }
                    }
                })
                .show();
    }

    private void addSongToServerPlaylist(final String playlistId, final String songId, final String plName) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String param = "playlistId=" + URLEncoder.encode(playlistId, "UTF-8") + "&songIdToAdd=" + URLEncoder.encode(songId, "UTF-8");
                    requestApi("updatePlaylist.view?" + param + "&" + getAuthParams());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "已添加到 " + plName, Toast.LENGTH_SHORT).show();
                            syncFavoritesAndPlaylists(false);
                        }
                    });
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void removeFromCurrentView(int position, DisplayEntry entry) {
        if (currentPage == PAGE_FAV && currentActiveFavPlaylistId != null) {
            if ("fav_entry".equals(currentActiveFavPlaylistId)) {
                serverStarSong(entry.id, false);
            }
            if (position >= 0 && position < favPlaylistsList.size()) {
                favPlaylistsList.remove(position);
                favPlaylistsData.remove(position);
                favPlaylistsAdapter.notifyDataSetChanged();
                Toast.makeText(this, "已从列表中移除", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void downloadSongItem(final DisplayEntry entry) {
        String customPath = prefs.getString("download_path", getDefaultDownloadPath());
        final File saveDir = new File(customPath);
        if (!saveDir.exists()) saveDir.mkdirs();
        final String fileName = sanitizeFileName(entry.title + " - " + entry.artist) + ".mp3";
        final File targetFile = new File(saveDir, fileName);
        Toast.makeText(this, "开始下载: " + entry.title, Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (CacheManager.isSongCached(MainActivity.this, entry.id)) {
                    File cachedFile = CacheManager.getSongFile(MainActivity.this, entry.id);
                    if (copyFile(cachedFile, targetFile)) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                Toast.makeText(MainActivity.this, "下载完成: " + targetFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                            }
                        });
                        return;
                    }
                }
                try {
                    String initialUrl;
                    if (entry.localPath != null && entry.localPath.length() > 0) {
                        initialUrl = "file://" + entry.localPath;
                    } else if (entry.id != null && entry.id.startsWith("local_file:")) {
                        initialUrl = "file://" + entry.id.substring(11);
                    } else {
                        initialUrl = buildStreamUrl(entry.id);
                    }
                    boolean ok = downloadWithRedirects(initialUrl, targetFile, 0);
                    if (ok && CacheManager.isValidAudioFile(targetFile)) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                Toast.makeText(MainActivity.this, "下载完成:\n" + targetFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private boolean copyFile(File src, File dest) {
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            fis = new FileInputStream(src);
            fos = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int len;
            while ((len = fis.read(buf)) != -1) fos.write(buf, 0, len);
            fos.flush();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try { if (fos != null) fos.close(); } catch (Exception ignored) {}
            try { if (fis != null) fis.close(); } catch (Exception ignored) {}
        }
    }

    private boolean downloadWithRedirects(String targetUrl, File destFile, int depth) throws Exception {
        if (depth > 6) return false;
        URL url = new URL(targetUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(20000);
        if (conn instanceof HttpsURLConnection) {
            ((HttpsURLConnection) conn).setSSLSocketFactory(new TLSSocketFactory());
        }
        int code = conn.getResponseCode();
        if (code == 301 || code == 302 || code == 303 || code == 307) {
            String location = conn.getHeaderField("Location");
            conn.disconnect();
            if (location != null && location.length() > 0) {
                return downloadWithRedirects(new URL(url, location).toString(), destFile, depth + 1);
            }
            return false;
        }
        if (code == 200 || code == 206) {
            InputStream is = conn.getInputStream();
            FileOutputStream fos = new FileOutputStream(destFile);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
            fos.flush(); fos.close(); is.close();
            conn.disconnect();
            return true;
        }
        conn.disconnect();
        return false;
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private void refreshQueueList() {
        ArrayList<MusicService.SongItem> list = MusicService.getPlaylist();
        int currentPlaying = MusicService.getCurrentIndex();
        queueData.clear();
        for (int i = 0; i < list.size(); i++) {
            MusicService.SongItem item = list.get(i);
            Map<String, String> row = new HashMap<String, String>();
            row.put("title", (i == currentPlaying ? "▶ " : "   ") + (i + 1) + ". " + item.title);
            row.put("subtitle", item.artist);
            queueData.add(row);
        }
        queueAdapter.notifyDataSetChanged();
        detailQueueAdapter.notifyDataSetChanged();
    }

    private void scrollToCurrentPlayingInQueue() {
        final int currentPlaying = MusicService.getCurrentIndex();
        if (currentPlaying >= 0 && currentPlaying < queueData.size()) {
            lvQueue.post(new Runnable() {
                @Override
                public void run() {
                    lvQueue.setSelection(currentPlaying);
                }
            });
            lvDetailQueue.post(new Runnable() {
                @Override
                public void run() {
                    lvDetailQueue.setSelection(currentPlaying);
                }
            });
        }
    }

    private void updateFavButtonState(String currentPlayingSongId) {
        String targetId = currentPlayingSongId;
        if (targetId == null) {
            ArrayList<MusicService.SongItem> q = MusicService.getPlaylist();
            int idx = MusicService.getCurrentIndex();
            if (q != null && idx >= 0 && idx < q.size()) targetId = q.get(idx).id;
        }
        boolean fav = isFav(targetId);
        String symbol = fav ? "♥" : "♡";
        if (btnBottomFav != null) btnBottomFav.setText(symbol);
        if (btnDetailFav != null) btnDetailFav.setText(symbol);
    }

    // 收藏操作 0ms 本地即时响应，后台静默通知服务端
    private void serverStarSong(final String songId, final boolean toStar) {
        if (songId == null) return;
        if (toStar) {
            favSongIds.add(songId);
            Toast.makeText(this, "已添加到「我喜欢的音乐」", Toast.LENGTH_SHORT).show();
        } else {
            favSongIds.remove(songId);
            Toast.makeText(this, "已从「我喜欢的音乐」中移除", Toast.LENGTH_SHORT).show();
        }
        saveFavSet();
        updateFavButtonState(songId);

        // 如果当前正在“我的收藏与歌单”总览页，即刻本地更新数量
        if (currentPage == PAGE_FAV && currentActiveFavPlaylistId == null && !favPlaylistsList.isEmpty()) {
            favPlaylistsList.get(0).subtitle = "共 " + favSongIds.size() + " 首";
            if (!favPlaylistsData.isEmpty()) {
                favPlaylistsData.get(0).put("subtitle", "共 " + favSongIds.size() + " 首");
            }
            favPlaylistsAdapter.notifyDataSetChanged();
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String endpoint = toStar ? "star.view" : "unstar.view";
                    requestApi(endpoint + "?id=" + URLEncoder.encode(songId, "UTF-8") + "&" + getAuthParams());
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private boolean isFav(String songId) { return songId != null && favSongIds.contains(songId); }
    private void loadFavSet() { favSongIds = new HashSet<String>(prefs.getStringSet("fav_songs_set", new HashSet<String>())); }
    private void saveFavSet() { prefs.edit().putStringSet("fav_songs_set", favSongIds).apply(); }

    private void loadLocalPlaylists() {
        featuredSongs.clear();
        carSongs.clear();
        try {
            String fStr = prefs.getString("local_playlist_featured", "[]");
            JSONArray fArr = new JSONArray(fStr);
            for (int i = 0; i < fArr.length(); i++) {
                JSONObject o = fArr.getJSONObject(i);
                featuredSongs.add(new DisplayEntry(
                        o.optString("id"),
                        o.optString("title"),
                        o.optString("artist"),
                        o.optString("subtitle"),
                        o.optString("coverArt"),
                        o.optString("quality"),
                        o.optBoolean("isSong", true),
                        o.optInt("bitRateNumeric", 0),
                        o.optString("localPath", null)
                ));
            }

            String cStr = prefs.getString("local_playlist_car", "[]");
            JSONArray cArr = new JSONArray(cStr);
            for (int i = 0; i < cArr.length(); i++) {
                JSONObject o = cArr.getJSONObject(i);
                carSongs.add(new DisplayEntry(
                        o.optString("id"),
                        o.optString("title"),
                        o.optString("artist"),
                        o.optString("subtitle"),
                        o.optString("coverArt"),
                        o.optString("quality"),
                        o.optBoolean("isSong", true),
                        o.optInt("bitRateNumeric", 0),
                        o.optString("localPath", null)
                ));
            }
        } catch (Exception ignored) {}
    }

    private void saveLocalPlaylists() {
        try {
            JSONArray fArr = new JSONArray();
            for (DisplayEntry e : featuredSongs) {
                JSONObject o = new JSONObject();
                o.put("id", e.id);
                o.put("title", e.title);
                o.put("artist", e.artist);
                o.put("subtitle", e.subtitle);
                o.put("coverArt", e.coverArt);
                o.put("quality", e.quality);
                o.put("isSong", e.isSong);
                o.put("bitRateNumeric", e.bitRateNumeric);
                o.put("localPath", e.localPath);
                fArr.put(o);
            }
            prefs.edit().putString("local_playlist_featured", fArr.toString()).apply();

            JSONArray cArr = new JSONArray();
            for (DisplayEntry e : carSongs) {
                JSONObject o = new JSONObject();
                o.put("id", e.id);
                o.put("title", e.title);
                o.put("artist", e.artist);
                o.put("subtitle", e.subtitle);
                o.put("coverArt", e.coverArt);
                o.put("quality", e.quality);
                o.put("isSong", e.isSong);
                o.put("bitRateNumeric", e.bitRateNumeric);
                o.put("localPath", e.localPath);
                cArr.put(o);
            }
            prefs.edit().putString("local_playlist_car", cArr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void setupVinylAnimation() {
        if (flVinylDisc != null) {
            flVinylDisc.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        vinylRotateAnim = new RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        vinylRotateAnim.setDuration(20000);
        vinylRotateAnim.setRepeatCount(Animation.INFINITE);
        vinylRotateAnim.setInterpolator(new LinearInterpolator());
    }

    private void updateVinylAnimationState() {
        if (flVinylDisc == null) return;
        if (isCurrentSongPlaying) {
            if (flVinylDisc.getAnimation() == null) {
                flVinylDisc.startAnimation(vinylRotateAnim);
            }
            if (viewTonearm != null) viewTonearm.setPlaying(true);
        } else {
            flVinylDisc.clearAnimation();
            if (viewTonearm != null) viewTonearm.setPlaying(false);
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

    private void toggleDetailQueueView() {
        if (layoutDetailQueueView.getVisibility() == View.VISIBLE) {
            layoutDetailQueueView.setVisibility(View.GONE);
            layoutDetailLyricsView.setVisibility(View.VISIBLE);
        } else {
            layoutDetailLyricsView.setVisibility(View.GONE);
            layoutDetailQueueView.setVisibility(View.VISIBLE);
            refreshQueueList();
            scrollToCurrentPlayingInQueue();
        }
    }

    private void updatePlayPauseIcons(boolean isPlaying) {
        int resId = isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        if (btnPlayPause != null) btnPlayPause.setImageResource(resId);
        if (btnDetailPlayPause != null) btnDetailPlayPause.setImageResource(resId);
    }

    private void updateModeIcons(int mode) {
        int iconRes;
        if (mode == MusicService.MODE_SHUFFLE) {
            iconRes = android.R.drawable.ic_menu_directions;
        } else if (mode == MusicService.MODE_SINGLE) {
            iconRes = android.R.drawable.ic_menu_revert;
        } else {
            iconRes = android.R.drawable.ic_menu_rotate;
        }
        if (btnMode != null) btnMode.setImageResource(iconRes);
        if (btnDetailMode != null) btnDetailMode.setImageResource(iconRes);
    }

    private void setupControlIcons() {
        if (btnPrev != null) btnPrev.setImageResource(android.R.drawable.ic_media_previous);
        if (btnNext != null) btnNext.setImageResource(android.R.drawable.ic_media_next);
        if (btnDetailPrev != null) btnDetailPrev.setImageResource(android.R.drawable.ic_media_previous);
        if (btnDetailNext != null) btnDetailNext.setImageResource(android.R.drawable.ic_media_next);
        if (btnOpenEq != null) btnOpenEq.setImageResource(android.R.drawable.ic_menu_preferences);
        if (btnDetailEq != null) btnDetailEq.setImageResource(android.R.drawable.ic_menu_preferences);
        if (btnExitApp != null) btnExitApp.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        if (btnDetailExitApp != null) btnDetailExitApp.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
    }

    private void loadSavedConfig() {
        etServer.setText(prefs.getString("server", "http://192.168.1.100:4533"));
        etUsername.setText(prefs.getString("user", "admin"));
        etPassword.setText(prefs.getString("password", "admin"));
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
        String path = etDownloadPath.getText().toString().trim();
        String cacheMb = etCacheSize.getText().toString().trim();

        if (server.endsWith("/")) server = server.substring(0, server.length() - 1);

        prefs.edit()
                .putString("server", server)
                .putString("user", user)
                .putString("password", pass)
                .putString("timeout_sec", timeout.isEmpty() ? "30" : timeout)
                .putString("retry_count", retry.isEmpty() ? "3" : retry)
                .putString("download_path", path.isEmpty() ? getDefaultDownloadPath() : path)
                .putString("cache_size_mb", cacheMb.isEmpty() ? "500" : cacheMb)
                .apply();

        Toast.makeText(this, "正在测试服务端连接...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String res = requestApi("ping.view?" + getAuthParams());
                final boolean success = res != null && res.contains("\"ok\"");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (success) {
                            Toast.makeText(MainActivity.this, "连接成功！配置已保存", Toast.LENGTH_SHORT).show();
                            syncFavoritesAndPlaylists(false);
                        } else {
                            Toast.makeText(MainActivity.this, "连接失败，请检查地址或账号密码", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    private String getDefaultDownloadPath() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath();
    }

    private void updateCacheSizeDisplay() {
        long usedBytes = CacheManager.getUsedCacheBytes(this);
        double mb = usedBytes / (1024.0 * 1024.0);
        if (tvCacheUsed != null) {
            tvCacheUsed.setText(String.format("(已用 %.1f MB)", mb));
        }
    }

    private String getSavedBitrate() {
        return prefs.getString("default_bitrate", "auto");
    }

    private int getBitrateIndex(String bitrate) {
        for (int i = 0; i < BITRATE_VALUES.length; i++) {
            if (BITRATE_VALUES[i].equalsIgnoreCase(bitrate)) return i;
        }
        return 0;
    }

    private int getPlazaBitrateIndex(String bitrate) {
        for (int i = 0; i < PLAZA_BITRATE_VALUES.length; i++) {
            if (PLAZA_BITRATE_VALUES[i].equalsIgnoreCase(bitrate)) return i;
        }
        return 0;
    }

    private String getBitrateDisplay(String bitrate, String fallbackQuality) {
        if ("auto".equalsIgnoreCase(bitrate)) return fallbackQuality != null ? fallbackQuality : "自动";
        return bitrate.toUpperCase() + (bitrate.matches("\\d+") ? "K" : "");
    }

    private String buildStreamUrl(String songId) {
        return buildStreamUrl(songId, getSavedBitrate());
    }

    private String buildStreamUrl(String songId, String bitrate) {
        String base = prefs.getString("server", "");
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String formatParam = "auto".equalsIgnoreCase(bitrate) ? "" : ("&maxBitRate=" + bitrate);
        return base + "/rest/stream.view?id=" + songId + formatParam + "&" + getAuthParams();
    }

    private String getAuthParams() {
        String user = prefs.getString("user", "admin");
        String pass = prefs.getString("password", "admin");
        return "u=" + URLEncoder.encode(user) + "&p=" + URLEncoder.encode(pass) + "&v=1.16.1&c=RetroSubsonic&f=json";
    }

    private String requestApi(String endpointAndParams) {
        String base = prefs.getString("server", "");
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String fullUrl = base + "/rest/" + endpointAndParams;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(fullUrl);
            conn = (HttpURLConnection) url.openConnection();
            int timeoutSec = 30;
            try {
                timeoutSec = Integer.parseInt(prefs.getString("timeout_sec", "30"));
            } catch (Exception ignored) {}
            int timeoutMs = Math.max(5, timeoutSec) * 1000;
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            if (conn instanceof HttpsURLConnection) {
                ((HttpsURLConnection) conn).setSSLSocketFactory(new TLSSocketFactory());
            }
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
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

    private void addSongRow(JSONObject s, ArrayList<DisplayEntry> list, ArrayList<Map<String, String>> data) {
        try {
            String id = s.getString("id");
            String title = s.getString("title");
            String artist = s.optString("artist", "未知歌手");
            String coverArt = s.optString("coverArt", null);
            int bitRate = s.optInt("bitRate", 128);
            String quality = bitRate > 0 ? (bitRate + "K") : "MP3";
            DisplayEntry entry = new DisplayEntry(id, title, artist, artist + " [" + quality + "]", coverArt, quality, true, bitRate);
            list.add(entry);
            Map<String, String> row = new HashMap<String, String>();
            row.put("title", title);
            row.put("subtitle", artist + " [" + quality + "]");
            data.add(row);
        } catch (Exception ignored) {}
    }

    private void addArtistRow(JSONObject a, ArrayList<DisplayEntry> list, ArrayList<Map<String, String>> data) {
        try {
            String id = a.getString("id");
            String name = a.getString("name");
            DisplayEntry entry = new DisplayEntry("artist_" + id, name, "歌手", "点击查看专辑", null, "歌手", false);
            list.add(entry);
            Map<String, String> row = new HashMap<String, String>();
            row.put("title", "👤 " + name);
            row.put("subtitle", "歌手 (点击查看包含专辑)");
            data.add(row);
        } catch (Exception ignored) {}
    }

    private void addAlbumRow(JSONObject a, ArrayList<DisplayEntry> list, ArrayList<Map<String, String>> data) {
        try {
            String id = a.getString("id");
            String title = a.getString("title");
            String artist = a.optString("artist", "");
            DisplayEntry entry = new DisplayEntry("album_" + id, title, artist, "专辑", a.optString("coverArt", null), "专辑", false);
            list.add(entry);
            Map<String, String> row = new HashMap<String, String>();
            row.put("title", "💿 " + title);
            row.put("subtitle", artist.length() > 0 ? (artist + " · 专辑") : "专辑");
            data.add(row);
        } catch (Exception ignored) {}
    }

    private long parseTime(String timePart) {
        try {
            String[] parts = timePart.split(":");
            if (parts.length >= 2) {
                int min = Integer.parseInt(parts[0]);
                float sec = Float.parseFloat(parts[1]);
                return (long) (min * 60000 + sec * 1000);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private String formatTime(int ms) {
        int totalSec = ms / 1000;
        int min = totalSec / 60;
        int sec = totalSec % 60;
        return String.format("%02d:%02d", min, sec);
    }

    private void performAppExit() {
        new AlertDialog.Builder(this)
                .setTitle("退出应用")
                .setMessage("确定要退出并停止所有后台播放服务吗？")
                .setPositiveButton("退出", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        stopService(new Intent(MainActivity.this, MusicService.class));
                        finish();
                        System.exit(0);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(MusicService.BROADCAST_STATUS);
        registerReceiver(statusReceiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        try { unregisterReceiver(statusReceiver); } catch (Exception ignored) {}
    }

    // 关键修复点 1：物理返回键严格按照栈层级回退，杜绝误退桌面
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (layoutDetailOverlay.getVisibility() == View.VISIBLE) {
                layoutDetailOverlay.setVisibility(View.GONE);
                return true;
            }
            if (layoutQueuePanel.getVisibility() == View.VISIBLE) {
                layoutQueuePanel.setVisibility(View.GONE);
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
            if (currentPage == PAGE_PLAZA && isBrowsingPlazaSongs) {
                exitPlazaSongsToPlaylists();
                return true;
            }
            moveTaskToBack(true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // =========================================================================
    // 磁盘持久缓存与下采样解码模块
    // =========================================================================
    private static File getCoverDiskCacheDir(Context context) {
        File baseDir = context.getExternalCacheDir();
        if (baseDir == null || !baseDir.exists()) baseDir = context.getCacheDir();
        File coverDir = new File(baseDir, "covers");
        if (!coverDir.exists()) coverDir.mkdirs();
        return coverDir;
    }

    private static String hashKeyForDisk(String key) {
        if (key == null) return "unknown";
        try {
            MessageDigest m = MessageDigest.getInstance("MD5");
            m.update(key.getBytes("UTF-8"));
            byte[] s = m.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : s) sb.append(Integer.toHexString((b & 0xFF) | 0x100).substring(1, 3));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(key.hashCode());
        }
    }

    private static Bitmap decodeSampledBitmapFromFile(File file, int reqWidth, int reqHeight) {
        if (file == null || !file.exists()) return null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inPurgeable = true;
            options.inInputShareable = true;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return Math.max(1, inSampleSize);
    }

    private static boolean downloadUrlToFile(String urlString, File destFile) {
        File tmp = new File(destFile.getAbsolutePath() + ".tmp");
        HttpURLConnection conn = null;
        InputStream is = null;
        FileOutputStream fos = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            if (conn instanceof HttpsURLConnection) {
                ((HttpsURLConnection) conn).setSSLSocketFactory(new TLSSocketFactory());
            }
            if (conn.getResponseCode() == 200) {
                is = conn.getInputStream();
                fos = new FileOutputStream(tmp);
                byte[] buf = new byte[4096];
                int len;
                while ((len = is.read(buf)) != -1) {
                    fos.write(buf, 0, len);
                }
                fos.flush();
                fos.close(); fos = null;
                is.close(); is = null;
                if (destFile.exists()) destFile.delete();
                return tmp.renameTo(destFile);
            }
        } catch (Throwable t) {
            if (tmp.exists()) tmp.delete();
        } finally {
            try { if (fos != null) fos.close(); } catch (Exception ignored) {}
            try { if (is != null) is.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
        return false;
    }

    private void clearDiskCovers() {
        File dir = getCoverDiskCacheDir(this);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) f.delete();
        }
        if (imageMemoryCache != null) imageMemoryCache.evictAll();
    }

    private class PlazaGridAdapter extends BaseAdapter {
        @Override public int getCount() { return plazaPlaylistsList.size(); }
        @Override public Object getItem(int pos) { return plazaPlaylistsList.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_plaza_grid, parent, false);
            }
            DisplayEntry item = plazaPlaylistsList.get(pos);
            final ImageView ivCover = (ImageView) view.findViewById(R.id.iv_grid_cover);
            TextView tvTitle = (TextView) view.findViewById(R.id.tv_grid_title);
            TextView tvAuthor = (TextView) view.findViewById(R.id.tv_grid_author);
            TextView tvPlayCount = (TextView) view.findViewById(R.id.tv_grid_playcount);

            tvTitle.setText(item.title);
            tvAuthor.setText(item.artist);
            tvPlayCount.setText(item.subtitle);

            ivCover.setImageResource(R.drawable.ic_launcher);
            if (item.coverArt != null && item.coverArt.length() > 0) {
                final String coverUrl = item.coverArt;
                ivCover.setTag(coverUrl);

                Bitmap cached = imageMemoryCache != null ? imageMemoryCache.get(coverUrl) : null;
                if (cached != null) {
                    ivCover.setImageBitmap(cached);
                } else if (!isGridFlinging) {
                    imageLoadExecutor.execute(new Runnable() {
                        @Override public void run() {
                            File diskFile = new File(getCoverDiskCacheDir(MainActivity.this), hashKeyForDisk(coverUrl));
                            Bitmap bmp = null;
                            if (diskFile.exists() && diskFile.length() > 0) {
                                bmp = decodeSampledBitmapFromFile(diskFile, 200, 200);
                            }
                            if (bmp == null) {
                                boolean ok = downloadUrlToFile(coverUrl, diskFile);
                                if (ok) {
                                    bmp = decodeSampledBitmapFromFile(diskFile, 200, 200);
                                }
                            }
                            if (bmp != null) {
                                if (imageMemoryCache != null) imageMemoryCache.put(coverUrl, bmp);
                                final Bitmap finalBmp = bmp;
                                runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        if (coverUrl.equals(ivCover.getTag())) {
                                            ivCover.setImageBitmap(finalBmp);
                                        }
                                    }
                                });
                            }
                        }
                    });
                }
            } else {
                ivCover.setTag(null);
            }
            return view;
        }
    }

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
            tv.setPadding(10, 4, 10, 4);
            tv.setText(items[position] + " ▾");
            return tv;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            TextView tv = (convertView instanceof TextView) ? (TextView) convertView : new TextView(MainActivity.this);
            tv.setTextSize(13);
            tv.setTextColor(0xFFE0E0E0);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setPadding(24, 18, 24, 18);
            tv.setBackgroundColor(0xFF1E222B);
            tv.setText(items[position]);
            return tv;
        }
    }

    private class BitrateSpinnerAdapter extends SimpleDarkAdapter {
        BitrateSpinnerAdapter(String[] items) { super(items); }
    }
}
