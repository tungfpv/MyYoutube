package com.tungpv.myyoutube;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.FutureTarget;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import at.huber.youtubeExtractor.VideoMeta;
import at.huber.youtubeExtractor.YouTubeExtractor;
import at.huber.youtubeExtractor.YtFile;

public class VideoService extends Service implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener {
    public static final String TAG = "tungfpv";
    private static final String CHANNEL_ID = "video_channel";
    public static final String ACTION_PLAY = "com.tungpv.myyoutube.play";
    public static final String ACTION_PAUSE = "com.tungpv.myyoutube.pause";
    public static final String ACTION_STOP = "com.tungpv.myyoutube.stop";

    private NotificationCompat.Builder notificationBuilder;
    private NotificationManager notificationManager;

    private MediaPlayer mMediaPlayer;
    private YouTubeExtractor mYouTubeExtractor;

    private boolean isPlaying = false;
    private boolean isStopped = false;
    private String mVideoTitle;
    private Bitmap mThumbBitmap;

    private static final int YOUTUBE_ITAG_251 = 251;    // webm - stereo, 48 KHz 160 Kbps (opus)
    private static final int YOUTUBE_ITAG_250 = 250;    // webm - stereo, 48 KHz 64 Kbps (opus)
    private static final int YOUTUBE_ITAG_249 = 249;    // webm - stereo, 48 KHz 48 Kbps (opus)
    private static final int YOUTUBE_ITAG_171 = 171;    // webm - stereo, 48 KHz 128 Kbps (vortis)
    private static final int YOUTUBE_ITAG_141 = 141;    // mp4a - stereo, 44.1 KHz 256 Kbps (aac)
    private static final int YOUTUBE_ITAG_140 = 140;    // mp4a - stereo, 44.1 KHz 128 Kbps (aac)
    private static final int YOUTUBE_ITAG_43 = 43;      // webm - stereo, 44.1 KHz 128 Kbps (vortis)
    private static final int YOUTUBE_ITAG_22 = 22;      // mp4 - stereo, 44.1 KHz 192 Kbps (aac)
    private static final int YOUTUBE_ITAG_18 = 18;      // mp4 - stereo, 44.1 KHz 96 Kbps (aac)
    private static final int YOUTUBE_ITAG_36 = 36;      // mp4 - stereo, 44.1 KHz 32 Kbps (aac)
    private static final int YOUTUBE_ITAG_17 = 17;      // mp4 - stereo, 44.1 KHz 24 Kbps (aac)

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_PLAY);
        intentFilter.addAction(ACTION_PAUSE);
        intentFilter.addAction(ACTION_STOP);
        registerReceiver(notificationReceiver, intentFilter);
        // Khởi tạo MediaPlayer và các tham số khác
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnPreparedListener(this);
        mMediaPlayer.setOnCompletionListener(this);

        mYouTubeExtractor = new YouTubeExtractor(this) {
            @Override
            protected void onExtractionComplete(SparseArray<YtFile> ytFiles, VideoMeta videoMeta) {
                if (ytFiles != null) {
                    YtFile ytFile = getBestStream(ytFiles);
                    String url = ytFile.getUrl();
                    mVideoTitle = videoMeta.getTitle();
                    /*String thumbUrl = videoMeta.getThumbUrl();
                    Glide.with(getApplicationContext())
                            .asBitmap()
                            .load(thumbUrl)
                            .into(new SimpleTarget<Bitmap>() {
                                @Override
                                public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                                    mThumbBitmap = resource;

                                }
                            });*/
                    playVideo(url);
                    Log.i(TAG, "video url " + url);


                }

            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Lấy URL của video từ Intent và bắt đầu phát video
        if (intent != null) {
            String action = intent.getAction();
            if (action != null && action.equals(Intent.ACTION_SEND)) {
                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                handleYoutubeLink(sharedText);
            }
        }
        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "notification_channel_name",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("notification_channel_description");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            getNotificationManager().createNotificationChannel(channel);
        }
    }

    private NotificationManager getNotificationManager() {
        if (notificationManager == null) {
            notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        }
        return notificationManager;
    }

    private void createNotification() {
        PendingIntent pauseIntent = PendingIntent.getBroadcast(
                this,
                0,
                new Intent(ACTION_PAUSE),
                PendingIntent.FLAG_IMMUTABLE);
        PendingIntent playIntent = PendingIntent.getBroadcast(
                this,
                0,
                new Intent(ACTION_PLAY),
                PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stopIntent = PendingIntent.getBroadcast(
                this,
                0,
                new Intent(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Action pauseAction = new NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                getString(R.string.notification_action_pause),
                pauseIntent);
        NotificationCompat.Action playAction = new NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                getString(R.string.notification_action_play),
                playIntent);
        NotificationCompat.Action stopAction = new NotificationCompat.Action(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_action_stop),
                stopIntent);


        notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(mVideoTitle)
                //.setLargeIcon(mThumbBitmap)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(null)
                        .setShowActionsInCompactView(0, 1)
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(stopIntent))
                .addAction(pauseAction)
                .addAction(stopAction)
                .setAutoCancel(true);
        startForeground(1, notificationBuilder.build());
    }

    private BroadcastReceiver notificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case ACTION_PAUSE:
                    isPlaying = false;
                    updateNotification();
                    // pause video
                    pauseVideo();
                    break;
                case ACTION_PLAY:
                    isPlaying = true;
                    updateNotification();
                    // play video
                    resumeVideo();
                    break;
                case ACTION_STOP:
                    isStopped = true;
                    // stop service
                    stopVideo();
                    stopSelf();
                    break;
            }

        }
    };

    @SuppressLint("RestrictedApi")
    private void updateNotification() {
        int playPauseIcon = isPlaying ?
                android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;

        NotificationCompat.Action pauseAction = new NotificationCompat.Action(
                playPauseIcon,
                isPlaying ? getString(R.string.notification_action_pause)
                        : getString(R.string.notification_action_play),
                PendingIntent.getBroadcast(this, 0, new Intent(isPlaying ? ACTION_PAUSE : ACTION_PLAY), PendingIntent.FLAG_IMMUTABLE));

        notificationBuilder.mActions.set(0, pauseAction);
        notificationBuilder.setOngoing(!isStopped);
        getNotificationManager().notify(1, notificationBuilder.build());
    }

    private void playVideo(String videoUrl) {
        // Phát video với URL đã lấy được
        try {
            mMediaPlayer.setDataSource(videoUrl);
            mMediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void pauseVideo() {
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
        }
    }

    private void resumeVideo() {
        if (mMediaPlayer != null && !mMediaPlayer.isPlaying()) {
            mMediaPlayer.start();
        }
    }

    private void stopVideo() {
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            mMediaPlayer.stop();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(notificationReceiver);
        mMediaPlayer.release();
        mYouTubeExtractor = null;
        // Giải phóng các tài nguyên và dừng MediaPlayer
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        Log.i(TAG, "onPrepared");
        mediaPlayer.start();
        isPlaying = true;
        createNotification();

    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        isPlaying = false;
        updateNotification();

    }

    private void handleYoutubeLink(String sharedText) {
        if (sharedText != null) {
            if (sharedText != null) {
                if (sharedText.contains("youtu.be") || sharedText.contains("youtube.com")) {
                    String videoId = sharedText.substring(sharedText.lastIndexOf("/") + 1);
                    String youtubeLink = "https://www.youtube.com/watch?v=" + videoId;
                    mYouTubeExtractor.extract(youtubeLink);
                }
            }
        }
    }

    private YtFile getBestStream(SparseArray<YtFile> ytFiles)
    {
        Log.i(TAG, "ytFiles: " + ytFiles);
        if (ytFiles.get(YOUTUBE_ITAG_141) != null) {
            Log.i(TAG, " gets YOUTUBE_ITAG_141");
            return ytFiles.get(YOUTUBE_ITAG_141);
        } else if (ytFiles.get(YOUTUBE_ITAG_140) != null) {
            Log.i(TAG, " gets YOUTUBE_ITAG_140");
            return ytFiles.get(YOUTUBE_ITAG_140);
        } else if (ytFiles.get(YOUTUBE_ITAG_251) != null) {
            Log.i(TAG, " gets YOUTUBE_ITAG_251");
            return ytFiles.get(YOUTUBE_ITAG_251);
        } else if (ytFiles.get(YOUTUBE_ITAG_250) != null) {
            Log.i(TAG, " gets YOUTUBE_ITAG_250");
            return ytFiles.get(YOUTUBE_ITAG_250);
        } else if (ytFiles.get(YOUTUBE_ITAG_249) != null) {
            Log.i(TAG, " gets YOUTUBE_ITAG_249");
            return ytFiles.get(YOUTUBE_ITAG_249);
        } else if (ytFiles.get(YOUTUBE_ITAG_171) != null) {
            Log.i(TAG, " gets YOUTUBE_ITAG_171");
            return ytFiles.get(YOUTUBE_ITAG_171);
        } else if (ytFiles.get(YOUTUBE_ITAG_18) != null) {
            Log.i(TAG, " gets YOUTUBE_ITAG_18");
            return ytFiles.get(YOUTUBE_ITAG_18);
        } else if (ytFiles.get(YOUTUBE_ITAG_22) != null) {
            Log.i(TAG, " gets YOUTUBE_ITAG_22");
            return ytFiles.get(YOUTUBE_ITAG_22);
        } else if (ytFiles.get(YOUTUBE_ITAG_43) != null) {
            Log.i(TAG, " gets YOUTUBE_ITAG_43");
            return ytFiles.get(YOUTUBE_ITAG_43);
        } else if (ytFiles.get(YOUTUBE_ITAG_36) != null) {
            Log.i(TAG, " gets YOUTUBE_ITAG_36");
            return ytFiles.get(YOUTUBE_ITAG_36);
        }

        Log.i(TAG, " gets YOUTUBE_ITAG_17");
        return ytFiles.get(YOUTUBE_ITAG_17);
    }

}

