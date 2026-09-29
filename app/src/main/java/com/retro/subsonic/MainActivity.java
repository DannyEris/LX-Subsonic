package com.retro.subsonic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.*;
import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends Activity {
    // 页面枚举
    private static final int PAGE_SEARCH = 0;
    private static final int PAGE_PLAZA = 1;
    private static final int PAGE_RANKING = 2;
    private static final int PAGE_FAV = 3;
    private static final int PAGE_LOCAL = 4;
    private static final int PAGE_SETTINGS = 5;
    private int currentPage = PAGE_SEARCH;

    // 音质常数
    private static final String[] BITRATE_LABELS = new String[]{"自动", "128K", "192K", "320K", "FLAC"};
    private static final String[] BITRATE_VALUES = new String[]{"auto", "128", "192", "320", "flac"};
    private static final String[] SEARCH_TYPES = new String[]{"歌曲", "歌手", "专辑"};

    // 导航栏按钮
    private Button btnNavSearch, btnNavPlaza, btnNavRanking, btnNavFav, btnNavLocal, btnNavSettings;
    private LinearLayout layoutPageSearch, layoutPagePlaza, layoutPageRanking, layoutPageFav, layoutPageLocal;
    private ScrollView layoutPageSettings;

    // 搜索页控件
    private Spinner spinnerSearchPlatform, spinnerSearchType;
    private EditText etSearchKeyword;
    private Button btnSearchSubmit;
    private LinearLayout layoutHotSearchBox, layoutHotSearchTags, layoutSearchResultBox;
    private TextView tvSearchResultTitle;
    private CheckBox cbDedupSongs;
    private ListView lvSearchResults;
    private ArrayList<DisplayEntry> searchResultsList = new ArrayList<DisplayEntry>();
    private ArrayList<Map<String, String>> searchResultsData = new ArrayList<Map<String, String>>();
    private SimpleAdapter searchResultsAdapter;
    private ArrayList<DisplayEntry> rawSearchSongResults = new ArrayList<DisplayEntry>();

    // 歌单广场页控件
    private Spinner spinnerPlazaPlatform;
    private Button btnPlazaCategory, btnPlazaImport, btnPlazaSearchSubmit, btnPlazaBack;
    private EditText etPlazaSearch;
    private TextView tvPlazaCurrentTag;
    private ListView lvPlazaPlaylists;
    private ArrayList<DisplayEntry> plazaPlaylistsList = new ArrayList<DisplayEntry>();
    private ArrayList<Map<String, String>> plazaPlaylistsData = new ArrayList<Map<String, String>>();
    private SimpleAdapter plazaPlaylistsAdapter;
    private String currentPlazaTag = "";

    // 排行榜页控件
    private Spinner spinnerRankingPlatform;
    private TextView tvRankingBoardTitle;
    private ListView lvRankingBoards, lvRankingSongs;
    private ArrayList<DisplayEntry> rankingBoardsList = new ArrayList<DisplayEntry>();
    private ArrayList<Map<String, String>> rankingBoardsData = new ArrayList<Map<String, String>>();
    private SimpleAdapter rankingBoardsAdapter;
    private ArrayList<DisplayEntry> rankingSongsList = new ArrayList<DisplayEntry>();
    private ArrayList<Map<String, String>> rankingSongsData = new ArrayList<Map<String, String>>();
    private SimpleAdapter rankingSongsAdapter;

    // 我的收藏页控件
    private TextView tvFavTitle;
    private Button btnCreatePlaylist, btnFavBack;
    private ListView lvFavPlaylists;
    private ArrayList<DisplayEntry> favPlaylistsList = new ArrayList<DisplayEntry>();
    private ArrayList<Map<String, String>> favPlaylistsData = new ArrayList<Map<String, String>>();
    private SimpleAdapter favPlaylistsAdapter;
    private ArrayList<DisplayEntry> rawServerUserPlaylists = new ArrayList<DisplayEntry>();
    private String currentActiveFavPlaylistId = null;

    // 本地音乐页控件
    private TextView tvLocalPathStatus;
    private Button btnScanLocalMusic;
    private ListView lvLocalMusic;
    private ArrayList<DisplayEntry> localMusicList = new ArrayList<DisplayEntry>();
    private ArrayList<Map<String, String>> localMusicData = new ArrayList<Map<String, String>>();
    private SimpleAdapter localMusicAdapter;

    // 设置页控件
    private EditText etServer, etUsername, etPassword, etTimeoutSec, etRetryCount, etDownloadPath, etCacheSize;
    private Spinner spinnerConfigBitrate;
    private Button btnClearCache, btnSaveSettings;
    private TextView tvCacheUsed;

    // 底部播放控制器 & 详情页 Overlay (原版核心组件保留)
    private ImageView btnExitApp, btnDetailExitApp;
    private ImageView ivBottomCover, btnMode, btnDetailMode, btnPrev, btnDetailPrev, btnPlayPause, btnDetailPlayPause, btnNext, btnDetailNext, btnOpenEq, btnDetailEq;
    private TextView tvCurrentSong, tvTime, tvDetailTitle, tvDetailArtist, tvDetailQuality, tvDetailBuffer, tvDetailTime, tvLyricOffsetStatus;
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
    private String lastLoadedSongId = "";
    private int lastValidProgressMs = 0;
    private RotateAnimation vinylRotateAnim;
    private Bitmap currentRawCoverBitmap, currentCircularCoverBitmap, currentBottomCoverBitmap;
    private Handler lyricHandler = new Handler();

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

        // 7 参数构造器
        public DisplayEntry(String id, String title, String artist, String subtitle, String coverArt, String quality, boolean isSong) {
            this(id, title, artist, subtitle, coverArt, quality, isSong, 0, null);
        }

        // 8 参数构造器（兼容 LxApiHelper.java 调用）
        public DisplayEntry(String id, String title, String artist, String subtitle, String coverArt, String quality, boolean isSong, int bitRateNumeric) {
            this(id, title, artist, subtitle, coverArt, quality, isSong, bitRateNumeric, null);
        }

        // 9 参数构造器（支持本地文件路径）
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
                int position = intent.getIntExtra("position", 0);
                int duration = intent.getIntExtra("duration", 0);

                if (title != null) {
                    tvCurrentSong.setText(title + " - " + artist);
                    tvDetailTitle.setText(title);
                    tvDetailArtist.setText(artist);
                    tvDetailQuality.setText(getBitrateDisplay(getSavedBitrate(), quality));

                    if (songId != null && !songId.equals(lastLoadedSongId)) {
                        lastLoadedSongId = songId;
                        lastValidProgressMs = 0;
                        seekBar.setProgress(0);
                        detailSeekBar.setProgress(0);
                        tvTime.setText("00:00 / 00:00");
                        tvDetailTime.setText("00:00 / 00:00");
                        loadCoverArt(coverArtId != null ? coverArtId : songId);
                        loadLyrics(songId, artist, title);
                        refreshQueueList();
                        updateCacheSizeDisplay();
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
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("subsonic_cfg", MODE_PRIVATE);
        lyricBaseFontSize = prefs.getInt("lyric_font_size", 15);
        isVinylDisplayMode = prefs.getBoolean("is_vinyl_display_mode", true);

        loadFavSet();
        loadLocalPlaylists();
        initViews();
        setupControlIcons();
        setupSpinners();
        setupVinylAnimation();
        updateCoverDisplayMode();
        loadSavedConfig();
        setupNavigation();
        setupListeners();
        updateCacheSizeDisplay();

        // 默认显示搜索主页，并拉取首选平台热搜词
        switchPage(PAGE_SEARCH);
        fetchHotSearchForCurrentPlatform();
        fetchServerFavoritesQuietly();
    }

    private void initViews() {
        // 导航按钮
        btnNavSearch = (Button) findViewById(R.id.btn_nav_search);
        btnNavPlaza = (Button) findViewById(R.id.btn_nav_plaza);
        btnNavRanking = (Button) findViewById(R.id.btn_nav_ranking);
        btnNavFav = (Button) findViewById(R.id.btn_nav_fav);
        btnNavLocal = (Button) findViewById(R.id.btn_nav_local);
        btnNavSettings = (Button) findViewById(R.id.btn_nav_settings);
        btnExitApp = (ImageView) findViewById(R.id.btn_exit_app);

        // 页面容器
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
        layoutHotSearchBox = (LinearLayout) findViewById(R.id.layout_hot_search_box);
        layoutHotSearchTags = (LinearLayout) findViewById(R.id.layout_hot_search_tags);
        layoutSearchResultBox = (LinearLayout) findViewById(R.id.layout_search_result_box);
        tvSearchResultTitle = (TextView) findViewById(R.id.tv_search_result_title);
        cbDedupSongs = (CheckBox) findViewById(R.id.cb_dedup_songs);
        lvSearchResults = (ListView) findViewById(R.id.lv_search_results);

        searchResultsAdapter = new SimpleAdapter(this, searchResultsData, android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvSearchResults.setAdapter(searchResultsAdapter);

        // 歌单广场页
        spinnerPlazaPlatform = (Spinner) findViewById(R.id.spinner_plaza_platform);
        btnPlazaCategory = (Button) findViewById(R.id.btn_plaza_category);
        btnPlazaImport = (Button) findViewById(R.id.btn_plaza_import);
        btnPlazaSearchSubmit = (Button) findViewById(R.id.btn_plaza_search_submit);
        btnPlazaBack = (Button) findViewById(R.id.btn_plaza_back);
        etPlazaSearch = (EditText) findViewById(R.id.et_plaza_search);
        tvPlazaCurrentTag = (TextView) findViewById(R.id.tv_plaza_current_tag);
        lvPlazaPlaylists = (ListView) findViewById(R.id.lv_plaza_playlists);

        plazaPlaylistsAdapter = new SimpleAdapter(this, plazaPlaylistsData, android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvPlazaPlaylists.setAdapter(plazaPlaylistsAdapter);

        // 排行榜页
        spinnerRankingPlatform = (Spinner) findViewById(R.id.spinner_ranking_platform);
        tvRankingBoardTitle = (TextView) findViewById(R.id.tv_ranking_board_title);
        lvRankingBoards = (ListView) findViewById(R.id.lv_ranking_boards);
        lvRankingSongs = (ListView) findViewById(R.id.lv_ranking_songs);

        rankingBoardsAdapter = new SimpleAdapter(this, rankingBoardsData, android.R.layout.simple_list_item_1,
                new String[]{"title"}, new int[]{android.R.id.text1});
        lvRankingBoards.setAdapter(rankingBoardsAdapter);

        rankingSongsAdapter = new SimpleAdapter(this, rankingSongsData, android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvRankingSongs.setAdapter(rankingSongsAdapter);

        // 我的收藏页
        tvFavTitle = (TextView) findViewById(R.id.tv_fav_title);
        btnCreatePlaylist = (Button) findViewById(R.id.btn_create_playlist);
        btnFavBack = (Button) findViewById(R.id.btn_fav_back);
        lvFavPlaylists = (ListView) findViewById(R.id.lv_fav_playlists);

        favPlaylistsAdapter = new SimpleAdapter(this, favPlaylistsData, android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvFavPlaylists.setAdapter(favPlaylistsAdapter);

        // 本地音乐页
        tvLocalPathStatus = (TextView) findViewById(R.id.tv_local_path_status);
        btnScanLocalMusic = (Button) findViewById(R.id.btn_scan_local_music);
        lvLocalMusic = (ListView) findViewById(R.id.lv_local_music);

        localMusicAdapter = new SimpleAdapter(this, localMusicData, android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvLocalMusic.setAdapter(localMusicAdapter);

        // 设置页
        etServer = (EditText) findViewById(R.id.et_server);
        etUsername = (EditText) findViewById(R.id.et_username);
        etPassword = (EditText) findViewById(R.id.et_password);
        etTimeoutSec = (EditText) findViewById(R.id.et_timeout_sec);
        etRetryCount = (EditText) findViewById(R.id.et_retry_count);
        etDownloadPath = (EditText) findViewById(R.id.et_download_path);
        spinnerConfigBitrate = (Spinner) findViewById(R.id.spinner_config_bitrate);
        etCacheSize = (EditText) findViewById(R.id.et_cache_size);
        btnClearCache = (Button) findViewById(R.id.btn_clear_cache);
        btnSaveSettings = (Button) findViewById(R.id.btn_save_settings);
        tvCacheUsed = (TextView) findViewById(R.id.tv_cache_used);

        // 底部播放控制器 & 详情页
        layoutBottomPlayer = (LinearLayout) findViewById(R.id.layout_bottom_player);
        ivBottomCover = (ImageView) findViewById(R.id.iv_bottom_cover);
        tvCurrentSong = (TextView) findViewById(R.id.tv_current_song);
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
        } else if (page == PAGE_PLAZA) {
            loadPlazaSonglists();
        } else if (page == PAGE_RANKING) {
            loadLeaderboardBoards();
        } else if (page == PAGE_FAV) {
            showFavAndCustomPlaylists();
        } else if (page == PAGE_LOCAL) {
            scanLocalMusicFiles();
        } else if (page == PAGE_SETTINGS) {
            updateCacheSizeDisplay();
        }
    }

    // 热门搜索词加载与动态点击
    private void fetchHotSearchForCurrentPlatform() {
        int pos = spinnerSearchPlatform.getSelectedItemPosition();
        if (pos < 0 || pos >= LxServerApi.PLATFORM_CODES.length) pos = 0;
        final String code = LxServerApi.PLATFORM_CODES[pos];
        final String server = prefs.getString("server", "http://127.0.0.1:9588");

        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<String> hotWords = LxServerApi.fetchHotSearch(server, code);
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
                            tagBtn.setPadding((int)(10 * density), 0, (int)(10 * density), 0);
                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT, (int)(28 * density));
                            lp.rightMargin = (int)(6 * density);
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

    // 搜索执行
    private void performSearch(final String keyword) {
        if (keyword == null || keyword.trim().length() == 0) return;
        int pPos = spinnerSearchPlatform.getSelectedItemPosition();
        final String source = LxServerApi.PLATFORM_CODES[pPos >= 0 ? pPos : 0];
        final int typePos = spinnerSearchType.getSelectedItemPosition(); // 0: 歌曲, 1: 歌手, 2: 专辑

        Toast.makeText(this, "正在搜索: " + keyword, Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String encoded = URLEncoder.encode(keyword.trim(), "UTF-8");
                    String queryParams;
                    if (typePos == 1) {
                        queryParams = "search3.view?query=" + encoded + "&artistCount=100&albumCount=0&songCount=0&source=" + source + "&" + getAuthParams();
                    } else if (typePos == 2) {
                        queryParams = "search3.view?query=" + encoded + "&albumCount=100&artistCount=0&songCount=0&source=" + source + "&" + getAuthParams();
                    } else {
                        queryParams = "search3.view?query=" + encoded + "&songCount=500&artistCount=0&albumCount=0&source=" + source + "&" + getAuthParams();
                    }
                    final String jsonStr = requestApi(queryParams);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            handleSearchResultsJson(jsonStr, keyword, typePos);
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
                    tvSearchResultTitle.setText("歌手结果: " + query + " (" + searchResultsList.size() + " 位)");
                } else if (typePos == 2 && result.has("album")) {
                    Object albObj = result.get("album");
                    if (albObj instanceof JSONArray) {
                        JSONArray arr = (JSONArray) albObj;
                        for (int i = 0; i < arr.length(); i++) addAlbumRow(arr.getJSONObject(i), searchResultsList, searchResultsData);
                    } else if (albObj instanceof JSONObject) {
                        addAlbumRow((JSONObject) albObj, searchResultsList, searchResultsData);
                    }
                    tvSearchResultTitle.setText("专辑结果: " + query + " (" + searchResultsList.size() + " 张)");
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
        tvSearchResultTitle.setText("歌曲结果: " + query + " (共 " + searchResultsList.size() + " 首)");
        searchResultsAdapter.notifyDataSetChanged();
    }

    // 歌单广场加载
    private void loadPlazaSonglists() {
        btnPlazaBack.setVisibility(View.GONE);
        tvPlazaCurrentTag.setText("全部分类歌单");
        int pos = spinnerPlazaPlatform.getSelectedItemPosition();
        final String code = LxServerApi.PLAZA_PLATFORM_CODES[pos >= 0 ? pos : 0];

        plazaPlaylistsList.clear();
        plazaPlaylistsData.clear();
        plazaPlaylistsAdapter.notifyDataSetChanged();

        new Thread(new Runnable() {
            @Override
            public void run() {
                // 优先通过 Subsonic getPlaylists 或服务端对应分类拉取
                String res = requestApi("getPlaylists.view?source=" + code + "&tag=" + URLEncoder.encode(currentPlazaTag) + "&" + getAuthParams());
                if (res != null) {
                    try {
                        JSONObject root = new JSONObject(res).getJSONObject("subsonic-response");
                        JSONObject playlistsObj = root.optJSONObject("playlists");
                        if (playlistsObj != null && playlistsObj.has("playlist")) {
                            Object plObj = playlistsObj.get("playlist");
                            if (plObj instanceof JSONArray) {
                                JSONArray arr = (JSONArray) plObj;
                                for (int i = 0; i < arr.length(); i++) parsePlazaItem(arr.getJSONObject(i));
                            } else if (plObj instanceof JSONObject) {
                                parsePlazaItem((JSONObject) plObj);
                            }
                        }
                    } catch (Exception ignored) {}
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        plazaPlaylistsAdapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    private void parsePlazaItem(JSONObject o) throws Exception {
        String id = o.getString("id");
        String name = o.getString("name");
        int count = o.optInt("songCount", 0);
        DisplayEntry entry = new DisplayEntry(id, name, "", "歌曲数: " + count + " 首", null, "歌单", false);
        plazaPlaylistsList.add(entry);
        Map<String, String> row = new HashMap<String, String>();
        row.put("title", name);
        row.put("subtitle", "歌曲数: " + count + " 首");
        plazaPlaylistsData.add(row);
    }

    // 歌单分类筛选弹窗 (匹配图片设计)
    private void showCategoryDialog() {
        int pos = spinnerPlazaPlatform.getSelectedItemPosition();
        String code = LxServerApi.PLAZA_PLATFORM_CODES[pos >= 0 ? pos : 0];
        Map<String, String[]> categories = LxServerApi.getPresetCategories(code);

        final ArrayList<String> flatTags = new ArrayList<String>();
        flatTags.add("全部歌单");
        for (Map.Entry<String, String[]> entry : categories.entrySet()) {
            flatTags.add("--- " + entry.getKey() + " ---");
            for (String t : entry.getValue()) flatTags.add(t);
        }

        new AlertDialog.Builder(this)
                .setTitle("选择分类 (" + LxServerApi.PLAZA_PLATFORM_NAMES[pos] + ")")
                .setItems(flatTags.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String selected = flatTags.get(which);
                        if (selected.startsWith("---")) return;
                        currentPlazaTag = "全部歌单".equals(selected) ? "" : selected;
                        btnPlazaCategory.setText(selected + " ▾");
                        tvPlazaCurrentTag.setText("当前分类: " + selected);
                        loadPlazaSonglists();
                    }
                })
                .show();
    }

    // 导入指定平台歌单弹窗
    private void promptImportPlaylist() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(20, 10, 20, 10);

        final Spinner spSource = new Spinner(this);
        spSource.setAdapter(new SimpleDarkAdapter(LxServerApi.PLAZA_PLATFORM_NAMES));
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
                        String srcCode = LxServerApi.PLAZA_PLATFORM_CODES[srcIdx];
                        if (text.length() > 0) {
                            executeImportPlaylist(srcCode, text);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void executeImportPlaylist(final String source, final String idOrUrl) {
        Toast.makeText(this, "正在导入外部歌单...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String param = "source=" + source + "&idOrUrl=" + URLEncoder.encode(idOrUrl, "UTF-8");
                    requestApi("importPlaylist.view?" + param + "&" + getAuthParams());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "导入完成", Toast.LENGTH_SHORT).show();
                            loadPlazaSonglists();
                        }
                    });
                } catch (Exception ignored) {}
            }
        }).start();
    }

    // 排行榜加载 (左侧榜单 + 右侧歌曲)
    private void loadLeaderboardBoards() {
        int pos = spinnerRankingPlatform.getSelectedItemPosition();
        final String code = LxServerApi.PLAZA_PLATFORM_CODES[pos >= 0 ? pos : 0];
        final String[][] presetBoards = LxServerApi.getPresetLeaderboards(code);

        rankingBoardsList.clear();
        rankingBoardsData.clear();
        for (String[] board : presetBoards) {
            rankingBoardsList.add(new DisplayEntry(board[1], board[0], "", "榜单", null, "排行榜", false));
            Map<String, String> row = new HashMap<String, String>();
            row.put("title", board[0]);
            rankingBoardsData.add(row);
        }
        rankingBoardsAdapter.notifyDataSetChanged();

        if (!rankingBoardsList.isEmpty()) {
            loadLeaderboardSongs(rankingBoardsList.get(0));
        }
    }

    private void loadLeaderboardSongs(final DisplayEntry board) {
        tvRankingBoardTitle.setText(board.title);
        rankingSongsList.clear();
        rankingSongsData.clear();
        rankingSongsAdapter.notifyDataSetChanged();

        new Thread(new Runnable() {
            @Override
            public void run() {
                String res = requestApi("getPlaylist.view?id=" + URLEncoder.encode(board.id) + "&" + getAuthParams());
                if (res != null) {
                    try {
                        JSONObject root = new JSONObject(res).getJSONObject("subsonic-response");
                        JSONObject playlist = root.optJSONObject("playlist");
                        if (playlist != null && playlist.has("entry")) {
                            Object entryObj = playlist.get("entry");
                            if (entryObj instanceof JSONArray) {
                                JSONArray arr = (JSONArray) entryObj;
                                for (int i = 0; i < arr.length(); i++) addSongRow(arr.getJSONObject(i), rankingSongsList, rankingSongsData);
                            } else if (entryObj instanceof JSONObject) {
                                addSongRow((JSONObject) entryObj, rankingSongsList, rankingSongsData);
                            }
                        }
                    } catch (Exception ignored) {}
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        rankingSongsAdapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    // 我的收藏与自建歌单 (过滤服务器共享榜单)
    private void showFavAndCustomPlaylists() {
        btnFavBack.setVisibility(View.GONE);
        tvFavTitle.setText("我的收藏与歌单");
        currentActiveFavPlaylistId = null;

        favPlaylistsList.clear();
        favPlaylistsData.clear();

        // 1. 我喜欢的音乐
        favPlaylistsList.add(new DisplayEntry("fav_entry", "★ 我喜欢的音乐", "本地及云端", "共 " + favSongIds.size() + " 首", null, "收藏", false));
        Map<String, String> favRow = new HashMap<String, String>();
        favRow.put("title", "★ 我喜欢的音乐");
        favRow.put("subtitle", "共 " + favSongIds.size() + " 首");
        favPlaylistsData.add(favRow);

        // 2. 本地自建特色歌单
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

        // 3. 服务端用户自己的私有歌单 (排除榜单和共享)
        for (DisplayEntry e : rawServerUserPlaylists) {
            favPlaylistsList.add(e);
            Map<String, String> row = new HashMap<String, String>();
            row.put("title", "☁ " + e.title);
            row.put("subtitle", e.quality + " (云端自建)");
            favPlaylistsData.add(row);
        }

        favPlaylistsAdapter.notifyDataSetChanged();
        fetchServerPlaylistsQuietly();
    }

    private void fetchServerPlaylistsQuietly() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String jsonStr = requestApi("getPlaylists.view?" + getAuthParams());
                if (jsonStr == null) return;
                try {
                    JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
                    JSONObject playlistsObj = root.optJSONObject("playlists");
                    if (playlistsObj != null && playlistsObj.has("playlist")) {
                        rawServerUserPlaylists.clear();
                        Object plObj = playlistsObj.get("playlist");
                        if (plObj instanceof JSONArray) {
                            JSONArray arr = (JSONArray) plObj;
                            for (int i = 0; i < arr.length(); i++) parseUserPlaylistItem(arr.getJSONObject(i));
                        } else if (plObj instanceof JSONObject) {
                            parseUserPlaylistItem((JSONObject) plObj);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void parseUserPlaylistItem(JSONObject p) throws Exception {
        String name = p.getString("name");
        int count = p.optInt("songCount", 0);
        String id = p.getString("id");
        String nLower = name.trim().toLowerCase();
        // 过滤榜单及公共只读歌单
        if (name.contains("榜") || name.startsWith("Top") || name.endsWith("榜") || "starred".equals(nLower)) {
            return;
        }
        DisplayEntry entry = new DisplayEntry(id, name, "", "歌曲数: " + count, null, count + " 首", false);
        rawServerUserPlaylists.add(entry);
    }

    // 本地音乐扫描与播放
    private void scanLocalMusicFiles() {
        String customPath = prefs.getString("download_path", getDefaultDownloadPath());
        tvLocalPathStatus.setText("本地目录: " + customPath);
        localMusicList.clear();
        localMusicData.clear();
        localMusicAdapter.notifyDataSetChanged();

        File dir = new File(customPath);
        if (!dir.exists() || !dir.isDirectory()) {
            dir.mkdirs();
        }
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
                        DisplayEntry entry = new DisplayEntry("local_file_" + f.getAbsolutePath().hashCode(),
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
        Toast.makeText(this, "扫描到 " + localMusicList.size() + " 首本地歌曲", Toast.LENGTH_SHORT).show();
    }

    // 设置项保存
    private void saveAndTestSettings() {
        saveConfig();
        Toast.makeText(this, "配置已保存，正在测试服务端...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String pingRes = requestApi("ping.view?" + getAuthParams());
                final boolean success = pingRes != null && pingRes.contains("\"status\":\"ok\"");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, success ? "服务器连接成功！" : "连接失败，请检查地址与密码", Toast.LENGTH_LONG).show();
                        if (success) {
                            fetchServerFavoritesQuietly();
                            fetchHotSearchForCurrentPlatform();
                        }
                    }
                });
            }
        }).start();
    }

    private void setupSpinners() {
        // 搜索平台 Spinner
        spinnerSearchPlatform.setAdapter(new SimpleDarkAdapter(LxServerApi.PLATFORM_NAMES));
        spinnerSearchPlatform.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                fetchHotSearchForCurrentPlatform();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 搜索类型 Spinner
        spinnerSearchType.setAdapter(new SimpleDarkAdapter(SEARCH_TYPES));

        // 歌单广场平台 Spinner
        spinnerPlazaPlatform.setAdapter(new SimpleDarkAdapter(LxServerApi.PLAZA_PLATFORM_NAMES));
        spinnerPlazaPlatform.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentPlazaTag = "";
                btnPlazaCategory.setText("选择分类 ▾");
                loadPlazaSonglists();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 排行榜平台 Spinner
        spinnerRankingPlatform.setAdapter(new SimpleDarkAdapter(LxServerApi.PLAZA_PLATFORM_NAMES));
        spinnerRankingPlatform.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                loadLeaderboardBoards();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 码率设置 Spinner
        spinnerConfigBitrate.setAdapter(new SimpleDarkAdapter(BITRATE_LABELS));
        spinnerConfigBitrate.setSelection(getBitrateIndex(getSavedBitrate()));
    }

    private void setupListeners() {
        // 退出应用
        View.OnClickListener exitListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) { performAppExit(); }
        };
        btnExitApp.setOnClickListener(exitListener);
        btnDetailExitApp.setOnClickListener(exitListener);

        // 搜索提交
        btnSearchSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performSearch(etSearchKeyword.getText().toString().trim());
            }
        });

        // 搜索结果点击与长按
        lvSearchResults.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= searchResultsList.size()) return;
                DisplayEntry entry = searchResultsList.get(position);
                if (entry.isSong) {
                    playSongInList(searchResultsList, entry);
                }
            }
        });
        lvSearchResults.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < searchResultsList.size()) {
                    showSongLongClickMenu(searchResultsList.get(position), position);
                    return true;
                }
                return false;
            }
        });

        // 歌单广场分类与导入
        btnPlazaCategory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showCategoryDialog(); }
        });
        btnPlazaImport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { promptImportPlaylist(); }
        });
        btnPlazaBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { loadPlazaSonglists(); }
        });
        lvPlazaPlaylists.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= plazaPlaylistsList.size()) return;
                DisplayEntry entry = plazaPlaylistsList.get(position);
                if (!entry.isSong) {
                    openSonglistDetails(entry);
                } else {
                    playSongInList(plazaPlaylistsList, entry);
                }
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

        // 排行榜切换与点击
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

        // 我的收藏
        btnCreatePlaylist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { promptCreatePlaylist(); }
        });
        btnFavBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showFavAndCustomPlaylists(); }
        });
        lvFavPlaylists.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= favPlaylistsList.size()) return;
                DisplayEntry entry = favPlaylistsList.get(position);
                if (!entry.isSong) {
                    openFavPlaylistSongs(entry);
                } else {
                    playSongInList(favPlaylistsList, entry);
                }
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

        // 本地音乐
        btnScanLocalMusic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { scanLocalMusicFiles(); }
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

        // 设置保存与清空缓存
        btnSaveSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { saveAndTestSettings(); }
        });
        btnClearCache.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CacheManager.clearAllCache(MainActivity.this);
                updateCacheSizeDisplay();
                Toast.makeText(MainActivity.this, "缓存已清空", Toast.LENGTH_SHORT).show();
            }
        });

        // 播放控制与详情页响应
        setupPlaybackControls();
    }

    private void openSonglistDetails(final DisplayEntry playlistEntry) {
        btnPlazaBack.setVisibility(View.VISIBLE);
        tvPlazaCurrentTag.setText("歌单: " + playlistEntry.title);
        plazaPlaylistsList.clear();
        plazaPlaylistsData.clear();
        plazaPlaylistsAdapter.notifyDataSetChanged();

        new Thread(new Runnable() {
            @Override
            public void run() {
                String res = requestApi("getPlaylist.view?id=" + URLEncoder.encode(playlistEntry.id) + "&" + getAuthParams());
                if (res != null) {
                    try {
                        JSONObject root = new JSONObject(res).getJSONObject("subsonic-response");
                        JSONObject pl = root.optJSONObject("playlist");
                        if (pl != null && pl.has("entry")) {
                            Object obj = pl.get("entry");
                            if (obj instanceof JSONArray) {
                                JSONArray arr = (JSONArray) obj;
                                for (int i = 0; i < arr.length(); i++) addSongRow(arr.getJSONObject(i), plazaPlaylistsList, plazaPlaylistsData);
                            } else if (obj instanceof JSONObject) {
                                addSongRow((JSONObject) obj, plazaPlaylistsList, plazaPlaylistsData);
                            }
                        }
                    } catch (Exception ignored) {}
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        plazaPlaylistsAdapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
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

        // 云端歌单拉取
        new Thread(new Runnable() {
            @Override
            public void run() {
                String res = requestApi("getPlaylist.view?id=" + URLEncoder.encode(playlistEntry.id) + "&" + getAuthParams());
                if (res != null) {
                    try {
                        JSONObject root = new JSONObject(res).getJSONObject("subsonic-response");
                        JSONObject pl = root.optJSONObject("playlist");
                        if (pl != null && pl.has("entry")) {
                            Object obj = pl.get("entry");
                            if (obj instanceof JSONArray) {
                                JSONArray arr = (JSONArray) obj;
                                for (int i = 0; i < arr.length(); i++) addSongRow(arr.getJSONObject(i), favPlaylistsList, favPlaylistsData);
                            } else if (obj instanceof JSONObject) {
                                addSongRow((JSONObject) obj, favPlaylistsList, favPlaylistsData);
                            }
                        }
                    } catch (Exception ignored) {}
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
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
                String jsonStr = requestApi("getStarred2.view?" + getAuthParams());
                if (jsonStr == null || !jsonStr.contains("\"song\"")) {
                    jsonStr = requestApi("getStarred.view?" + getAuthParams());
                }
                final String finalJson = jsonStr;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (finalJson != null) {
                            try {
                                JSONObject root = new JSONObject(finalJson).getJSONObject("subsonic-response");
                                JSONObject starred = root.optJSONObject("starred2");
                                if (starred == null) starred = root.optJSONObject("starred");
                                if (starred != null && starred.has("song")) {
                                    Object songObj = starred.get("song");
                                    if (songObj instanceof JSONArray) {
                                        JSONArray arr = (JSONArray) songObj;
                                        for (int i = 0; i < arr.length(); i++) addSongRow(arr.getJSONObject(i), favPlaylistsList, favPlaylistsData);
                                    } else if (songObj instanceof JSONObject) {
                                        addSongRow((JSONObject) songObj, favPlaylistsList, favPlaylistsData);
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                        favPlaylistsAdapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    // 播放列表与歌曲交互
    private void playSongInList(ArrayList<DisplayEntry> list, DisplayEntry entry) {
        ArrayList<MusicService.SongItem> queue = new ArrayList<MusicService.SongItem>();
        int clickedSongIndex = 0;
        for (int i = 0; i < list.size(); i++) {
            DisplayEntry item = list.get(i);
            if (item.isSong) {
                if (item.id.equals(entry.id)) {
                    clickedSongIndex = queue.size();
                }
                String playUrl = item.localPath != null ? ("file://" + item.localPath) : buildStreamUrl(item.id);
                queue.add(new MusicService.SongItem(item.id, item.title, item.artist, playUrl, item.coverArt, item.quality));
            }
        }
        MusicService.setQueue(queue, clickedSongIndex, MainActivity.this);
        refreshQueueList();
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

        View.OnClickListener nextListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_NEXT));
            }
        };
        btnNext.setOnClickListener(nextListener);
        btnDetailNext.setOnClickListener(nextListener);

        View.OnClickListener prevListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_PREV));
            }
        };
        btnPrev.setOnClickListener(prevListener);
        btnDetailPrev.setOnClickListener(prevListener);

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
            public void onClick(View v) { new EqualizerDialog(MainActivity.this).show(); }
        };
        btnOpenEq.setOnClickListener(eqListener);
        btnDetailEq.setOnClickListener(eqListener);

        View.OnClickListener openDetailListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) { layoutDetailOverlay.setVisibility(View.VISIBLE); }
        };
        ivBottomCover.setOnClickListener(openDetailListener);
        btnCloseDetail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { layoutDetailOverlay.setVisibility(View.GONE); }
        });

        btnToggleQueue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (layoutQueuePanel.getVisibility() == View.VISIBLE) layoutQueuePanel.setVisibility(View.GONE);
                else {
                    refreshQueueList();
                    layoutQueuePanel.setVisibility(View.VISIBLE);
                }
            }
        });
        btnCloseQueue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { layoutQueuePanel.setVisibility(View.GONE); }
        });

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

        // 封面模式切换 (黑胶 / 方形封面)
        View.OnClickListener coverToggleListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) { toggleCoverDisplayMode(); }
        };
        layoutVinylContainer.setOnClickListener(coverToggleListener);
        ivVinylCircularCover.setOnClickListener(coverToggleListener);
        viewTonearm.setOnClickListener(coverToggleListener);
        ivSquareCover.setOnClickListener(coverToggleListener);

        layoutDetailBottomBlank.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { toggleDetailQueueView(); }
        });
        btnDetailQueue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { toggleDetailQueueView(); }
        });

        // 歌词字体及延迟
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
    }

    // 维持长按歌曲与歌单菜单功能完全一致
    private void showSongLongClickMenu(final DisplayEntry entry, final int position) {
        if (!entry.isSong) return;
        final boolean fav = isFav(entry.id);
        ArrayList<String> optList = new ArrayList<String>();
        optList.add(fav ? "取消收藏" : "收藏到我的音乐");
        optList.add("添加到歌单...");
        optList.add("从当前列表移除");
        optList.add("下载歌曲文件");
        final String[] options = optList.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle(entry.title + " - " + entry.artist)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String opt = options[which];
                        if (opt.contains("收藏")) {
                            serverStarSong(entry.id, !fav);
                        } else if (opt.contains("添加到歌单")) {
                            showAddToPlaylistDialog(entry);
                        } else if (opt.contains("移除")) {
                            removeFromCurrentView(position, entry);
                        } else if (opt.contains("下载")) {
                            downloadSongItem(entry);
                        }
                    }
                })
                .show();
    }

    private void showPlaylistLongClickMenu(final DisplayEntry playlistEntry) {
        if ("fav_entry".equals(playlistEntry.id) || "local_featured".equals(playlistEntry.id)
                || "local_car".equals(playlistEntry.id)) return;

        String[] options = new String[]{"重命名歌单", "删除歌单"};
        new AlertDialog.Builder(this)
                .setTitle("歌单操作: " + playlistEntry.title)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) promptRenamePlaylist(playlistEntry);
                        else if (which == 1) promptDeletePlaylist(playlistEntry);
                    }
                })
                .show();
    }

    // ----------------------- 基础辅助方法 -----------------------
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
                            showFavAndCustomPlaylists();
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
                        public void run() { showFavAndCustomPlaylists(); }
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
                        public void run() { showFavAndCustomPlaylists(); }
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
            }
        }
    }

    private void downloadSongItem(final DisplayEntry entry) {
        String customPath = prefs.getString("download_path", getDefaultDownloadPath());
        File dir = new File(customPath);
        if (!dir.exists()) dir.mkdirs();
        Toast.makeText(this, "开始下载: " + entry.title, Toast.LENGTH_SHORT).show();
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

    private void serverStarSong(final String songId, final boolean toStar) {
        if (songId == null) return;
        if (toStar) favSongIds.add(songId);
        else favSongIds.remove(songId);
        saveFavSet();
        updateFavButtonState(songId);
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

    private void fetchServerFavoritesQuietly() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String jsonStr = requestApi("getStarred2.view?" + getAuthParams());
                if (jsonStr == null) jsonStr = requestApi("getStarred.view?" + getAuthParams());
                if (jsonStr != null) {
                    try {
                        JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
                        JSONObject starred = root.optJSONObject("starred2");
                        if (starred == null) starred = root.optJSONObject("starred");
                        if (starred != null && starred.has("song")) {
                            favSongIds.clear();
                            Object songObj = starred.get("song");
                            if (songObj instanceof JSONArray) {
                                JSONArray arr = (JSONArray) songObj;
                                for (int i = 0; i < arr.length(); i++) favSongIds.add(arr.getJSONObject(i).getString("id"));
                            } else if (songObj instanceof JSONObject) {
                                favSongIds.add(((JSONObject) songObj).getString("id"));
                            }
                            saveFavSet();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() { updateFavButtonState(null); }
                            });
                        }
                    } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    private boolean isFav(String songId) { return songId != null && favSongIds.contains(songId); }
    private void loadFavSet() { favSongIds = new HashSet<String>(prefs.getStringSet("fav_songs_set", new HashSet<String>())); }
    private void saveFavSet() { prefs.edit().putStringSet("fav_songs_set", favSongIds).commit(); }

    private void loadLocalPlaylists() {
        featuredSongs.clear(); carSongs.clear();
        try {
            String fStr = prefs.getString("local_playlist_featured", "[]");
            JSONArray fArr = new JSONArray(fStr);
            for (int i = 0; i < fArr.length(); i++) {
                JSONObject o = fArr.getJSONObject(i);
                featuredSongs.add(new DisplayEntry(o.getString("id"), o.getString("title"), o.optString("artist", "未知"), "", null, "本地", true));
            }
        } catch (Exception ignored) {}
    }

    private void saveLocalPlaylists() {
        try {
            JSONArray fArr = new JSONArray();
            for (DisplayEntry e : featuredSongs) {
                JSONObject o = new JSONObject();
                o.put("id", e.id); o.put("title", e.title); o.put("artist", e.artist);
                fArr.put(o);
            }
            prefs.edit().putString("local_playlist_featured", fArr.toString()).commit();
        } catch (Exception ignored) {}
    }

    private void setupControlIcons() {
        int dark = 0xFF10141A, light = 0xFFE2E8F0, cyan = 0xFF00E5FF, red = 0xFFFF6B6B;
        btnPrev.setImageDrawable(MediaIconHelper.createPreviousIcon(this, 18, light));
        btnNext.setImageDrawable(MediaIconHelper.createNextIcon(this, 18, light));
        btnPlayPause.setImageDrawable(MediaIconHelper.createPlayIcon(this, 22, dark));
        btnDetailPrev.setImageDrawable(MediaIconHelper.createPreviousIcon(this, 22, light));
        btnDetailNext.setImageDrawable(MediaIconHelper.createNextIcon(this, 22, light));
        btnDetailPlayPause.setImageDrawable(MediaIconHelper.createPlayIcon(this, 28, dark));
        btnExitApp.setImageDrawable(MediaIconHelper.createPowerIcon(this, 18, red));
        btnDetailExitApp.setImageDrawable(MediaIconHelper.createPowerIcon(this, 18, red));
        btnOpenEq.setImageDrawable(MediaIconHelper.createEqualizerIcon(this, 18, cyan));
        btnDetailEq.setImageDrawable(MediaIconHelper.createEqualizerIcon(this, 20, cyan));
        updateModeIcons(MusicService.getCurrentMode());
    }

    private void updateModeIcons(int mode) {
        int color = 0xFF00E5FF;
        if (mode == MusicService.MODE_SHUFFLE) {
            btnMode.setImageDrawable(MediaIconHelper.createShuffleIcon(this, 18, color));
            btnDetailMode.setImageDrawable(MediaIconHelper.createShuffleIcon(this, 20, color));
        } else if (mode == MusicService.MODE_SINGLE) {
            btnMode.setImageDrawable(MediaIconHelper.createRepeatOneIcon(this, 18, color));
            btnDetailMode.setImageDrawable(MediaIconHelper.createRepeatOneIcon(this, 20, color));
        } else {
            btnMode.setImageDrawable(MediaIconHelper.createRepeatIcon(this, 18, color));
            btnDetailMode.setImageDrawable(MediaIconHelper.createRepeatIcon(this, 20, color));
        }
    }

    private void updatePlayPauseIcons(boolean isPlaying) {
        int dark = 0xFF10141A;
        btnPlayPause.setImageDrawable(isPlaying ? MediaIconHelper.createPauseIcon(this, 20, dark) : MediaIconHelper.createPlayIcon(this, 22, dark));
        btnDetailPlayPause.setImageDrawable(isPlaying ? MediaIconHelper.createPauseIcon(this, 26, dark) : MediaIconHelper.createPlayIcon(this, 28, dark));
    }

    private void setupVinylAnimation() {
        vinylRotateAnim = new RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        vinylRotateAnim.setDuration(12000);
        vinylRotateAnim.setRepeatCount(Animation.INFINITE);
        vinylRotateAnim.setInterpolator(new LinearInterpolator());
    }

    private void updateVinylAnimationState() {
        if (flVinylDisc == null) return;
        if (isVinylDisplayMode && isCurrentSongPlaying) {
            if (flVinylDisc.getAnimation() == null) flVinylDisc.startAnimation(vinylRotateAnim);
        } else {
            flVinylDisc.clearAnimation();
        }
    }

    private void updateCoverDisplayMode() {
        if (isVinylDisplayMode) {
            layoutVinylContainer.setVisibility(View.VISIBLE);
            ivSquareCover.setVisibility(View.GONE);
            updateVinylAnimationState();
        } else {
            layoutVinylContainer.setVisibility(View.GONE);
            if (flVinylDisc != null) flVinylDisc.clearAnimation();
            ivSquareCover.setVisibility(View.VISIBLE);
        }
    }

    private void toggleCoverDisplayMode() {
        isVinylDisplayMode = !isVinylDisplayMode;
        prefs.edit().putBoolean("is_vinyl_display_mode", isVinylDisplayMode).commit();
        updateCoverDisplayMode();
    }

    private void toggleDetailQueueView() {
        if (layoutDetailQueueView.getVisibility() == View.VISIBLE) {
            layoutDetailQueueView.setVisibility(View.GONE);
            layoutDetailLyricsView.setVisibility(View.VISIBLE);
            btnDetailQueue.setText("队列");
        } else {
            refreshQueueList();
            layoutDetailLyricsView.setVisibility(View.GONE);
            layoutDetailQueueView.setVisibility(View.VISIBLE);
            btnDetailQueue.setText("歌词");
        }
    }

    private void loadSavedConfig() {
        etServer.setText(prefs.getString("server", "http://192.168.1.100:4533"));
        etUsername.setText(prefs.getString("user", "admin"));
        etPassword.setText(prefs.getString("pass", "admin"));
        etCacheSize.setText(prefs.getString("cache_size_mb", "500"));
        etTimeoutSec.setText(prefs.getString("play_timeout_sec", "30"));
        etRetryCount.setText(prefs.getString("play_retry_count", "3"));
        etDownloadPath.setText(prefs.getString("download_path", getDefaultDownloadPath()));
    }

    private void saveConfig() {
        prefs.edit()
                .putString("server", etServer.getText().toString().trim())
                .putString("user", etUsername.getText().toString().trim())
                .putString("pass", etPassword.getText().toString().trim())
                .putString("cache_size_mb", etCacheSize.getText().toString().trim())
                .putString("play_timeout_sec", etTimeoutSec.getText().toString().trim())
                .putString("play_retry_count", etRetryCount.getText().toString().trim())
                .putString("download_path", etDownloadPath.getText().toString().trim())
                .commit();
    }

    private String getDefaultDownloadPath() {
        try {
            File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
            if (musicDir != null) return musicDir.getAbsolutePath();
        } catch (Throwable ignored) {}
        return "/sdcard/Music";
    }

    private String getSavedBitrate() { return prefs.getString("default_bitrate", "auto"); }
    private int getBitrateIndex(String val) {
        for (int i = 0; i < BITRATE_VALUES.length; i++) if (BITRATE_VALUES[i].equalsIgnoreCase(val)) return i;
        return 0;
    }
    private String getBitrateDisplay(String val, String orig) {
        if ("128".equalsIgnoreCase(val)) return "128K MP3";
        if ("192".equalsIgnoreCase(val)) return "192K MP3";
        if ("320".equalsIgnoreCase(val)) return "320K MP3";
        if ("flac".equalsIgnoreCase(val)) return "FLAC 无损";
        return (orig != null && orig.length() > 0) ? orig : "标准";
    }

    private String buildStreamUrl(String songId) {
        String base = prefs.getString("server", "");
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String u = prefs.getString("user", "");
        String p = prefs.getString("pass", "");
        try {
            return base + "/rest/stream.view?id=" + URLEncoder.encode(songId, "UTF-8")
                    + "&u=" + URLEncoder.encode(u, "UTF-8") + "&p=" + URLEncoder.encode(p, "UTF-8")
                    + "&v=1.12.0&c=RetroSubsonic";
        } catch (Exception e) {
            return base + "/rest/stream.view?id=" + songId;
        }
    }

    private String getAuthParams() {
        String u = prefs.getString("user", "");
        String p = prefs.getString("pass", "");
        try {
            return "u=" + URLEncoder.encode(u, "UTF-8") + "&p=" + URLEncoder.encode(p, "UTF-8") + "&v=1.12.0&c=RetroSubsonic&f=json";
        } catch (Exception e) {
            return "u=" + u + "&p=" + p + "&v=1.12.0&c=RetroSubsonic&f=json";
        }
    }

    private String requestApi(String pathWithParams) {
        String base = prefs.getString("server", "");
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String fullUrl = base + "/rest/" + pathWithParams;
        return LxServerApi.httpGet(fullUrl);
    }

    private void updateCacheSizeDisplay() {
        if (tvCacheUsed == null) return;
        long bytes = CacheManager.getUsedCacheBytes(this);
        tvCacheUsed.setText(String.format("(已用 %.1f MB)", bytes / (1024.0 * 1024.0)));
    }

    private void addArtistRow(JSONObject a, ArrayList<DisplayEntry> list, ArrayList<Map<String, String>> data) throws Exception {
        String id = a.getString("id");
        String name = a.getString("name");
        list.add(new DisplayEntry("artist_" + id, name, "歌手", "歌手", null, "歌手", false));
        Map<String, String> row = new HashMap<String, String>();
        row.put("title", name); row.put("subtitle", "歌手");
        data.add(row);
    }

    private void addAlbumRow(JSONObject a, ArrayList<DisplayEntry> list, ArrayList<Map<String, String>> data) throws Exception {
        String id = a.getString("id");
        String name = a.getString("name");
        String artist = a.optString("artist", "未知");
        list.add(new DisplayEntry("album_" + id, name, artist, "专辑 - " + artist, null, "专辑", false));
        Map<String, String> row = new HashMap<String, String>();
        row.put("title", name); row.put("subtitle", "专辑 - " + artist);
        data.add(row);
    }

    private void addSongRow(JSONObject s, ArrayList<DisplayEntry> list, ArrayList<Map<String, String>> data) throws Exception {
        String title = s.getString("title");
        String artist = s.optString("artist", "未知");
        String cover = s.optString("coverArt", null);
        int bitRate = s.optInt("bitRate", 0);
        String quality = bitRate > 320 ? "FLAC 无损" : (bitRate > 0 ? bitRate + "K" : "标准");
        list.add(new DisplayEntry(s.getString("id"), title, artist, artist + " [" + quality + "]", cover, quality, true, bitRate, null));
        Map<String, String> row = new HashMap<String, String>();
        row.put("title", title);
        row.put("subtitle", artist + " [" + quality + "]");
        data.add(row);
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
                String urlStr = base + "/rest/getCoverArt.view?id=" + coverId + "&size=400&" + getAuthParams();
                try {
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(5000);
                    InputStream is = conn.getInputStream();
                    final Bitmap bmp = BitmapFactory.decodeStream(is);
                    is.close();
                    conn.disconnect();
                    if (bmp != null) {
                        final Bitmap circular = getCircularBitmap(bmp, 240);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ivVinylCircularCover.setImageBitmap(circular);
                                ivSquareCover.setImageBitmap(bmp);
                                ivBottomCover.setImageBitmap(bmp);
                            }
                        });
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private Bitmap getCircularBitmap(Bitmap bitmap, int size) {
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()), new Rect(0, 0, size, size), paint);
        return output;
    }

    private void loadLyrics(final String songId, final String artist, final String title) {
        lyricRows.clear();
        currentLyricIndex = -1;
        layoutLyricsContainer.removeAllViews();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String raw = requestApi("getLyricsBySongId.view?id=" + URLEncoder.encode(songId) + "&" + getAuthParams());
                final String lrc = parseLrc(raw);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        buildLyricsView(lrc != null ? lrc : "[00:00.00]未找到匹配歌词");
                    }
                });
            }
        }).start();
    }

    private String parseLrc(String jsonStr) {
        if (jsonStr == null) return null;
        try {
            JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
            if (root.has("lyrics")) {
                Object obj = root.get("lyrics");
                if (obj instanceof JSONObject) return ((JSONObject) obj).optString("content", "");
                if (obj instanceof String) return (String) obj;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void buildLyricsView(String rawText) {
        layoutLyricsContainer.removeAllViews();
        lyricRows.clear();
        String[] lines = rawText.split("\n");
        for (String line : lines) {
            int close = line.indexOf(']');
            if (line.startsWith("[") && close > 1) {
                long t = parseTime(line.substring(1, close));
                if (t >= 0) lyricRows.add(new LyricRow(t, line.substring(close + 1).trim()));
            }
        }
        for (LyricRow row : lyricRows) {
            TextView tv = new TextView(this);
            tv.setText(row.text);
            tv.setTextColor(0xFF777777);
            tv.setTextSize(lyricBaseFontSize);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, 10, 0, 10);
            row.view = tv;
            layoutLyricsContainer.addView(tv);
        }
    }

    private long parseTime(String timeStr) {
        try {
            String[] p = timeStr.split(":");
            return (long) (Long.parseLong(p[0]) * 60000 + Float.parseFloat(p[1]) * 1000);
        } catch (Exception e) { return -1; }
    }

    private void updateLyricPosition(int curMs) {
        if (lyricRows.isEmpty() || isUserTouchingLyrics) return;
        int target = -1;
        for (int i = 0; i < lyricRows.size(); i++) {
            if (curMs >= lyricRows.get(i).timeMs) target = i;
            else break;
        }
        if (target != currentLyricIndex && target >= 0) {
            if (currentLyricIndex >= 0 && currentLyricIndex < lyricRows.size()) {
                LyricRow old = lyricRows.get(currentLyricIndex);
                if (old.view != null) {
                    old.view.setTextColor(0xFF777777);
                    old.view.setTextSize(lyricBaseFontSize);
                }
            }
            currentLyricIndex = target;
            final LyricRow cur = lyricRows.get(currentLyricIndex);
            if (cur.view != null) {
                cur.view.setTextColor(0xFF00E5FF);
                cur.view.setTextSize(lyricBaseFontSize + 4);
                scrollLyrics.post(new Runnable() {
                    @Override
                    public void run() {
                        int y = cur.view.getTop() - (scrollLyrics.getHeight() / 2) + (cur.view.getHeight() / 2);
                        scrollLyrics.smoothScrollTo(0, Math.max(0, y));
                    }
                });
            }
        }
    }

    private void adjustLyricOffset(long delta) {
        manualLyricOffsetMs += delta;
        tvLyricOffsetStatus.setText(String.format("%.1fs", manualLyricOffsetMs / 1000.0));
    }
    private void resetLyricOffset() {
        manualLyricOffsetMs = 0;
        tvLyricOffsetStatus.setText("0.0s");
    }
    private void applyLyricFontSize(int delta) {
        lyricBaseFontSize = Math.max(12, Math.min(26, lyricBaseFontSize + delta));
        prefs.edit().putInt("lyric_font_size", lyricBaseFontSize).commit();
        for (LyricRow r : lyricRows) if (r.view != null) r.view.setTextSize(lyricBaseFontSize);
    }

    private String formatTime(int ms) {
        int sec = (ms / 1000) % 60;
        int min = (ms / (1000 * 60)) % 60;
        return String.format("%02d:%02d", min, sec);
    }

    private void performAppExit() {
        new AlertDialog.Builder(this)
                .setTitle("退出应用")
                .setMessage("是否确定退出 Retro Subsonic？")
                .setPositiveButton("退出", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Intent stopIntent = new Intent(MainActivity.this, MusicService.class);
                            stopIntent.setAction(MusicService.ACTION_STOP);
                            startService(stopIntent);
                        } catch (Exception ignored) {}
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
            if (layoutDetailOverlay.getVisibility() == View.VISIBLE) {
                layoutDetailOverlay.setVisibility(View.GONE);
                return true;
            }
            if (layoutQueuePanel.getVisibility() == View.VISIBLE) {
                layoutQueuePanel.setVisibility(View.GONE);
                return true;
            }
            if (btnPlazaBack.getVisibility() == View.VISIBLE) {
                btnPlazaBack.performClick();
                return true;
            }
            if (btnFavBack.getVisibility() == View.VISIBLE) {
                btnFavBack.performClick();
                return true;
            }
            if (currentPage != PAGE_SEARCH) {
                switchPage(PAGE_SEARCH);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(statusReceiver, new IntentFilter(MusicService.BROADCAST_STATUS));
        refreshQueueList();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(statusReceiver); } catch (Exception ignored) {}
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
            tv.setPadding(8, 2, 8, 2);
            tv.setText(items[position] + " ▾");
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
}
