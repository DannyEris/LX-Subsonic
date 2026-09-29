package com.retro.subsonic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends Activity {
    private static final String[] BITRATE_LABELS = new String[]{"自动", "128K", "192K", "320K", "FLAC无损"};
    private static final String[] BITRATE_VALUES = new String[]{"auto", "128", "192", "320", "flac"};
    private static final String[] SEARCH_TYPES = new String[]{"歌曲", "歌手", "专辑"};
    private static final String[] PLATFORMS = new String[]{"聚合搜索", "QQ音乐", "网易云", "酷狗音乐", "酷我音乐", "咪咕音乐"};
    private static final String[] PLATFORM_PREFIXES = new String[]{"all:", "tx:", "wy:", "kg:", "kw:", "mg:"};

    // 页面标识枚举
    private static final int PAGE_SEARCH = 0;
    private static final int PAGE_SQUARE = 1;
    private static final int PAGE_RANKING = 2;
    private static final int PAGE_MY_FAV = 3;
    private static final int PAGE_LOCAL = 4;
    private static final int PAGE_SETTINGS = 5;
    private int currentPage = PAGE_SEARCH;

    // 左侧导航按钮
    private Button navBtnSearch, navBtnPlaylists, navBtnRanking, navBtnMyFav, navBtnLocal, navBtnSettings;
    private View pageSearch, pagePlaylistsSquare, pageRanking, pageMyFav, pageLocalMusic, pageSettings;

    // 搜索页组件
    private Spinner spinnerSearchPlatform, spinnerSearchType;
    private EditText etSearchKeyword;
    private Button btnSearchSubmit, btnRefreshHotSearch;
    private LinearLayout layoutHotSearchBox, layoutSearchResultBox, containerHotSearchTags;
    private TextView tvHotSearchTitle, tvSearchResultTitle;
    private CheckBox cbDedupSongs;
    private ListView lvSearchResults;
    private ArrayList<DisplayEntry> searchResultsList = new ArrayList<DisplayEntry>();
    private ArrayList<Map<String, String>> searchResultsData = new ArrayList<Map<String, String>>();
    private SimpleAdapter searchResultsAdapter;
    private ArrayList<DisplayEntry> rawSearchSongResults = new ArrayList<DisplayEntry>();
    private String lastSearchKeyword = "";

    // 歌单广场组件
    private Spinner spinnerSquarePlatform, spinnerSquareSort;
    private Button btnImportPlaylist;
    private LinearLayout containerSquareTags;
    private ListView lvSquarePlaylists;
    private ArrayList<DisplayEntry> squarePlaylistsList = new ArrayList<DisplayEntry>();
    private ArrayList<Map<String, String>> squarePlaylistsData = new ArrayList<Map<String, String>>();
    private SimpleAdapter squarePlaylistsAdapter;

    // 排行榜组件
    private Spinner spinnerRankingPlatform;
    private TextView tvCurrentBoardName;
    private Button btnRankingPlayAll;
    private LinearLayout containerRankingTabs;
    private ListView lvRankingSongs;
    private ArrayList<DisplayEntry> rankingSongsList = new ArrayList<DisplayEntry>();
    private ArrayList<Map<String, String>> rankingSongsData = new ArrayList<Map<String, String>>();
    private SimpleAdapter rankingSongsAdapter;

    // 我的收藏页组件
    private TextView tvFavListTitle;
    private Button btnFavBack;
    private ListView lvMyPlaylists;
    private ArrayList<DisplayEntry> myPlaylistsList = new ArrayList<DisplayEntry>();
    private ArrayList<Map<String, String>> myPlaylistsData = new ArrayList<Map<String, String>>();
    private SimpleAdapter myPlaylistsAdapter;

    // 本地音乐页组件
    private TextView tvLocalMusicPath;
    private Button btnScanLocalMusic;
    private ListView lvLocalMusic;
    private ArrayList<DisplayEntry> localMusicList = new ArrayList<DisplayEntry>();
    private ArrayList<Map<String, String>> localMusicData = new ArrayList<Map<String, String>>();
    private SimpleAdapter localMusicAdapter;

    // 设置页组件
    private EditText etServer, etUsername, etPassword, etCacheSize, etTimeoutSec, etRetryCount, etDownloadPath;
    private Spinner spinnerConfigBitrate;
    private Button btnClearCache, btnConnect;
    private TextView tvCacheUsed;

    // 迷你播放器及全屏详情浮层
    private LinearLayout layoutBottomPlayer, layoutDetailOverlay;
    private ImageView ivBottomCover, btnPlayPause, btnPrev, btnNext, btnMode, btnOpenEq, btnExitApp;
    private TextView tvCurrentSong, tvTime;
    private SeekBar seekBar;
    private Button btnBottomFav, btnToggleQueue, btnCloseQueue;
    private ListView lvQueue;
    private ArrayList<Map<String, String>> queueData = new ArrayList<Map<String, String>>();
    private SimpleAdapter queueAdapter;
    private LinearLayout layoutQueuePanel;

    // 详情页专属组件
    private Button btnCloseDetail, btnDetailKeepScreen, btnDetailQueue, btnDetailDownload, btnDetailDlna, btnDetailFav;
    private ImageView btnDetailPlayPause, btnDetailPrev, btnDetailNext, btnDetailMode, btnDetailEq, btnDetailExitApp;
    private FrameLayout layoutVinylContainer, flVinylDisc;
    private ImageView ivVinylCircularCover, ivSquareCover;
    private TonearmView viewTonearm;
    private TextView tvDetailTitle, tvDetailArtist, tvDetailQuality, tvDetailBuffer, tvDetailTime;
    private SeekBar detailSeekBar;
    private Spinner spinnerDetailBitrate;
    private ScrollView scrollLyrics;
    private LinearLayout layoutLyricsContainer, layoutDetailLyricsView, layoutDetailQueueView;
    private ListView lvDetailQueue;
    private SimpleAdapter detailQueueAdapter;
    private Button btnLyricDec, btnLyricInc, btnLyricDelay, btnLyricReset, btnLyricAdvance;
    private TextView tvLyricOffsetStatus;

    // 动效与控制状态
    private RotateAnimation vinylRotateAnim;
    private boolean isVinylDisplayMode = true;
    private Bitmap currentRawCoverBitmap, currentCircularCoverBitmap, currentBottomCoverBitmap;
    private boolean isCurrentSongPlaying = false;
    private boolean isKeepScreenOn = false;
    private boolean isUserSeeking = false;
    private String lastLoadedSongId = "";
    private int lyricBaseFontSize = 15;
    private long manualLyricOffsetMs = 0;
    private long dlnaGlobalLyricOffsetMs = 0;
    private String currentLoadedRawLyrics = null;
    private String currentSongIdForLyric = null;
    private String currentArtistForLyric = null;
    private String currentTitleForLyric = null;
    private int lastValidProgressMs = 0;
    private int currentLyricIndex = -1;
    private boolean isUserTouchingLyrics = false;
    private Handler lyricHandler = new Handler();

    private SharedPreferences prefs;
    private Set<String> favSongIds = new HashSet<String>();
    private ArrayList<DisplayEntry> featuredSongs = new ArrayList<DisplayEntry>();
    private ArrayList<DisplayEntry> carSongs = new ArrayList<DisplayEntry>();

    public static class DisplayEntry {
        public String id;
        public String title;
        public String artist;
        public String subtitle;
        public String coverArt;
        public String quality;
        public boolean isSong;
        public int bitRateNumeric;

        public DisplayEntry(String id, String title, String artist, String subtitle, String coverArt, String quality, boolean isSong) {
            this(id, title, artist, subtitle, coverArt, quality, isSong, 0);
        }
        public DisplayEntry(String id, String title, String artist, String subtitle, String coverArt, String quality, boolean isSong, int bitRateNumeric) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.subtitle = subtitle;
            this.coverArt = coverArt;
            this.quality = quality;
            this.isSong = isSong;
            this.bitRateNumeric = bitRateNumeric;
        }
    }

    private static class LyricRow {
        long timeMs;
        String text;
        TextView view;
        LyricRow(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
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
                boolean isBuffering = intent.getBooleanExtra("isBuffering", false);
                int bufferPercent = intent.getIntExtra("bufferPercent", 0);
                int retryCount = intent.getIntExtra("retryCount", 0);
                int maxRetries = intent.getIntExtra("maxRetries", 3);
                String songId = intent.getStringExtra("songId");
                String title = intent.getStringExtra("title");
                String artist = intent.getStringExtra("artist");
                String coverArtId = intent.getStringExtra("coverArtId");
                String quality = intent.getStringExtra("quality");
                String streamUrl = intent.getStringExtra("streamUrl");

                if (title != null) {
                    tvDetailTitle.setText(title);
                    if (retryCount > 0) {
                        tvCurrentSong.setText("重试(" + retryCount + "/" + maxRetries + "): " + title);
                        tvDetailBuffer.setText("(重试 " + retryCount + "/" + maxRetries + ")");
                        tvDetailBuffer.setVisibility(View.VISIBLE);
                    } else if (isPlaying) {
                        if (isBuffering && bufferPercent < 100) {
                            tvCurrentSong.setText(title + " - " + artist + " (缓冲 " + bufferPercent + "%)");
                            tvDetailBuffer.setText("(缓冲 " + bufferPercent + "%)");
                            tvDetailBuffer.setVisibility(View.VISIBLE);
                        } else {
                            tvCurrentSong.setText(title + " - " + artist);
                            tvDetailBuffer.setVisibility(View.GONE);
                        }
                    } else {
                        tvCurrentSong.setText(title + " - " + artist);
                        tvDetailBuffer.setVisibility(View.GONE);
                    }
                    tvDetailArtist.setText(artist);
                    String currentBitrate = getSavedBitrate();
                    tvDetailQuality.setText(getBitrateDisplay(currentBitrate, quality));

                    if (songId != null && !songId.equals(lastLoadedSongId)) {
                        lastLoadedSongId = songId;
                        lastValidProgressMs = 0;
                        if (!DlnaManager.isCasting()) {
                            manualLyricOffsetMs = 0;
                        }
                        updateLyricOffsetStatusView();
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
                int position = intent.getIntExtra("position", 0);
                int duration = intent.getIntExtra("duration", 0);
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
        setupNavigation();
        setupControlIcons();
        setupBitrateSpinners();
        setupSearchSection();
        setupSquareSection();
        setupRankingSection();
        setupMyFavSection();
        setupLocalMusicSection();
        setupVinylAnimation();
        updateCoverDisplayMode();
        loadSavedConfig();
        setupPlayerControls();
        restoreLastSessionIfAvailable();

        // 默认进入搜索首页并加载热搜
        switchPage(PAGE_SEARCH);
        loadPlatformHotSearch("wy");
        syncServerFavoritesQuietly();
    }

    private void initViews() {
        // 侧边导航按钮
        navBtnSearch = (Button) findViewById(R.id.nav_btn_search);
        navBtnPlaylists = (Button) findViewById(R.id.nav_btn_playlists);
        navBtnRanking = (Button) findViewById(R.id.nav_btn_ranking);
        navBtnMyFav = (Button) findViewById(R.id.nav_btn_my_fav);
        navBtnLocal = (Button) findViewById(R.id.nav_btn_local);
        navBtnSettings = (Button) findViewById(R.id.nav_btn_settings);

        // 页面容器
        pageSearch = findViewById(R.id.page_search);
        pagePlaylistsSquare = findViewById(R.id.page_playlists_square);
        pageRanking = findViewById(R.id.page_ranking);
        pageMyFav = findViewById(R.id.page_my_fav);
        pageLocalMusic = findViewById(R.id.page_local_music);
        pageSettings = findViewById(R.id.page_settings);

        // 搜索页
        spinnerSearchPlatform = (Spinner) findViewById(R.id.spinner_search_platform);
        spinnerSearchType = (Spinner) findViewById(R.id.spinner_search_type);
        etSearchKeyword = (EditText) findViewById(R.id.et_search_keyword);
        btnSearchSubmit = (Button) findViewById(R.id.btn_search_submit);
        btnRefreshHotSearch = (Button) findViewById(R.id.btn_refresh_hot_search);
        layoutHotSearchBox = (LinearLayout) findViewById(R.id.layout_hot_search_box);
        layoutSearchResultBox = (LinearLayout) findViewById(R.id.layout_search_result_box);
        containerHotSearchTags = (LinearLayout) findViewById(R.id.container_hot_search_tags);
        tvHotSearchTitle = (TextView) findViewById(R.id.tv_hot_search_title);
        tvSearchResultTitle = (TextView) findViewById(R.id.tv_search_result_title);
        cbDedupSongs = (CheckBox) findViewById(R.id.cb_dedup_songs);
        lvSearchResults = (ListView) findViewById(R.id.lv_search_results);

        // 歌单广场
        spinnerSquarePlatform = (Spinner) findViewById(R.id.spinner_square_platform);
        spinnerSquareSort = (Spinner) findViewById(R.id.spinner_square_sort);
        btnImportPlaylist = (Button) findViewById(R.id.btn_import_playlist);
        containerSquareTags = (LinearLayout) findViewById(R.id.container_square_tags);
        lvSquarePlaylists = (ListView) findViewById(R.id.lv_square_playlists);

        // 排行榜
        spinnerRankingPlatform = (Spinner) findViewById(R.id.spinner_ranking_platform);
        tvCurrentBoardName = (TextView) findViewById(R.id.tv_current_board_name);
        btnRankingPlayAll = (Button) findViewById(R.id.btn_ranking_play_all);
        containerRankingTabs = (LinearLayout) findViewById(R.id.container_ranking_tabs);
        lvRankingSongs = (ListView) findViewById(R.id.lv_ranking_songs);

        // 我的收藏
        tvFavListTitle = (TextView) findViewById(R.id.tv_fav_list_title);
        btnFavBack = (Button) findViewById(R.id.btn_fav_back);
        lvMyPlaylists = (ListView) findViewById(R.id.lv_my_playlists);

        // 本地音乐
        tvLocalMusicPath = (TextView) findViewById(R.id.tv_local_music_path);
        btnScanLocalMusic = (Button) findViewById(R.id.btn_scan_local_music);
        lvLocalMusic = (ListView) findViewById(R.id.lv_local_music);

        // 设置页
        etServer = (EditText) findViewById(R.id.et_server);
        etUsername = (EditText) findViewById(R.id.et_username);
        etPassword = (EditText) findViewById(R.id.et_password);
        etCacheSize = (EditText) findViewById(R.id.et_cache_size);
        etTimeoutSec = (EditText) findViewById(R.id.et_timeout_sec);
        etRetryCount = (EditText) findViewById(R.id.et_retry_count);
        etDownloadPath = (EditText) findViewById(R.id.et_download_path);
        spinnerConfigBitrate = (Spinner) findViewById(R.id.spinner_config_bitrate);
        btnClearCache = (Button) findViewById(R.id.btn_clear_cache);
        btnConnect = (Button) findViewById(R.id.btn_connect);
        tvCacheUsed = (TextView) findViewById(R.id.tv_cache_used);

        // 底部控制器
        layoutBottomPlayer = (LinearLayout) findViewById(R.id.layout_bottom_player);
        ivBottomCover = (ImageView) findViewById(R.id.iv_bottom_cover);
        tvCurrentSong = (TextView) findViewById(R.id.tv_current_song);
        tvTime = (TextView) findViewById(R.id.tv_time);
        seekBar = (SeekBar) findViewById(R.id.seek_bar);
        btnPlayPause = (ImageView) findViewById(R.id.btn_play_pause);
        btnPrev = (ImageView) findViewById(R.id.btn_prev);
        btnNext = (ImageView) findViewById(R.id.btn_next);
        btnMode = (ImageView) findViewById(R.id.btn_mode);
        btnOpenEq = (ImageView) findViewById(R.id.btn_open_eq);
        btnBottomFav = (Button) findViewById(R.id.btn_bottom_fav);
        btnToggleQueue = (Button) findViewById(R.id.btn_toggle_queue);
        btnCloseQueue = (Button) findViewById(R.id.btn_close_queue);
        lvQueue = (ListView) findViewById(R.id.lv_queue);
        layoutQueuePanel = (LinearLayout) findViewById(R.id.layout_queue_panel);
        btnExitApp = (ImageView) findViewById(R.id.btn_exit_app);

        // 详情浮层
        layoutDetailOverlay = (LinearLayout) findViewById(R.id.layout_detail_overlay);
        btnCloseDetail = (Button) findViewById(R.id.btn_close_detail);
        btnDetailKeepScreen = (Button) findViewById(R.id.btn_detail_keep_screen);
        btnDetailQueue = (Button) findViewById(R.id.btn_detail_queue);
        btnDetailDownload = (Button) findViewById(R.id.btn_detail_download);
        btnDetailDlna = (Button) findViewById(R.id.btn_detail_dlna);
        btnDetailExitApp = (ImageView) findViewById(R.id.btn_detail_exit_app);
        btnDetailFav = (Button) findViewById(R.id.btn_detail_fav);
        layoutVinylContainer = (FrameLayout) findViewById(R.id.layout_vinyl_container);
        flVinylDisc = (FrameLayout) findViewById(R.id.fl_vinyl_disc);
        ivVinylCircularCover = (ImageView) findViewById(R.id.iv_vinyl_circular_cover);
        viewTonearm = (TonearmView) findViewById(R.id.view_tonearm);
        ivSquareCover = (ImageView) findViewById(R.id.iv_square_cover);
        tvDetailTitle = (TextView) findViewById(R.id.tv_detail_title);
        tvDetailArtist = (TextView) findViewById(R.id.tv_detail_artist);
        tvDetailQuality = (TextView) findViewById(R.id.tv_detail_quality);
        tvDetailBuffer = (TextView) findViewById(R.id.tv_detail_buffer);
        tvDetailTime = (TextView) findViewById(R.id.tv_detail_time);
        detailSeekBar = (SeekBar) findViewById(R.id.detail_seek_bar);
        spinnerDetailBitrate = (Spinner) findViewById(R.id.spinner_detail_bitrate);
        btnDetailPlayPause = (ImageView) findViewById(R.id.btn_detail_play_pause);
        btnDetailPrev = (ImageView) findViewById(R.id.btn_detail_prev);
        btnDetailNext = (ImageView) findViewById(R.id.btn_detail_next);
        btnDetailMode = (ImageView) findViewById(R.id.btn_detail_mode);
        btnDetailEq = (ImageView) findViewById(R.id.btn_detail_eq);
        scrollLyrics = (ScrollView) findViewById(R.id.scroll_lyrics);
        layoutLyricsContainer = (LinearLayout) findViewById(R.id.layout_lyrics_container);
        layoutDetailLyricsView = (LinearLayout) findViewById(R.id.layout_detail_lyrics_view);
        layoutDetailQueueView = (LinearLayout) findViewById(R.id.layout_detail_queue_view);
        lvDetailQueue = (ListView) findViewById(R.id.lv_detail_queue);
        btnLyricDec = (Button) findViewById(R.id.btn_lyric_dec);
        btnLyricInc = (Button) findViewById(R.id.btn_lyric_inc);
        btnLyricDelay = (Button) findViewById(R.id.btn_lyric_delay);
        btnLyricReset = (Button) findViewById(R.id.btn_lyric_reset);
        btnLyricAdvance = (Button) findViewById(R.id.btn_lyric_advance);
        tvLyricOffsetStatus = (TextView) findViewById(R.id.tv_lyric_offset_status);

        // 队列适配器
        queueAdapter = new SimpleAdapter(this, queueData, android.R.layout.simple_list_item_2, new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvQueue.setAdapter(queueAdapter);
        detailQueueAdapter = new SimpleAdapter(this, queueData, android.R.layout.simple_list_item_2, new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvDetailQueue.setAdapter(detailQueueAdapter);

        // 搜索结果适配器
        searchResultsAdapter = new SimpleAdapter(this, searchResultsData, android.R.layout.simple_list_item_2, new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvSearchResults.setAdapter(searchResultsAdapter);

        // 歌单广场适配器
        squarePlaylistsAdapter = new SimpleAdapter(this, squarePlaylistsData, android.R.layout.simple_list_item_2, new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvSquarePlaylists.setAdapter(squarePlaylistsAdapter);

        // 排行榜歌曲适配器
        rankingSongsAdapter = new SimpleAdapter(this, rankingSongsData, android.R.layout.simple_list_item_2, new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvRankingSongs.setAdapter(rankingSongsAdapter);

        // 我的收藏适配器
        myPlaylistsAdapter = new SimpleAdapter(this, myPlaylistsData, android.R.layout.simple_list_item_2, new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvMyPlaylists.setAdapter(myPlaylistsAdapter);

        // 本地音乐适配器
        localMusicAdapter = new SimpleAdapter(this, localMusicData, android.R.layout.simple_list_item_2, new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvLocalMusic.setAdapter(localMusicAdapter);
    }

    // 侧边栏导航切换逻辑
    private void setupNavigation() {
        View.OnClickListener navClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v == navBtnSearch) switchPage(PAGE_SEARCH);
                else if (v == navBtnPlaylists) switchPage(PAGE_SQUARE);
                else if (v == navBtnRanking) switchPage(PAGE_RANKING);
                else if (v == navBtnMyFav) switchPage(PAGE_MY_FAV);
                else if (v == navBtnLocal) switchPage(PAGE_LOCAL);
                else if (v == navBtnSettings) switchPage(PAGE_SETTINGS);
            }
        };

        navBtnSearch.setOnClickListener(navClickListener);
        navBtnPlaylists.setOnClickListener(navClickListener);
        navBtnRanking.setOnClickListener(navClickListener);
        navBtnMyFav.setOnClickListener(navClickListener);
        navBtnLocal.setOnClickListener(navClickListener);
        navBtnSettings.setOnClickListener(navClickListener);

        btnExitApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { performAppExit(); }
        });
    }

    private void switchPage(int page) {
        currentPage = page;
        // 重置按钮颜色与选择态
        Button[] navButtons = new Button[]{navBtnSearch, navBtnPlaylists, navBtnRanking, navBtnMyFav, navBtnLocal, navBtnSettings};
        View[] pages = new View[]{pageSearch, pagePlaylistsSquare, pageRanking, pageMyFav, pageLocalMusic, pageSettings};

        for (int i = 0; i < navButtons.length; i++) {
            boolean isSel = (i == page);
            navButtons[i].setSelected(isSel);
            navButtons[i].setTextColor(isSel ? 0xFF00E5FF : 0xFFA0A5B5);
            pages[i].setVisibility(isSel ? View.VISIBLE : View.GONE);
        }

        // 切页触发对应业务刷新
        if (page == PAGE_SQUARE && squarePlaylistsList.isEmpty()) {
            loadSquarePlaylists("wy", "全部");
        } else if (page == PAGE_RANKING && rankingSongsList.isEmpty()) {
            loadRankingList("wy");
        } else if (page == PAGE_MY_FAV) {
            loadMyPersonalPlaylists();
        } else if (page == PAGE_LOCAL && localMusicList.isEmpty()) {
            scanLocalMusicFiles();
        }
    }

    // 1. 搜索与热门搜索设置
    private void setupSearchSection() {
        spinnerSearchPlatform.setAdapter(new SimpleDarkAdapter(PLATFORMS));
        spinnerSearchType.setAdapter(new SimpleDarkAdapter(SEARCH_TYPES));

        spinnerSearchPlatform.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String platCode = getPlatformCodeByIndex(position);
                tvHotSearchTitle.setText("🔥 热门搜索 (" + PLATFORMS[position] + ")");
                loadPlatformHotSearch(platCode);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnRefreshHotSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int pos = spinnerSearchPlatform.getSelectedItemPosition();
                loadPlatformHotSearch(getPlatformCodeByIndex(pos));
            }
        });

        btnSearchSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String kw = etSearchKeyword.getText().toString().trim();
                executePlatformSearch(kw);
            }
        });

        lvSearchResults.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < searchResultsList.size()) {
                    playSongInList(searchResultsList, searchResultsList.get(position));
                }
            }
        });

        cbDedupSongs.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                applySongDeduplication(isChecked);
            }
        });
    }

    private String getPlatformCodeByIndex(int idx) {
        switch (idx) {
            case 1: return "tx";
            case 2: return "wy";
            case 3: return "kg";
            case 4: return "kw";
            case 5: return "mg";
            default: return "all";
        }
    }

    private void loadPlatformHotSearch(final String platCode) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<String> words = LxApiHelper.fetchHotSearch(platCode);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        renderHotSearchTags(words);
                    }
                });
            }
        }).start();
    }

    private void renderHotSearchTags(ArrayList<String> words) {
        containerHotSearchTags.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        LinearLayout row = null;
        for (int i = 0; i < words.size(); i++) {
            final String word = words.get(i);
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, (int) (4 * density), 0, (int) (4 * density));
                containerHotSearchTags.addView(row);
            }
            TextView tagView = new TextView(this);
            tagView.setText((i + 1) + ". " + word);
            tagView.setTextColor(i < 3 ? 0xFFFF7043 : 0xFFCBD5E1);
            tagView.setTextSize(12);
            tagView.setBackgroundResource(R.drawable.bg_chip_tag);
            tagView.setPadding((int) (12 * density), (int) (6 * density), (int) (12 * density), (int) (6 * density));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = (int) (3 * density);
            lp.rightMargin = (int) (3 * density);
            tagView.setLayoutParams(lp);
            tagView.setSingleLine(true);
            tagView.setGravity(Gravity.CENTER);
            tagView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    etSearchKeyword.setText(word);
                    executePlatformSearch(word);
                }
            });
            if (row != null) row.addView(tagView);
        }
    }

    private void executePlatformSearch(String query) {
        if (query == null || query.trim().length() == 0) {
            layoutHotSearchBox.setVisibility(View.VISIBLE);
            layoutSearchResultBox.setVisibility(View.GONE);
            return;
        }
        lastSearchKeyword = query.trim();
        layoutHotSearchBox.setVisibility(View.GONE);
        layoutSearchResultBox.setVisibility(View.VISIBLE);

        int platIdx = spinnerSearchPlatform.getSelectedItemPosition();
        String prefix = PLATFORM_PREFIXES[platIdx];
        final String finalQuery = prefix + lastSearchKeyword;
        final int typePos = spinnerSearchType.getSelectedItemPosition();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String encoded = URLEncoder.encode(finalQuery, "UTF-8");
                    String queryParams;
                    if (typePos == 1) {
                        queryParams = "search3.view?query=" + encoded + "&artistCount=100&albumCount=0&songCount=0&" + getAuthParams();
                    } else if (typePos == 2) {
                        queryParams = "search3.view?query=" + encoded + "&albumCount=100&artistCount=0&songCount=0&" + getAuthParams();
                    } else {
                        queryParams = "search3.view?query=" + encoded + "&songCount=500&artistCount=0&albumCount=0&" + getAuthParams();
                    }
                    final String jsonStr = requestApi(queryParams);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            handleSearchResponse(jsonStr, typePos);
                        }
                    });
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void handleSearchResponse(String jsonStr, int searchTypePos) {
        if (jsonStr == null) {
            Toast.makeText(MainActivity.this, "搜索连接超时，请检查服务设置", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
            JSONObject result = root.optJSONObject("searchResult3");
            searchResultsList.clear();
            searchResultsData.clear();
            rawSearchSongResults.clear();
            if (result != null && result.has("song")) {
                Object sObj = result.get("song");
                ArrayList<Map<String, String>> dummyData = new ArrayList<Map<String, String>>();
                if (sObj instanceof JSONArray) {
                    JSONArray arr = (JSONArray) sObj;
                    for (int i = 0; i < arr.length(); i++) addSongRow(arr.getJSONObject(i), rawSearchSongResults, dummyData);
                } else if (sObj instanceof JSONObject) {
                    addSongRow((JSONObject) sObj, rawSearchSongResults, dummyData);
                }
                applySongDeduplication(cbDedupSongs.isChecked());
            }
            searchResultsAdapter.notifyDataSetChanged();
        } catch (Exception ignored) {}
    }

    private void applySongDeduplication(boolean dedup) {
        searchResultsList.clear();
        searchResultsData.clear();
        if (!dedup) {
            for (DisplayEntry e : rawSearchSongResults) {
                searchResultsList.add(e);
                Map<String, String> row = new HashMap<String, String>();
                row.put("title", e.title);
                row.put("subtitle", e.artist + "  [" + e.quality + "]");
                searchResultsData.add(row);
            }
        } else {
            LinkedHashMap<String, DisplayEntry> bestSongsMap = new LinkedHashMap<String, DisplayEntry>();
            for (DisplayEntry song : rawSearchSongResults) {
                String key = song.title.trim().toLowerCase();
                if (!bestSongsMap.containsKey(key) || song.bitRateNumeric > bestSongsMap.get(key).bitRateNumeric) {
                    bestSongsMap.put(key, song);
                }
            }
            for (DisplayEntry bestSong : bestSongsMap.values()) {
                searchResultsList.add(bestSong);
                Map<String, String> row = new HashMap<String, String>();
                row.put("title", bestSong.title);
                row.put("subtitle", bestSong.artist + "  [" + bestSong.quality + "]");
                searchResultsData.add(row);
            }
        }
        tvSearchResultTitle.setText("搜索: " + lastSearchKeyword + " (" + searchResultsList.size() + " 首)");
        searchResultsAdapter.notifyDataSetChanged();
    }

    // 2. 歌单广场逻辑 (参考图二)
    private void setupSquareSection() {
        spinnerSquarePlatform.setAdapter(new SimpleDarkAdapter(new String[]{"网易云", "酷狗音乐"}));
        spinnerSquareSort.setAdapter(new SimpleDarkAdapter(new String[]{"全部", "最热", "最新", "飙升"}));

        // 渲染顶部标签流
        final String[] squareTags = new String[]{"全部", "华语", "流行", "古风", "摇滚", "民谣", "电子", "轻音乐", "说唱", "ACG"};
        containerSquareTags.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        for (final String t : squareTags) {
            final Button tagBtn = new Button(this);
            tagBtn.setText(t);
            tagBtn.setTextColor(0xFFCBD5E1);
            tagBtn.setTextSize(11);
            tagBtn.setBackgroundResource(R.drawable.bg_btn_default);
            tagBtn.setPadding((int) (10 * density), 0, (int) (10 * density), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (28 * density));
            lp.rightMargin = (int) (6 * density);
            tagBtn.setLayoutParams(lp);
            tagBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    loadSquarePlaylists("wy", t);
                }
            });
            containerSquareTags.addView(tagBtn);
        }

        btnImportPlaylist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptImportPlaylist();
            }
        });

        lvSquarePlaylists.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < squarePlaylistsList.size()) {
                    DisplayEntry pl = squarePlaylistsList.get(position);
                    fetchSquarePlaylistSongs(pl.id, pl.title);
                }
            }
        });
    }

    private void loadSquarePlaylists(final String platform, final String tag) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                ArrayList<LxApiHelper.PlaylistInfo> list = LxApiHelper.fetchPlaylists(platform, tag, 1);
                squarePlaylistsList.clear();
                squarePlaylistsData.clear();
                for (LxApiHelper.PlaylistInfo info : list) {
                    squarePlaylistsList.add(new DisplayEntry(info.id, info.name, info.author, info.author + " | 播放: " + info.playCount, info.coverImg, "公开歌单", false));
                    Map<String, String> row = new HashMap<String, String>();
                    row.put("title", "📑 " + info.name);
                    row.put("subtitle", info.author + (info.playCount.length() > 0 ? (" | 播放量: " + info.playCount) : ""));
                    squarePlaylistsData.add(row);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        squarePlaylistsAdapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    private void fetchSquarePlaylistSongs(final String playlistId, final String playlistName) {
        Toast.makeText(this, "正在载入: " + playlistName, Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                // 歌单通过 search3 或 Subsonic 的 getPlaylist 解析
                final String res = requestApi("search3.view?query=" + URLEncoder.encode(playlistId) + "&songCount=100&" + getAuthParams());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        handleSearchResponse(res, 0);
                        switchPage(PAGE_SEARCH);
                    }
                });
            }
        }).start();
    }

    private void promptImportPlaylist() {
        final EditText input = new EditText(this);
        input.setHint("支持粘贴网易云/酷狗/QQ歌单链接或数字ID");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF777777);
        new AlertDialog.Builder(this)
                .setTitle("导入外部歌单")
                .setView(input)
                .setPositiveButton("立即导入", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String text = input.getText().toString().trim();
                        if (text.length() > 0) {
                            fetchSquarePlaylistSongs(text, "导入歌单");
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 3. 排行榜逻辑 (参考图三)
    private void setupRankingSection() {
        spinnerRankingPlatform.setAdapter(new SimpleDarkAdapter(new String[]{"网易云榜单", "QQ音乐榜单", "酷狗榜单"}));
        spinnerRankingPlatform.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String plat = position == 1 ? "tx" : (position == 2 ? "kg" : "wy");
                loadRankingList(plat);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnRankingPlayAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!rankingSongsList.isEmpty()) {
                    playSongInList(rankingSongsList, rankingSongsList.get(0));
                    Toast.makeText(MainActivity.this, "开始顺序播放榜单", Toast.LENGTH_SHORT).show();
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
    }

    private void loadRankingList(final String platform) {
        final String[] boards = new String[]{"飙升榜", "新歌榜", "热歌榜", "原创榜", "抖音热歌榜", "流行指数榜"};
        containerRankingTabs.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        for (final String b : boards) {
            Button tab = new Button(this);
            tab.setText(b);
            tab.setTextColor(0xFFCBD5E1);
            tab.setTextSize(11);
            tab.setBackgroundResource(R.drawable.bg_btn_default);
            tab.setPadding((int) (10 * density), 0, (int) (10 * density), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (28 * density));
            lp.rightMargin = (int) (6 * density);
            tab.setLayoutParams(lp);
            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tvCurrentBoardName.setText("🔥 " + b);
                    fetchBoardSongs(platform, b);
                }
            });
            containerRankingTabs.addView(tab);
        }
        fetchBoardSongs(platform, "热歌榜");
    }

    private void fetchBoardSongs(final String platform, final String boardName) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String query = platform + ":" + boardName;
                    String jsonStr = requestApi("search3.view?query=" + URLEncoder.encode(query, "UTF-8") + "&songCount=100&" + getAuthParams());
                    final ArrayList<DisplayEntry> songs = new ArrayList<DisplayEntry>();
                    final ArrayList<Map<String, String>> data = new ArrayList<Map<String, String>>();
                    if (jsonStr != null) {
                        JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
                        JSONObject result = root.optJSONObject("searchResult3");
                        if (result != null && result.has("song")) {
                            Object sObj = result.get("song");
                            if (sObj instanceof JSONArray) {
                                JSONArray arr = (JSONArray) sObj;
                                for (int i = 0; i < arr.length(); i++) addSongRow(arr.getJSONObject(i), songs, data);
                            } else if (sObj instanceof JSONObject) {
                                addSongRow((JSONObject) sObj, songs, data);
                            }
                        }
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            rankingSongsList.clear();
                            rankingSongsData.clear();
                            for (int i = 0; i < songs.size(); i++) {
                                rankingSongsList.add(songs.get(i));
                                Map<String, String> row = new HashMap<String, String>();
                                row.put("title", (i + 1) + ". " + songs.get(i).title);
                                row.put("subtitle", songs.get(i).artist + " [" + songs.get(i).quality + "]");
                                rankingSongsData.add(row);
                            }
                            rankingSongsAdapter.notifyDataSetChanged();
                        }
                    });
                } catch (Exception ignored) {}
            }
        }).start();
    }

    // 4. 我的收藏（个人歌单，完全剔除服务器公共榜单）
    private void setupMyFavSection() {
        btnFavBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnFavBack.setVisibility(View.GONE);
                loadMyPersonalPlaylists();
            }
        });

        lvMyPlaylists.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < myPlaylistsList.size()) {
                    DisplayEntry pl = myPlaylistsList.get(position);
                    if ("fav_entry".equals(pl.id)) {
                        fetchServerFavoriteSongs();
                    } else if (pl.isSong) {
                        playSongInList(myPlaylistsList, pl);
                    } else {
                        fetchPlaylistSongs(pl.id, pl.title);
                    }
                }
            }
        });
    }

    private void loadMyPersonalPlaylists() {
        tvFavListTitle.setText("我的自建与收藏歌单");
        btnFavBack.setVisibility(View.GONE);
        myPlaylistsList.clear();
        myPlaylistsData.clear();

        // 1. 我喜欢的音乐
        myPlaylistsList.add(new DisplayEntry("fav_entry", "我喜欢的音乐", "", "已收藏 (" + favSongIds.size() + " 首)", null, "红心", false));
        Map<String, String> favRow = new HashMap<String, String>();
        favRow.put("title", "❤️ 我喜欢的音乐");
        favRow.put("subtitle", "已收藏 (" + favSongIds.size() + " 首)");
        myPlaylistsData.add(favRow);

        // 2. 本地收藏歌单
        myPlaylistsList.add(new DisplayEntry("local_featured", "精选珍藏", "", "本地歌单 (" + featuredSongs.size() + " 首)", null, "本地", false));
        Map<String, String> featRow = new HashMap<String, String>();
        featRow.put("title", "⭐ 精选珍藏");
        featRow.put("subtitle", "本地 (" + featuredSongs.size() + " 首)");
        myPlaylistsData.add(featRow);

        myPlaylistsList.add(new DisplayEntry("local_car", "车载驾驶", "", "本地歌单 (" + carSongs.size() + " 首)", null, "本地", false));
        Map<String, String> carRow = new HashMap<String, String>();
        carRow.put("title", "🚗 车载驾驶");
        carRow.put("subtitle", "本地 (" + carSongs.size() + " 首)");
        myPlaylistsData.add(carRow);

        // 3. 从服务端加载个人歌单（核心：精准过滤只读公共排行榜）
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String jsonStr = requestApi("getPlaylists.view?" + getAuthParams());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (jsonStr != null) {
                            try {
                                JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
                                JSONObject playlistsObj = root.optJSONObject("playlists");
                                if (playlistsObj != null && playlistsObj.has("playlist")) {
                                    Object plObj = playlistsObj.get("playlist");
                                    JSONArray arr = (plObj instanceof JSONArray) ? (JSONArray) plObj : new JSONArray().put(plObj);
                                    for (int i = 0; i < arr.length(); i++) {
                                        JSONObject p = arr.getJSONObject(i);
                                        String name = p.getString("name");
                                        String id = p.getString("id");
                                        int count = p.optInt("songCount", 0);
                                        // 过滤公开排行榜与只读榜单歌单
                                        if (isServerPublicRankingPlaylist(name, p)) {
                                            continue;
                                        }
                                        myPlaylistsList.add(new DisplayEntry(id, name, "", "云端自建 | " + count + " 首歌", null, "自建", false));
                                        Map<String, String> r = new HashMap<String, String>();
                                        r.put("title", "📁 " + name);
                                        r.put("subtitle", count + " 首歌曲 (云端歌单)");
                                        myPlaylistsData.add(r);
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                        myPlaylistsAdapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    private boolean isServerPublicRankingPlaylist(String name, JSONObject p) {
        if (name == null) return false;
        String n = name.trim().toLowerCase();
        // 包含平台榜单标识或者带有公开只读特性的歌单排除在“我的收藏”之外
        return n.contains("榜") || n.contains("top") || n.contains("hot") || n.contains("billboard")
                || n.contains("oricon") || n.startsWith("tx_") || n.startsWith("wy_") || n.startsWith("kg_")
                || p.optBoolean("public", false);
    }

    // 5. 本地音乐管理
    private void setupLocalMusicSection() {
        btnScanLocalMusic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanLocalMusicFiles();
            }
        });

        lvLocalMusic.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < localMusicList.size()) {
                    playSongInList(localMusicList, localMusicList.get(position));
                }
            }
        });
    }

    private void scanLocalMusicFiles() {
        String path = prefs.getString("download_path", getDefaultDownloadPath());
        tvLocalMusicPath.setText("本地目录: " + path);
        localMusicList.clear();
        localMusicData.clear();
        ArrayList<DisplayEntry> localSongs = LocalMusicManager.scanFolder(path);
        for (DisplayEntry e : localSongs) {
            localMusicList.add(e);
            Map<String, String> row = new HashMap<String, String>();
            row.put("title", e.title);
            row.put("subtitle", e.artist + "  [" + e.quality + "]");
            localMusicData.add(row);
        }
        localMusicAdapter.notifyDataSetChanged();
        Toast.makeText(this, "扫描完成，共找到 " + localSongs.size() + " 首本地歌曲", Toast.LENGTH_SHORT).show();
    }

    // 播放逻辑兼容（支持网络 Subsonic 直链与本地本地绝对路径文件）
    private void playSongInList(ArrayList<DisplayEntry> list, DisplayEntry entry) {
        ArrayList<MusicService.SongItem> queue = new ArrayList<MusicService.SongItem>();
        int clickedSongIndex = 0;
        for (int i = 0; i < list.size(); i++) {
            DisplayEntry item = list.get(i);
            if (item.isSong) {
                if (item.id.equals(entry.id)) {
                    clickedSongIndex = queue.size();
                }
                String playUrl;
                if (item.id.startsWith("local_file:")) {
                    playUrl = item.id.substring(11);
                } else {
                    playUrl = buildStreamUrl(item.id);
                }
                queue.add(new MusicService.SongItem(item.id, item.title, item.artist, playUrl, item.coverArt, item.quality));
            }
        }
        MusicService.setQueue(queue, clickedSongIndex, MainActivity.this);
        refreshQueueList();
    }

    // 其他播放控制器、配置加载、唱盘动效等核心辅助方法
    private void setupPlayerControls() {
        View.OnClickListener togglePlayListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_TOGGLE));
            }
        };
        btnPlayPause.setOnClickListener(togglePlayListener);
        btnDetailPlayPause.setOnClickListener(togglePlayListener);

        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_NEXT)); }
        });
        btnDetailNext.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_NEXT)); }
        });

        btnPrev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_PREV)); }
        });
        btnDetailPrev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_PREV)); }
        });

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

        ivBottomCover.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { layoutDetailOverlay.setVisibility(View.VISIBLE); }
        });
        btnCloseDetail.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { layoutDetailOverlay.setVisibility(View.GONE); }
        });

        btnToggleQueue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutQueuePanel.setVisibility(layoutQueuePanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });
        btnCloseQueue.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { layoutQueuePanel.setVisibility(View.GONE); }
        });

        SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    String t = formatTime(progress) + " / " + formatTime(sb.getMax());
                    tvTime.setText(t);
                    tvDetailTime.setText(t);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { isUserSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                isUserSeeking = false;
                Intent intent = new Intent(MainActivity.this, MusicService.class);
                intent.setAction(MusicService.ACTION_SEEK);
                intent.putExtra("position", sb.getProgress());
                startService(intent);
            }
        };
        seekBar.setOnSeekBarChangeListener(seekListener);
        detailSeekBar.setOnSeekBarChangeListener(seekListener);

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveConfig();
                Toast.makeText(MainActivity.this, "配置已保存", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupBitrateSpinners() {
        BitrateSpinnerAdapter adapterConfig = new BitrateSpinnerAdapter(BITRATE_LABELS);
        BitrateSpinnerAdapter adapterDetail = new BitrateSpinnerAdapter(BITRATE_LABELS);
        spinnerConfigBitrate.setAdapter(adapterConfig);
        spinnerDetailBitrate.setAdapter(adapterDetail);
        int initialIndex = getBitrateIndex(getSavedBitrate());
        spinnerConfigBitrate.setSelection(initialIndex);
        spinnerDetailBitrate.setSelection(initialIndex);
    }

    private void setupControlIcons() {
        int darkIconColor = 0xFF10141A;
        int lightIconColor = 0xFFE2E8F0;
        int redIconColor = 0xFFFF6B6B;
        int cyanIconColor = 0xFF00E5FF;
        btnPrev.setImageDrawable(MediaIconHelper.createPreviousIcon(this, 18, lightIconColor));
        btnNext.setImageDrawable(MediaIconHelper.createNextIcon(this, 18, lightIconColor));
        btnPlayPause.setImageDrawable(MediaIconHelper.createPlayIcon(this, 22, darkIconColor));
        btnDetailPrev.setImageDrawable(MediaIconHelper.createPreviousIcon(this, 22, lightIconColor));
        btnDetailNext.setImageDrawable(MediaIconHelper.createNextIcon(this, 22, lightIconColor));
        btnDetailPlayPause.setImageDrawable(MediaIconHelper.createPlayIcon(this, 28, darkIconColor));
        btnExitApp.setImageDrawable(MediaIconHelper.createPowerIcon(this, 18, redIconColor));
        btnDetailExitApp.setImageDrawable(MediaIconHelper.createPowerIcon(this, 18, redIconColor));
        btnOpenEq.setImageDrawable(MediaIconHelper.createEqualizerIcon(this, 18, cyanIconColor));
        btnDetailEq.setImageDrawable(MediaIconHelper.createEqualizerIcon(this, 20, cyanIconColor));
        updateModeIcons(MusicService.getCurrentMode());
    }

    private void updateModeIcons(int mode) {
        int iconColor = 0xFF00E5FF;
        if (mode == MusicService.MODE_SHUFFLE) {
            btnMode.setImageDrawable(MediaIconHelper.createShuffleIcon(this, 18, iconColor));
            btnDetailMode.setImageDrawable(MediaIconHelper.createShuffleIcon(this, 20, iconColor));
        } else if (mode == MusicService.MODE_SINGLE) {
            btnMode.setImageDrawable(MediaIconHelper.createRepeatOneIcon(this, 18, iconColor));
            btnDetailMode.setImageDrawable(MediaIconHelper.createRepeatOneIcon(this, 20, iconColor));
        } else {
            btnMode.setImageDrawable(MediaIconHelper.createRepeatIcon(this, 18, iconColor));
            btnDetailMode.setImageDrawable(MediaIconHelper.createRepeatIcon(this, 20, iconColor));
        }
    }

    private void updatePlayPauseIcons(boolean isPlaying) {
        int darkIconColor = 0xFF10141A;
        if (isPlaying) {
            btnPlayPause.setImageDrawable(MediaIconHelper.createPauseIcon(this, 20, darkIconColor));
            btnDetailPlayPause.setImageDrawable(MediaIconHelper.createPauseIcon(this, 26, darkIconColor));
        } else {
            btnPlayPause.setImageDrawable(MediaIconHelper.createPlayIcon(this, 22, darkIconColor));
            btnDetailPlayPause.setImageDrawable(MediaIconHelper.createPlayIcon(this, 28, darkIconColor));
        }
    }

    private void loadSavedConfig() {
        etServer.setText(prefs.getString("server", "http://192.168.1.100:9527"));
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

    private String buildStreamUrl(String songId) {
        return buildStreamUrl(songId, getSavedBitrate());
    }

    private String buildStreamUrl(String songId, String bitrate) {
        String base = prefs.getString("server", "");
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String u = prefs.getString("user", "");
        String p = prefs.getString("pass", "");
        String bitrateParam = "";
        if ("128".equalsIgnoreCase(bitrate)) bitrateParam = "&maxBitRate=128";
        else if ("192".equalsIgnoreCase(bitrate)) bitrateParam = "&maxBitRate=192";
        else if ("320".equalsIgnoreCase(bitrate)) bitrateParam = "&maxBitRate=320";
        else if ("flac".equalsIgnoreCase(bitrate)) bitrateParam = "&format=flac";

        try {
            return base + "/rest/stream.view?id=" + URLEncoder.encode(songId, "UTF-8") + "&u=" + URLEncoder.encode(u, "UTF-8") + "&p=" + URLEncoder.encode(p, "UTF-8") + "&v=1.12.0&c=RetroSubsonic" + bitrateParam;
        } catch (Exception e) {
            return base + "/rest/stream.view?id=" + songId + "&u=" + u + "&p=" + p + "&v=1.12.0&c=RetroSubsonic" + bitrateParam;
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
        HttpURLConnection conn = null;
        try {
            URL url = new URL(fullUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            if (conn instanceof HttpsURLConnection) {
                ((HttpsURLConnection) conn).setSSLSocketFactory(new TLSSocketFactory());
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void addSongRow(JSONObject s, ArrayList<DisplayEntry> targetList, ArrayList<Map<String, String>> targetData) throws Exception {
        String title = s.getString("title");
        String artist = s.optString("artist", "未知歌手");
        String coverArt = s.optString("coverArt", null);
        int bitRate = s.optInt("bitRate", 0);
        String suffix = s.optString("suffix", "").toUpperCase();
        String quality;
        int bitRateScore = bitRate;
        if (suffix.contains("FLAC") || suffix.contains("WAV")) {
            quality = "FLAC无损";
            bitRateScore = 10000 + bitRate;
        } else if (bitRate > 0) {
            quality = bitRate + "K " + (suffix.length() > 0 ? suffix : "MP3");
            bitRateScore = bitRate;
        } else {
            quality = "320K MP3";
            bitRateScore = 320;
        }
        targetList.add(new DisplayEntry(s.getString("id"), title, artist, artist + " [" + quality + "]", coverArt, quality, true, bitRateScore));
        Map<String, String> row = new HashMap<String, String>();
        row.put("title", title);
        row.put("subtitle", artist + " [" + quality + "]");
        targetData.add(row);
    }

    private void fetchServerFavoriteSongs() {
        btnFavBack.setVisibility(View.VISIBLE);
        tvFavListTitle.setText("❤️ 我喜欢的音乐");
        myPlaylistsList.clear();
        myPlaylistsData.clear();
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
                                    JSONArray arr = (songObj instanceof JSONArray) ? (JSONArray) songObj : new JSONArray().put(songObj);
                                    for (int i = 0; i < arr.length(); i++) {
                                        addSongRow(arr.getJSONObject(i), myPlaylistsList, myPlaylistsData);
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                        myPlaylistsAdapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    private void fetchPlaylistSongs(final String playlistId, final String playlistName) {
        btnFavBack.setVisibility(View.VISIBLE);
        tvFavListTitle.setText(playlistName);
        myPlaylistsList.clear();
        myPlaylistsData.clear();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String jsonStr = requestApi("getPlaylist.view?id=" + playlistId + "&" + getAuthParams());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (jsonStr != null) {
                            try {
                                JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
                                JSONObject playlist = root.getJSONObject("playlist");
                                if (playlist.has("entry")) {
                                    Object entryObj = playlist.get("entry");
                                    JSONArray arr = (entryObj instanceof JSONArray) ? (JSONArray) entryObj : new JSONArray().put(entryObj);
                                    for (int i = 0; i < arr.length(); i++) {
                                        addSongRow(arr.getJSONObject(i), myPlaylistsList, myPlaylistsData);
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                        myPlaylistsAdapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
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

    private void loadFavSet() {
        Set<String> set = prefs.getStringSet("fav_songs_set", new HashSet<String>());
        favSongIds = new HashSet<String>(set);
    }

    private void saveFavSet() {
        prefs.edit().putStringSet("fav_songs_set", favSongIds).commit();
    }

    private boolean isFav(String songId) {
        return songId != null && favSongIds.contains(songId);
    }

    private void updateFavButtonState(String currentPlayingSongId) {
        String targetId = currentPlayingSongId;
        if (targetId == null || targetId.length() == 0) {
            ArrayList<MusicService.SongItem> q = MusicService.getPlaylist();
            int idx = MusicService.getCurrentIndex();
            if (q != null && idx >= 0 && idx < q.size()) targetId = q.get(idx).id;
        }
        boolean fav = isFav(targetId);
        String symbol = fav ? "♥" : "♡";
        if (btnBottomFav != null) btnBottomFav.setText(symbol);
        if (btnDetailFav != null) btnDetailFav.setText(symbol);
    }

    private void syncServerFavoritesQuietly() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String jsonStr = requestApi("getStarred2.view?" + getAuthParams());
                if (jsonStr == null || !jsonStr.contains("\"song\"")) {
                    jsonStr = requestApi("getStarred.view?" + getAuthParams());
                }
                if (jsonStr == null) return;
                try {
                    JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
                    JSONObject starred = root.optJSONObject("starred2");
                    if (starred == null) starred = root.optJSONObject("starred");
                    if (starred != null && starred.has("song")) {
                        favSongIds.clear();
                        Object songObj = starred.get("song");
                        JSONArray arr = (songObj instanceof JSONArray) ? (JSONArray) songObj : new JSONArray().put(songObj);
                        for (int i = 0; i < arr.length(); i++) favSongIds.add(arr.getJSONObject(i).getString("id"));
                        saveFavSet();
                        runOnUiThread(new Runnable() {
                            @Override public void run() { updateFavButtonState(null); }
                        });
                    }
                } catch (Exception ignored) {}
            }
        }).start();
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
                    loadCoverArt(song.coverArtId != null ? song.coverArtId : song.id);
                    loadLyrics(song.id, song.artist, song.title);
                    updateFavButtonState(song.id);
                }
            }
        }
    }

    private void loadLocalPlaylists() {
        featuredSongs.clear();
        carSongs.clear();
        try {
            String fStr = prefs.getString("local_playlist_featured", "[]");
            JSONArray fArr = new JSONArray(fStr);
            for (int i = 0; i < fArr.length(); i++) {
                JSONObject o = fArr.getJSONObject(i);
                featuredSongs.add(new DisplayEntry(o.getString("id"), o.getString("title"), o.optString("artist", "未知歌手"), "", o.optString("coverArt", null), o.optString("quality", "标准"), true));
            }
            String cStr = prefs.getString("local_playlist_car", "[]");
            JSONArray cArr = new JSONArray(cStr);
            for (int i = 0; i < cArr.length(); i++) {
                JSONObject o = cArr.getJSONObject(i);
                carSongs.add(new DisplayEntry(o.getString("id"), o.getString("title"), o.optString("artist", "未知歌手"), "", o.optString("coverArt", null), o.optString("quality", "标准"), true));
            }
        } catch (Exception ignored) {}
    }

    private void updateCacheSizeDisplay() {
        if (tvCacheUsed == null) return;
        long bytes = CacheManager.getUsedCacheBytes(this);
        double mb = bytes / (1024.0 * 1024.0);
        tvCacheUsed.setText(String.format("(已用 %.1f MB)", mb));
    }

    private String getSavedBitrate() { return prefs.getString("default_bitrate", "auto"); }
    private int getBitrateIndex(String val) {
        for (int i = 0; i < BITRATE_VALUES.length; i++) {
            if (BITRATE_VALUES[i].equalsIgnoreCase(val)) return i;
        }
        return 0;
    }
    private String getBitrateDisplay(String val, String originalQuality) {
        if ("128".equalsIgnoreCase(val)) return "128K MP3";
        if ("192".equalsIgnoreCase(val)) return "192K MP3";
        if ("320".equalsIgnoreCase(val)) return "320K MP3";
        if ("flac".equalsIgnoreCase(val)) return "FLAC无损";
        return (originalQuality != null && originalQuality.length() > 0) ? originalQuality : "自动音质";
    }

    private String formatTime(int ms) {
        int seconds = (ms / 1000) % 60;
        int minutes = (ms / (1000 * 60)) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void updateLyricOffsetStatusView() {
        if (tvLyricOffsetStatus != null) {
            double sec = (DlnaManager.isCasting() ? dlnaGlobalLyricOffsetMs : manualLyricOffsetMs) / 1000.0;
            tvLyricOffsetStatus.setText(String.format("%s%.1fs", (sec > 0 ? "+" : ""), sec));
        }
    }

    private void loadLyrics(final String songId, final String artist, final String title) {
        currentSongIdForLyric = songId;
        currentArtistForLyric = artist;
        currentTitleForLyric = title;
        lyricRows.clear();
        layoutLyricsContainer.removeAllViews();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String lyricsText = null;
                try {
                    String res = requestApi("getLyricsBySongId.view?id=" + URLEncoder.encode(songId, "UTF-8") + "&" + getAuthParams());
                    lyricsText = parseLyricsFromJson(res);
                } catch (Throwable ignored) {}
                final String finalLyrics = lyricsText;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        currentLoadedRawLyrics = finalLyrics;
                        if (finalLyrics != null && finalLyrics.trim().length() > 0) {
                            buildLyricsView(finalLyrics);
                        }
                    }
                });
            }
        }).start();
    }

    private String parseLyricsFromJson(String jsonStr) {
        if (jsonStr == null) return null;
        try {
            JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
            if (root.has("lyrics")) {
                Object lyricsObj = root.get("lyrics");
                if (lyricsObj instanceof JSONObject) return ((JSONObject) lyricsObj).optString("content", "");
                if (lyricsObj instanceof String) return (String) lyricsObj;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void buildLyricsView(String rawText) {
        layoutLyricsContainer.removeAllViews();
        lyricRows.clear();
        String[] lines = rawText.split("\n");
        for (String line : lines) {
            line = line.trim();
            int closeBracket = line.indexOf(']');
            if (line.startsWith("[") && closeBracket > 1) {
                String timePart = line.substring(1, closeBracket);
                long timeMs = parseTime(timePart);
                if (timeMs >= 0) {
                    String content = line.substring(closeBracket + 1).trim();
                    lyricRows.add(new LyricRow(timeMs, content));
                }
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
            String[] parts = timeStr.split(":");
            if (parts.length >= 2) {
                long min = Long.parseLong(parts[0]);
                float sec = Float.parseFloat(parts[1]);
                return (long) (min * 60 * 1000 + sec * 1000);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private void updateLyricPosition(int currentPosMs) {
        if (lyricRows.isEmpty()) return;
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
                }
            }
            currentLyricIndex = targetIndex;
            final LyricRow curRow = lyricRows.get(currentLyricIndex);
            if (curRow.view != null) {
                curRow.view.setTextColor(0xFF00E5FF);
                curRow.view.setTextSize(lyricBaseFontSize + 4);
                scrollLyrics.post(new Runnable() {
                    @Override
                    public void run() {
                        int scrollY = curRow.view.getTop() - (scrollLyrics.getHeight() / 2) + (curRow.view.getHeight() / 2);
                        scrollLyrics.smoothScrollTo(0, Math.max(0, scrollY));
                    }
                });
            }
        }
    }

    private void loadCoverArt(final String coverId) {
        if (coverId == null) return;
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
                    if (bmp != null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ivBottomCover.setImageBitmap(bmp);
                                ivSquareCover.setImageBitmap(bmp);
                                ivVinylCircularCover.setImageBitmap(bmp);
                            }
                        });
                    }
                } catch (Throwable ignored) {}
            }
        }).start();
    }

    private void performAppExit() {
        new AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("确定要退出音乐播放器吗？")
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
    protected void onResume() {
        super.onResume();
        registerReceiver(statusReceiver, new IntentFilter(MusicService.BROADCAST_STATUS));
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(statusReceiver); } catch (Exception ignored) {}
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
            tv.setPadding(6, 4, 6, 4);
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

    private class BitrateSpinnerAdapter extends SimpleDarkAdapter {
        BitrateSpinnerAdapter(String[] items) { super(items); }
    }
}
