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
    private static final int PAGE_SEARCH = 0;
    private static final int PAGE_PLAZA = 1;
    private static final int PAGE_RANKING = 2;
    private static final int PAGE_FAV = 3;
    private static final int PAGE_LOCAL = 4;
    private static final int PAGE_SETTINGS = 5;
    private int currentPage = PAGE_SEARCH;

    private static final String[] BITRATE_LABELS = new String[]{"自动", "128K", "192K", "320K", "FLAC"};
    private static final String[] BITRATE_VALUES = new String[]{"auto", "128", "192", "320", "flac"};
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
    private String currentPlazaTag = "";
    private String currentPlazaSort = "最热";
    private int currentPlazaPage = 1;
    private boolean isLoadingPlaza = false;
    private boolean hasMorePlaza = true;
    private boolean isPlazaSearchMode = false;
    private String currentPlazaSearchKeyword = "";
    private boolean isPlazaGridMode = true;
    private TextView footerPlazaLoading;

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

    // 我的收藏
    private TextView tvFavTitle;
    private Button btnCreatePlaylist, btnFavBack;
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
    private Spinner spinnerConfigBitrate, spinnerDetailBitrate;
    private boolean isSpinnersInitializing = true;
    private Button btnClearCache, btnSaveSettings;
    private TextView tvCacheUsed;

    // 底部控制条与全屏详情 Overlay
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
    private String currentSongIdForLyric = null;
    private String currentArtistForLyric = null;
    private String currentTitleForLyric = null;
    private String lastLoadedSongId = "";
    private int lastValidProgressMs = 0;
    private RotateAnimation vinylRotateAnim;
    private Bitmap currentRawCoverBitmap, currentCircularCoverBitmap, currentBottomCoverBitmap;
    private Handler lyricHandler = new Handler();

    // 内存图片缓存 (防 OOM 机制)
    private static LruCache<String, Bitmap> imageMemoryCache;

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
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("subsonic_cfg", MODE_PRIVATE);
        lyricBaseFontSize = prefs.getInt("lyric_font_size", 15);
        isVinylDisplayMode = prefs.getBoolean("is_vinyl_display_mode", true);
        isPlazaGridMode = prefs.getBoolean("is_plaza_grid_mode", true);

        // 初始化内存图片缓存，避免低配设备滑动图片 OOM
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
        fetchServerFavoritesQuietly();
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

        // 广场分页加载 Footer
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
        btnCreatePlaylist = (Button) findViewById(R.id.btn_create_playlist);
        btnFavBack = (Button) findViewById(R.id.btn_fav_back);
        lvFavPlaylists = (ListView) findViewById(R.id.lv_fav_playlists);

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
        etCacheSize = (EditText) findViewById(R.id.et_cache_size);
        btnClearCache = (Button) findViewById(R.id.btn_clear_cache);
        btnSaveSettings = (Button) findViewById(R.id.btn_save_settings);
        tvCacheUsed = (TextView) findViewById(R.id.tv_cache_used);

        // 底部播放条
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

    private void updatePlazaViewModeState() {
        if (isPlazaGridMode) {
            gvPlazaPlaylists.setVisibility(View.VISIBLE);
            lvPlazaPlaylists.setVisibility(View.GONE);
            btnPlazaViewMode.setText("视图:网格");
        } else {
            gvPlazaPlaylists.setVisibility(View.GONE);
            lvPlazaPlaylists.setVisibility(View.VISIBLE);
            btnPlazaViewMode.setText("视图:列表");
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
        } else if (page == PAGE_PLAZA) {
            setupPlazaSortButtons();
            loadPlazaSonglists(true);
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

    // 动态生成歌单广场排序胶囊按钮
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
            sBtn.setPadding((int)(8 * density), 0, (int)(8 * density), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int)(24 * density));
            lp.leftMargin = (int)(4 * density);
            sBtn.setLayoutParams(lp);
            sBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!sName.equals(currentPlazaSort)) {
                        currentPlazaSort = sName;
                        setupPlazaSortButtons();
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

    // 歌单广场加载：支持刷新重载与分页加载更多
    private void loadPlazaSonglists(boolean isRefresh) {
        if (isLoadingPlaza) return;
        btnPlazaBack.setVisibility(View.GONE);
        isPlazaSearchMode = false;
        int pos = spinnerPlazaPlatform.getSelectedItemPosition();
        final String code = LxApiHelper.PLAZA_PLATFORM_CODES[pos >= 0 ? pos : 0];
        final String tag = currentPlazaTag;

        if (isRefresh) {
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

        tvPlazaCurrentTag.setText("当前分类: " + (tag.length() > 0 ? tag : "全部歌单") + " · " + currentPlazaSort + " (第 " + currentPlazaPage + " 页)");
        isLoadingPlaza = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<LxApiHelper.PlaylistInfo> list = LxApiHelper.fetchPlaylists(code, tag, currentPlazaSort, currentPlazaPage);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
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

    // 歌单搜索：支持分页
    private void performPlazaSearch(final String keyword, boolean isRefresh) {
        if (keyword == null || keyword.trim().length() == 0) return;
        if (isLoadingPlaza) return;
        btnPlazaBack.setVisibility(View.VISIBLE);
        isPlazaSearchMode = true;
        currentPlazaSearchKeyword = keyword.trim();

        int pos = spinnerPlazaPlatform.getSelectedItemPosition();
        final String code = LxApiHelper.PLAZA_PLATFORM_CODES[pos >= 0 ? pos : 0];

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
        String code = LxApiHelper.PLAZA_PLATFORM_CODES[pos >= 0 ? pos : 0];
        Map<String, String[]> categories = LxApiHelper.getPresetCategories(code);

        final ArrayList<String> flatTags = new ArrayList<String>();
        flatTags.add("全部");
        for (Map.Entry<String, String[]> entry : categories.entrySet()) {
            flatTags.add("--- " + entry.getKey() + " ---");
            for (String t : entry.getValue()) flatTags.add(t);
        }

        new AlertDialog.Builder(this)
                .setTitle("选择分类 (" + LxApiHelper.PLAZA_PLATFORM_NAMES[pos] + ")")
                .setItems(flatTags.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String selected = flatTags.get(which);
                        if (selected.startsWith("---")) return;
                        currentPlazaTag = "全部".equals(selected) ? "" : selected;
                        btnPlazaCategory.setText(selected + " ▾");
                        loadPlazaSonglists(true);
                    }
                })
                .show();
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

    private void openSonglistDetails(final DisplayEntry playlistEntry) {
        btnPlazaBack.setVisibility(View.VISIBLE);
        tvPlazaCurrentTag.setText("歌单: " + playlistEntry.title);
        plazaPlaylistsList.clear();
        plazaPlaylistsData.clear();
        plazaPlaylistsAdapter.notifyDataSetChanged();
        plazaGridAdapter.notifyDataSetChanged();
        footerPlazaLoading.setText("正在加载歌单内歌曲...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<DisplayEntry> songs = LxApiHelper.fetchPlaylistSongs(playlistEntry.id);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
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
                        plazaGridAdapter.notifyDataSetChanged();
                        footerPlazaLoading.setText("共 " + plazaPlaylistsList.size() + " 首歌曲 (已全部加载)");
                    }
                });
            }
        }).start();
    }

    private void loadLeaderboardBoards() {
        int pos = spinnerRankingPlatform.getSelectedItemPosition();
        final String code = LxApiHelper.PLAZA_PLATFORM_CODES[pos >= 0 ? pos : 0];
        final String[][] presetBoards = LxApiHelper.getPresetLeaderboards(code);

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

        int pos = spinnerRankingPlatform.getSelectedItemPosition();
        final String code = LxApiHelper.PLAZA_PLATFORM_CODES[pos >= 0 ? pos : 0];

        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<DisplayEntry> songs = LxApiHelper.fetchLeaderboardSongs(code, board.id);
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
        fetchServerPlaylistsQuietly();
    }

    private void fetchServerPlaylistsQuietly() {
        final String currentUser = prefs.getString("user", "admin").trim();
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
                            for (int i = 0; i < arr.length(); i++) parseUserPlaylistItem(arr.getJSONObject(i), currentUser);
                        } else if (plObj instanceof JSONObject) {
                            parseUserPlaylistItem((JSONObject) plObj, currentUser);
                        }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (currentPage == PAGE_FAV && currentActiveFavPlaylistId == null) {
                                    showFavAndCustomPlaylists();
                                }
                            }
                        });
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void parseUserPlaylistItem(JSONObject p, String currentUser) throws Exception {
        String name = p.getString("name");
        int count = p.optInt("songCount", 0);
        String id = p.getString("id");
        String nLower = name.trim().toLowerCase();

        if ("我喜欢的音乐".equals(name) || "starred".equals(nLower) || "favorites".equals(nLower) || "favourite".equals(nLower)) return;
        if (name.contains("榜") || name.startsWith("Top") || name.startsWith("TOP") || name.endsWith("榜") || name.contains("热搜") || name.contains("指数")) return;
        if (p.has("owner") && currentUser.length() > 0 && !currentUser.equalsIgnoreCase(p.optString("owner", "").trim())) return;
        if (p.optBoolean("public", false) && p.has("owner") && currentUser.length() > 0 && !currentUser.equalsIgnoreCase(p.optString("owner", "").trim())) return;

        DisplayEntry entry = new DisplayEntry(id, name, "", "歌曲数: " + count + " 首", null, count + " 首", false);
        rawServerUserPlaylists.add(entry);
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

        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isSpinnersInitializing) return;
                String newBitrate = BITRATE_VALUES[position];
                String oldBitrate = getSavedBitrate();
                if (!newBitrate.equals(oldBitrate)) {
                    prefs.edit().putString("default_bitrate", newBitrate).commit();
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
                if (item.localPath != null && item.localPath.length() > 0) {
                    playUrl = "file://" + item.localPath;
                } else if (item.id != null && item.id.startsWith("local_file:")) {
                    playUrl = "file://" + item.id.substring(11);
                } else {
                    playUrl = buildStreamUrl(item.id, getSavedBitrate());
                }
                queue.add(new MusicService.SongItem(item.id, item.title, item.artist, playUrl, item.coverArt, item.quality));
            }
        }
        MusicService.setQueue(queue, clickedSongIndex, MainActivity.this);
        refreshQueueList();
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
                currentPlazaTag = "";
                btnPlazaCategory.setText("全部分类 ▾");
                setupPlazaSortButtons();
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

        // 热门搜索折叠/展开
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

        // 搜索列表点击
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

        // 搜索列表长按（无论是歌手、专辑还是歌曲均支持）
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

        // 歌单广场分类、视图切换、刷新、导入与搜索
        btnPlazaCategory.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showCategoryDialog(); }
        });
        btnPlazaViewMode.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                isPlazaGridMode = !isPlazaGridMode;
                prefs.edit().putBoolean("is_plaza_grid_mode", isPlazaGridMode).commit();
                updatePlazaViewModeState();
            }
        });
        btnPlazaRefresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (isPlazaSearchMode) performPlazaSearch(currentPlazaSearchKeyword, true);
                else loadPlazaSonglists(true);
            }
        });
        btnPlazaImport.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { promptImportPlaylist(); }
        });
        btnPlazaBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { loadPlazaSonglists(true); }
        });
        btnPlazaSearchSubmit.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                performPlazaSearch(etPlazaSearch.getText().toString().trim(), true);
            }
        });

        // 歌单广场列表点击与长按 (ListView)
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

        // 歌单广场网格点击与长按 (GridView)
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

        // 列表 Footer 点击触发加载更多
        footerPlazaLoading.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (hasMorePlaza && !isLoadingPlaza) {
                    if (isPlazaSearchMode) performPlazaSearch(currentPlazaSearchKeyword, false);
                    else loadPlazaSonglists(false);
                }
            }
        });

        // ListView 与 GridView 触底滑动监听加载更多
        AbsListView.OnScrollListener autoScrollLoader = new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int scrollState) {}
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (!isBrowsingArtistOrAlbum && btnPlazaBack.getVisibility() != View.VISIBLE) {
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

        // 排行榜列表与刷新
        btnRankingRefresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { loadLeaderboardBoards(); }
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

        // 收藏
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

        // 本地音乐
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
                if (layoutQueuePanel.getVisibility() == View.VISIBLE) layoutQueuePanel.setVisibility(View.GONE);
                else {
                    refreshQueueList();
                    layoutQueuePanel.setVisibility(View.VISIBLE);
                }
            }
        });
        btnCloseQueue.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { layoutQueuePanel.setVisibility(View.GONE); }
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

    // 歌单广场网格自定义适配器（带异步图片错位校验和内存缓存）
    private class PlazaGridAdapter extends BaseAdapter {
        @Override public int getCount() { return plazaPlaylistsList.size(); }
        @Override public Object getItem(int position) { return plazaPlaylistsList.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            GridHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_plaza_grid, parent, false);
                holder = new GridHolder();
                holder.ivCover = (ImageView) convertView.findViewById(R.id.iv_grid_cover);
                holder.tvPlayCount = (TextView) convertView.findViewById(R.id.tv_grid_playcount);
                holder.tvTitle = (TextView) convertView.findViewById(R.id.tv_grid_title);
                holder.tvAuthor = (TextView) convertView.findViewById(R.id.tv_grid_author);
                convertView.setTag(holder);
            } else {
                holder = (GridHolder) convertView.getTag();
            }

            DisplayEntry item = plazaPlaylistsList.get(position);
            holder.tvTitle.setText(item.title);
            holder.tvAuthor.setText(item.artist != null && item.artist.length() > 0 ? item.artist : "推荐歌单");
            holder.tvPlayCount.setText("播放: " + item.subtitle);

            holder.ivCover.setImageResource(R.drawable.ic_launcher);
            if (item.coverArt != null && item.coverArt.length() > 0) {
                holder.ivCover.setTag(item.coverArt);
                loadAsyncGridCover(item.coverArt, holder.ivCover);
            }
            return convertView;
        }
    }

    private static class GridHolder {
        ImageView ivCover;
        TextView tvPlayCount;
        TextView tvTitle;
        TextView tvAuthor;
    }

    // 异步加载网格图片并写入内存缓存
    private void loadAsyncGridCover(final String urlStr, final ImageView iv) {
        if (imageMemoryCache != null) {
            Bitmap cached = imageMemoryCache.get(urlStr);
            if (cached != null) {
                iv.setImageBitmap(cached);
                return;
            }
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL u = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                    conn.setConnectTimeout(4000);
                    conn.setReadTimeout(5000);
                    if (conn instanceof HttpsURLConnection) {
                        ((HttpsURLConnection) conn).setSSLSocketFactory(new TLSSocketFactory());
                    }
                    InputStream is = conn.getInputStream();
                    final Bitmap bmp = BitmapFactory.decodeStream(is);
                    is.close();
                    conn.disconnect();
                    if (bmp != null) {
                        if (imageMemoryCache != null) imageMemoryCache.put(urlStr, bmp);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (iv.getTag() != null && iv.getTag().equals(urlStr)) {
                                    iv.setImageBitmap(bmp);
                                }
                            }
                        });
                    }
                } catch (Exception ignored) {}
            }
        }).start();
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
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(6, 2, 6, 2);
            tv.setTextColor(0xFF00E5FF);
            tv.setText(items[position] + " ▾");
            return tv;
        }
        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            TextView tv = (convertView instanceof TextView) ? (TextView) convertView : new TextView(MainActivity.this);
            tv.setTextSize(13);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setPadding(24, 18, 24, 18);
            tv.setBackgroundColor(0xFF1E222B);
            tv.setTextColor(0xFFE0E0E0);
            tv.setText(items[position]);
            return tv;
        }
    }
}
