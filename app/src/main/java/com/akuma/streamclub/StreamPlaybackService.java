package com.akuma.streamclub;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.session.MediaSession;
import android.os.Build;
import android.os.IBinder;

public class StreamPlaybackService extends Service {

    public static final String ACTION_START = "com.akuma.streamclub.START";
    public static final String ACTION_UPDATE = "com.akuma.streamclub.UPDATE";
    public static final String ACTION_PLAY = "com.akuma.streamclub.PLAY";
    public static final String ACTION_PAUSE = "com.akuma.streamclub.PAUSE";
    public static final String ACTION_OPEN = "com.akuma.streamclub.OPEN";
    public static final String ACTION_STOP = "com.akuma.streamclub.STOP";
    public static final String BROADCAST_CONTROL = "com.akuma.streamclub.CONTROL";
    public static final String EXTRA_CONTROL = "control";
    public static final String EXTRA_PLATFORM = "platform";
    public static final String EXTRA_CHANNEL = "channel";

    private static final String CHANNEL_ID = "akuma_stream_playback";
    private static final int NOTIFICATION_ID = 4017;

    private MediaSession mediaSession;
    private String platform = "Kick";
    private String channel = "danilostorm";
    private boolean paused = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        mediaSession = new MediaSession(this, "AkumaStreamClub");
        mediaSession.setActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START;

        if (intent != null) {
            if (intent.hasExtra(EXTRA_PLATFORM)) {
                platform = intent.getStringExtra(EXTRA_PLATFORM);
            }
            if (intent.hasExtra(EXTRA_CHANNEL)) {
                channel = intent.getStringExtra(EXTRA_CHANNEL);
            }
        }

        if (ACTION_PLAY.equals(action)) {
            paused = false;
            sendControl("play");
        } else if (ACTION_PAUSE.equals(action)) {
            paused = true;
            sendControl("pause");
        } else if (ACTION_OPEN.equals(action)) {
            Intent open = new Intent(this, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(open);
        } else if (ACTION_STOP.equals(action)) {
            sendControl("stop");
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification());
        return START_STICKY;
    }

    private void sendControl(String control) {
        Intent broadcast = new Intent(BROADCAST_CONTROL);
        broadcast.setPackage(getPackageName());
        broadcast.putExtra(EXTRA_CONTROL, control);
        sendBroadcast(broadcast);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Akuma Stream Club",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Controles da live em segundo plano");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private PendingIntent serviceAction(String action, int requestCode) {
        Intent intent = new Intent(this, StreamPlaybackService.class);
        intent.setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, requestCode, intent, flags);
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int pFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) pFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(this, 80, openIntent, pFlags);

        Notification.Action playPause = paused
                ? new Notification.Action.Builder(
                        android.R.drawable.ic_media_play,
                        "Reproduzir",
                        serviceAction(ACTION_PLAY, 81)
                ).build()
                : new Notification.Action.Builder(
                        android.R.drawable.ic_media_pause,
                        "Pausar",
                        serviceAction(ACTION_PAUSE, 82)
                ).build();

        Notification.Action watch = new Notification.Action.Builder(
                android.R.drawable.ic_menu_view,
                "Assistir",
                serviceAction(ACTION_OPEN, 83)
        ).build();

        Notification.Action stop = new Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Fechar",
                serviceAction(ACTION_STOP, 84)
        ).build();

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Akuma Stream Club • " + platform)
                .setContentText("@" + channel + (paused ? " • pausado" : " • áudio em segundo plano"))
                .setContentIntent(open)
                .setOngoing(!paused)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .addAction(playPause)
                .addAction(watch)
                .addAction(stop)
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1))
                .build();
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
