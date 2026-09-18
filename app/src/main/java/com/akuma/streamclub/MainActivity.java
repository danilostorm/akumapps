package com.akuma.streamclub;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String PREFS = "akuma_stream_prefs";
    private static final String MODE_AUTO = "auto";
    private static final String MODE_KICK = "kick";
    private static final String MODE_TWITCH = "twitch";

    private WebView player;
    private FrameLayout playerShell;
    private LinearLayout audioCard;
    private LinearLayout header;
    private LinearLayout statusCard;
    private Button btnVideo;
    private Button btnRefresh;
    private TextView platformBadge;
    private TextView channelName;
    private TextView statusText;
    private TextView liveBadge;
    private ScrollView pageScroll;

    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();

    private String currentMode = MODE_AUTO;
    private String currentPlatform = MODE_KICK;
    private String loadedPlatform = "";
    private String loadedChannel = "";
    private boolean videoMode = false;
    private boolean isInPip = false;
    private boolean customFullscreen = false;
    private FrameLayout customContainer;
    private WebChromeClient.CustomViewCallback customCallback;

    private final Runnable liveCheckTask = new Runnable() {
        @Override
        public void run() {
            checkLiveStatus(false);
            handler.postDelayed(this, 30000);
        }
    };

    private final BroadcastReceiver controlReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String control = intent.getStringExtra(StreamPlaybackService.EXTRA_CONTROL);
            if ("pause".equals(control)) pausePlayback();
            if ("play".equals(control)) resumePlayback();
            if ("stop".equals(control)) stopPlayback();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        bindViews();
        configureWebView();
        registerControls();
        registerControlReceiver();
        requestNotificationPermission();

        currentMode = prefs.getString("mode", MODE_AUTO);
        videoMode = prefs.getBoolean("start_video", false);

        String initial = MODE_TWITCH.equals(currentMode) ? MODE_TWITCH : MODE_KICK;
        switchPlatform(initial, true);
        setVideoMode(videoMode);

        checkLiveStatus(true);
        handler.postDelayed(liveCheckTask, 30000);
    }

    private void bindViews() {
        player = findViewById(R.id.player_webview);
        playerShell = findViewById(R.id.player_shell);
        audioCard = findViewById(R.id.audio_card);
        header = findViewById(R.id.header);
        statusCard = findViewById(R.id.status_card);
        btnVideo = findViewById(R.id.btn_video);
        btnRefresh = findViewById(R.id.btn_refresh);
        platformBadge = findViewById(R.id.platform_badge);
        channelName = findViewById(R.id.channel_name);
        statusText = findViewById(R.id.status_text);
        liveBadge = findViewById(R.id.live_badge);
        pageScroll = findViewById(R.id.page_scroll);

        findViewById(R.id.btn_settings).setOnClickListener(v -> showSettings());
        audioCard.setOnClickListener(v -> setVideoMode(true));
    }

    private void registerControls() {
        btnVideo.setOnClickListener(v -> setVideoMode(!videoMode));
        btnRefresh.setOnClickListener(v -> {
            forceAutoplay();
            checkLiveStatus(true);
        });
    }

    private void configureWebView() {
        WebSettings settings = player.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(settings.getUserAgentString() + " " + BuildConfig.APP_USER_AGENT);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(player, true);

        player.setBackgroundColor(Color.BLACK);
        player.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        player.setKeepScreenOn(false);

        player.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri uri = request.getUrl();
                String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
                if (host.contains("kick.com") || host.contains("twitch.tv") || host.equals("akumstream.app")) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {}
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                forceAutoplay();
                handler.postDelayed(() -> forceAutoplay(), 700);
                handler.postDelayed(() -> forceAutoplay(), 1800);
                handler.postDelayed(() -> forceAutoplay(), 3500);
            }
        });

        player.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                showCustomFullscreen(view, callback);
            }

            @Override
            public void onHideCustomView() {
                hideCustomFullscreen();
            }
        });
    }

    private String kickChannel() {
        return sanitizeChannel(prefs.getString("kick_channel", BuildConfig.KICK_DEFAULT_CHANNEL));
    }

    private String twitchChannel() {
        return sanitizeChannel(prefs.getString("twitch_channel", BuildConfig.TWITCH_DEFAULT_CHANNEL));
    }

    private String sanitizeChannel(String value) {
        if (value == null) return "danilostorm";
        value = value.trim().replace("@", "");
        value = value.replaceAll("[^a-zA-Z0-9_-]", "");
        return value.isEmpty() ? "danilostorm" : value;
    }

    private void switchPlatform(String platform, boolean force) {
        String channel = MODE_TWITCH.equals(platform) ? twitchChannel() : kickChannel();

        if (!force && platform.equals(loadedPlatform) && channel.equals(loadedChannel)) {
            updatePlatformUi(platform, channel);
            forceAutoplay();
            return;
        }

        currentPlatform = platform;
        loadedPlatform = platform;
        loadedChannel = channel;

        updatePlatformUi(platform, channel);
        loadPlayer(platform, channel);
        startOrUpdateService(false);
    }

    private void loadPlayer(String platform, String channel) {
        player.onResume();

        if (MODE_TWITCH.equals(platform)) {
            String safeChannel = channel.replace("'", "");
            String html = "<!doctype html><html><head>"
                    + "<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>"
                    + "<style>html,body,#twitch{margin:0;width:100%;height:100%;background:#000;overflow:hidden}</style>"
                    + "</head><body><div id='twitch'></div>"
                    + "<script src='https://player.twitch.tv/js/embed/v1.js'></script>"
                    + "<script>"
                    + "function boot(){try{"
                    + "window.akumaPlayer=new Twitch.Player('twitch',{channel:'" + safeChannel + "',width:'100%',height:'100%',autoplay:true,muted:false,parent:['akumstream.app']});"
                    + "window.akumaPlayer.addEventListener(Twitch.Player.READY,function(){try{window.akumaPlayer.setMuted(false);window.akumaPlayer.setVolume(1);window.akumaPlayer.play();}catch(e){}});"
                    + "}catch(e){setTimeout(boot,500);}}"
                    + "boot();"
                    + "</script></body></html>";
            player.loadDataWithBaseURL("https://akumstream.app/", html, "text/html", "UTF-8", null);
        } else {
            String quality = prefs.getString("quality", "160p");
            String url = "https://player.kick.com/" + Uri.encode(channel)
                    + "?autoplay=true&muted=false&quality=" + Uri.encode(quality)
                    + "&parent=akumstream.app";
            player.loadUrl(url);
        }
    }

    private void forceAutoplay() {
        if (player == null) return;
        String js = "(function(){try{"
                + "if(window.akumaPlayer){try{window.akumaPlayer.setMuted(false);window.akumaPlayer.setVolume(1);window.akumaPlayer.play();}catch(e){}}"
                + "var v=document.querySelector('video');"
                + "if(v){v.muted=false;v.volume=1;var p=v.play();if(p&&p.catch){p.catch(function(){});}}"
                + "if(v&&v.paused){var b=document.querySelector('button[aria-label*=Play],button[aria-label*=play],[data-testid*=play]');if(b){try{b.click();}catch(e){}}}"
                + "}catch(e){}})();";
        player.evaluateJavascript(js, null);
    }

    private void updatePlatformUi(String platform, String channel) {
        boolean twitch = MODE_TWITCH.equals(platform);
        platformBadge.setText(twitch ? "TWITCH" : "KICK");
        platformBadge.setTextColor(getColor(twitch ? R.color.twitch_purple : R.color.kick_green));
        channelName.setText("@" + channel);
        statusText.setText("Conectando…");
    }

    private void setVideoMode(boolean enabled) {
        videoMode = enabled;
        prefs.edit().putBoolean("last_video_mode", enabled).apply();

        audioCard.setVisibility(enabled ? View.GONE : View.VISIBLE);
        player.setAlpha(enabled ? 1f : 0.02f);
        btnVideo.setText(enabled ? "Ouvir em segundo plano" : "Assistir live");

        if (enabled) forceAutoplay();
    }

    private void checkLiveStatus(boolean immediateUi) {
        if (!isOnline()) {
            statusText.setText("Sem internet");
            return;
        }
        if (immediateUi) statusText.setText("Conectando…");

        final String kick = kickChannel();
        final String twitch = twitchChannel();

        networkExecutor.submit(() -> {
            boolean kickLive = isKickLive(kick);
            boolean twitchLive = isTwitchLive(twitch);
            runOnUiThread(() -> applyLiveResult(kickLive, twitchLive));
        });
    }

    private void applyLiveResult(boolean kickLive, boolean twitchLive) {
        boolean anyLive = kickLive || twitchLive;
        liveBadge.setVisibility(anyLive ? View.VISIBLE : View.INVISIBLE);

        if (MODE_AUTO.equals(currentMode)) {
            boolean returnKick = prefs.getBoolean("return_kick", true);
            if (kickLive && (returnKick || !twitchLive)) {
                switchPlatform(MODE_KICK, false);
            } else if (twitchLive) {
                switchPlatform(MODE_TWITCH, false);
            }
        } else if (MODE_KICK.equals(currentMode) && !MODE_KICK.equals(currentPlatform)) {
            switchPlatform(MODE_KICK, false);
        } else if (MODE_TWITCH.equals(currentMode) && !MODE_TWITCH.equals(currentPlatform)) {
            switchPlatform(MODE_TWITCH, false);
        }

        boolean currentLive = MODE_TWITCH.equals(currentPlatform) ? twitchLive : kickLive;

        if (currentLive) {
            statusText.setText("Ao vivo agora");
            forceAutoplay();
        } else if (anyLive && MODE_AUTO.equals(currentMode)) {
            statusText.setText("Trocando transmissão…");
        } else {
            statusText.setText("Offline no momento");
        }
    }

    private boolean isKickLive(String channel) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://kick.com/api/v2/channels/" + channel + "/livestream");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3500);
            connection.setReadTimeout(3500);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", BuildConfig.APP_USER_AGENT);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return false;

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
            reader.close();

            String json = body.toString().replace(" ", "");
            return !json.isEmpty() && !json.equals("null") && !json.equals("{}")
                    && !json.equals("[]") && !json.contains("\"data\":null");
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private boolean isTwitchLive(String channel) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://static-cdn.jtvnw.net/previews-ttv/live_user_"
                    + channel + "-320x180.jpg");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3500);
            connection.setReadTimeout(3500);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", BuildConfig.APP_USER_AGENT);

            int code = connection.getResponseCode();
            String location = connection.getHeaderField("Location");
            if (code >= 200 && code < 300) return true;
            return code >= 300 && code < 400
                    && location != null
                    && !location.contains("404_preview");
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private boolean isOnline() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return true;
        Network network = manager.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = manager.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void pausePlayback() {
        player.onPause();
        player.evaluateJavascript("(function(){try{if(window.akumaPlayer){window.akumaPlayer.pause();}var v=document.querySelector('video');if(v)v.pause();}catch(e){}})();", null);
        statusText.setText("Pausado");
        startOrUpdateService(true);
    }

    private void resumePlayback() {
        player.onResume();
        forceAutoplay();
        handler.postDelayed(() -> forceAutoplay(), 600);
        statusText.setText("Conectando…");
        startOrUpdateService(false);
    }

    private void stopPlayback() {
        player.loadUrl("about:blank");
        statusText.setText("Pausado");
    }

    private void startOrUpdateService(boolean paused) {
        Intent service = new Intent(this, StreamPlaybackService.class);
        service.setAction(StreamPlaybackService.ACTION_UPDATE);
        service.putExtra(StreamPlaybackService.EXTRA_PLATFORM,
                MODE_TWITCH.equals(currentPlatform) ? "Twitch" : "Kick");
        service.putExtra(StreamPlaybackService.EXTRA_CHANNEL,
                MODE_TWITCH.equals(currentPlatform) ? twitchChannel() : kickChannel());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }

        if (paused) {
            Intent pause = new Intent(this, StreamPlaybackService.class);
            pause.setAction(StreamPlaybackService.ACTION_PAUSE);
            startService(pause);
        }
    }

    private void showSettings() {
        int pad = dp(18);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, dp(4), pad, dp(4));

        EditText kick = new EditText(this);
        kick.setHint("Canal da Kick");
        kick.setText(kickChannel());

        EditText twitch = new EditText(this);
        twitch.setHint("Canal da Twitch");
        twitch.setText(twitchChannel());

        TextView sourceLabel = new TextView(this);
        sourceLabel.setText("Reprodução");
        sourceLabel.setPadding(0, dp(12), 0, dp(4));

        Spinner source = new Spinner(this);
        String[] modes = new String[]{"Automático", "Somente Kick", "Somente Twitch"};
        source.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes));
        source.setSelection(MODE_KICK.equals(currentMode) ? 1 : MODE_TWITCH.equals(currentMode) ? 2 : 0);

        CheckBox returnKick = new CheckBox(this);
        returnKick.setText("Voltar para a Kick quando ela estiver ao vivo");
        returnKick.setChecked(prefs.getBoolean("return_kick", true));

        CheckBox startVideo = new CheckBox(this);
        startVideo.setText("Abrir já com o vídeo");
        startVideo.setChecked(prefs.getBoolean("start_video", false));

        CheckBox autoPip = new CheckBox(this);
        autoPip.setText("Mini player ao sair do app");
        autoPip.setChecked(prefs.getBoolean("auto_pip", true));

        TextView qualityLabel = new TextView(this);
        qualityLabel.setText("Qualidade na Kick");
        qualityLabel.setPadding(0, dp(12), 0, dp(4));

        Spinner quality = new Spinner(this);
        String[] qualities = new String[]{"160p", "360p", "720p", "auto"};
        quality.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, qualities));
        String savedQuality = prefs.getString("quality", "160p");
        for (int i = 0; i < qualities.length; i++) {
            if (qualities[i].equals(savedQuality)) quality.setSelection(i);
        }

        content.addView(kick);
        content.addView(twitch);
        content.addView(sourceLabel);
        content.addView(source);
        content.addView(returnKick);
        content.addView(startVideo);
        content.addView(autoPip);
        content.addView(qualityLabel);
        content.addView(quality);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);

        new AlertDialog.Builder(this)
                .setTitle("Configurações")
                .setView(scroll)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", (dialog, which) -> {
                    int modePosition = source.getSelectedItemPosition();
                    currentMode = modePosition == 1 ? MODE_KICK : modePosition == 2 ? MODE_TWITCH : MODE_AUTO;

                    prefs.edit()
                            .putString("kick_channel", sanitizeChannel(kick.getText().toString()))
                            .putString("twitch_channel", sanitizeChannel(twitch.getText().toString()))
                            .putString("mode", currentMode)
                            .putBoolean("return_kick", returnKick.isChecked())
                            .putBoolean("start_video", startVideo.isChecked())
                            .putBoolean("auto_pip", autoPip.isChecked())
                            .putString("quality", qualities[quality.getSelectedItemPosition()])
                            .apply();

                    loadedPlatform = "";
                    loadedChannel = "";

                    if (MODE_TWITCH.equals(currentMode)) {
                        switchPlatform(MODE_TWITCH, true);
                    } else {
                        switchPlatform(MODE_KICK, true);
                    }
                    checkLiveStatus(true);
                })
                .show();
    }

    private void registerControlReceiver() {
        IntentFilter filter = new IntentFilter(StreamPlaybackService.BROADCAST_CONTROL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(controlReceiver, filter);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 700);
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (videoMode && prefs.getBoolean("auto_pip", true) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                PictureInPictureParams params = new PictureInPictureParams.Builder()
                        .setAspectRatio(new Rational(16, 9))
                        .build();
                enterPictureInPictureMode(params);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        isInPip = isInPictureInPictureMode;
        setChromeVisible(!isInPictureInPictureMode);
        audioCard.setVisibility(isInPictureInPictureMode ? View.GONE : (!videoMode ? View.VISIBLE : View.GONE));
        player.setAlpha(1f);
    }

    private void setChromeVisible(boolean visible) {
        int value = visible ? View.VISIBLE : View.GONE;
        header.setVisibility(value);
        statusCard.setVisibility(value);
        btnRefresh.setVisibility(value);
        btnVideo.setVisibility(value);
    }

    private void showCustomFullscreen(View view, WebChromeClient.CustomViewCallback callback) {
        if (customFullscreen) {
            callback.onCustomViewHidden();
            return;
        }

        customFullscreen = true;
        customCallback = callback;

        customContainer = new FrameLayout(this);
        customContainer.setBackgroundColor(Color.BLACK);
        customContainer.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        addContentView(customContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        pageScroll.setVisibility(View.GONE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        setImmersiveMode(true);
    }

    private void hideCustomFullscreen() {
        if (!customFullscreen) return;
        customFullscreen = false;

        if (customContainer != null) {
            customContainer.removeAllViews();
            ViewGroup parent = (ViewGroup) customContainer.getParent();
            if (parent != null) parent.removeView(customContainer);
            customContainer = null;
        }

        pageScroll.setVisibility(View.VISIBLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        setImmersiveMode(false);

        if (customCallback != null) {
            customCallback.onCustomViewHidden();
            customCallback = null;
        }
    }

    private void setImmersiveMode(boolean immersive) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (immersive) {
                    controller.hide(WindowInsets.Type.systemBars());
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    );
                } else {
                    controller.show(WindowInsets.Type.systemBars());
                }
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(immersive
                    ? View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    : View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (customFullscreen) {
            hideCustomFullscreen();
            return;
        }
        if (videoMode && !isInPip) {
            setVideoMode(false);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        networkExecutor.shutdownNow();

        try {
            unregisterReceiver(controlReceiver);
        } catch (Exception ignored) {}

        if (player != null) {
            player.stopLoading();
            player.loadUrl("about:blank");
            player.setWebChromeClient(null);
            player.setWebViewClient(null);
            player.destroy();
        }

        super.onDestroy();
    }
}
