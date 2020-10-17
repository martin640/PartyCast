package sk.martin64.partycast;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.audiofx.AudioEffect;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.palette.graphics.Palette;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import partycast.model.Lobby;
import partycast.model.LobbyEventListener;
import partycast.model.LobbyMember;
import partycast.model.RemoteMedia;
import partycast.server.ServerLobby;
import sk.martin64.partycast.androidserver.AndroidServerLobby;
import sk.martin64.partycast.ui.UiHelper;
import sk.martin64.partycast.utils.Callback;
import sk.martin64.partycast.utils.StateLock;

public class ServerLobbyService extends Service implements LobbyEventListener {

    public static final String CHANNEL_ID = "ServerLobbyService";
    public static final int SERVER_PORT = 10784;
    public static final int ARTWORK_SERVER_PORT = 10785;

    private ServerLobbyServiceBinder binder = new ServerLobbyServiceBinder();
    private AndroidServerLobby lobby;
    private NotificationCompat.Builder notificationBuilder;

    private ExoPlayer player;

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("STOP_SERVER")) {
                lobby.stop();
                System.err.println("Stopped ServerLobby");
                stopSelf();
                stopForeground(true);
            } else if (intent.getAction().equals("AC_PAUSE")) {
                if (lobby.getPlayerState() == Lobby.PLAYBACK_PLAYING) {
                    lobby.getLooper().pause(null);
                } else {
                    lobby.getLooper().play(null);
                }
            } else if (intent.getAction().equals("AC_SKIP")) {
                lobby.getLooper().skip(null);
            }
        }
    };

    public ServerLobby getLobby() {
        return lobby;
    }

    public static String pickName(String preferred) {
        if (!TextUtils.isEmpty(preferred)) return preferred;

        String serverName = null;
        try {
            BluetoothAdapter myDevice = BluetoothAdapter.getDefaultAdapter();
            serverName = myDevice.getName();
        } catch (Exception ignored) {
        }

        if (!TextUtils.isEmpty(serverName)) return serverName;

        return Build.MODEL;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        player = new SimpleExoPlayer.Builder(this).build();

        SharedPreferences savedInstance = getSharedPreferences("si", MODE_PRIVATE);

        lobby = new AndroidServerLobby(pickName(savedInstance.getString("last_server_name", null)),
                SERVER_PORT, savedInstance.getString("last_name", "Admin"), this,
                player, new DefaultDataSourceFactory(this, "PartyCast"),
                ARTWORK_SERVER_PORT, getContentResolver()) {

            @Override
            public void changeTitle(String newName, Callback<Lobby> callback) {
                super.changeTitle(newName, callback);
                savedInstance.edit()
                        .putString("last_server_name", newName)
                        .apply();
            }
        };

        registerReceiver(receiver, new IntentFilter("STOP_SERVER"));
        registerReceiver(receiver, new IntentFilter("AC_PAUSE"));
        registerReceiver(receiver, new IntentFilter("AC_SKIP"));

        createNotificationChannel();
        notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_round_device_hub_24)
                .setColor(0xFFFFFFFF)
                .setColorized(true)
                .setShowWhen(false)
                .setSubText(String.format("%s (0)", lobby.getTitle()))
                .setContentTitle("")
                .setContentText("")
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(PendingIntent.getActivity(this, 1, new Intent(this, ConnectActivity.class), 0))
                .addAction(R.drawable.ic_round_close_24, "Stop server", PendingIntent.getBroadcast(this, 2, new Intent("STOP_SERVER"), 0))
                .addAction(R.drawable.ic_round_play_arrow_24, "Play/Pause", PendingIntent.getBroadcast(this, 3, new Intent("AC_PAUSE"), 0))
                .addAction(R.drawable.ic_round_skip_next_24, "Skip", PendingIntent.getBroadcast(this, 4, new Intent("AC_SKIP"), 0))
                .setPriority(NotificationCompat.PRIORITY_MAX);
    }

    private void updateNotification() {
        if (notificationBuilder == null)
            return;

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        int size = lobby.getMembers().size() - 1;
        notificationBuilder.setSubText(String.format("%s (%s)", lobby.getTitle(), size));

        notificationBuilder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle());

        notificationBuilder.mActions.set(1, new NotificationCompat.Action((lobby.getPlayerState() == Lobby.PLAYBACK_PLAYING) ?
                R.drawable.ic_round_pause_24 : R.drawable.ic_round_play_arrow_24, "Play/Pause",
                PendingIntent.getBroadcast(this, 3, new Intent("AC_PAUSE"), 0)));

        if (lobby.getPlayerState() == Lobby.PLAYBACK_READY) {
            notificationBuilder.setContentTitle("");
            notificationBuilder.setContentText("");
            notificationBuilder.setLargeIcon(null);
        } else {
            RemoteMedia nowPlaying = lobby.getLooper().getCurrentQueue().getCurrentlyPlaying();
            if (nowPlaying == null) {
                Log.w("SLService", "ServerLobby claims playback state == playing/paused but currently playing media is null; skipping notification update");
                return;
            }
            openAudioFx();

            StateLock<RemoteMedia> nowPlayingLock = new StateLock<>(nowPlaying);

            UiHelper.runOnUiCompact(() -> {
                Glide.with(this)
                        .asBitmap()
                        .load(nowPlaying.getArtwork())
                        .error(R.drawable.ic_no_artwork)
                        .into(new CustomTarget<Bitmap>() {
                            @Override
                            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                                Palette.from(resource).generate(palette -> UiHelper.runOnUiCompact(() -> {
                                    try {
                                        RemoteMedia np = lobby.getLooper().getCurrentQueue().getCurrentlyPlaying();
                                        if (nowPlayingLock.verify(np)) {
                                            notificationBuilder.setColor(palette != null ?palette.getDominantColor(0xFFFFFFFF) : 0xFFFFFFFF);
                                            notificationBuilder.setLargeIcon(resource);
                                            notificationManager.notify(1, notificationBuilder.build());
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }));
                            }

                            @Override
                            public void onLoadCleared(@Nullable Drawable placeholder) { }
                        });
            });

            notificationBuilder.setContentTitle(nowPlaying.getTitle());
            notificationBuilder.setContentText(nowPlaying.getArtist());
        }

        notificationManager.notify(1, notificationBuilder.build());
    }

    public void openAudioFx() {
        Intent i = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
        i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, lobby.getMediaSessionId());
        this.sendBroadcast(i);
    }

    public void closeAudioFx() {
        Intent k = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        k.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
        k.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, lobby.getMediaSessionId());
        this.sendBroadcast(k);
    }

    @Override
    public void onUserJoined(Lobby lobby, LobbyMember member) {
        updateNotification();
    }

    @Override
    public void onUserLeft(Lobby lobby, LobbyMember member) {
        updateNotification();
    }

    @Override
    public void onUserUpdated(Lobby lobby, LobbyMember member) {
        updateNotification();
    }

    @Override
    public void onLobbyStateChanged(Lobby lobby) {
        updateNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, notificationBuilder.build());
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        lobby.stop();
        lobby.removeEventListener(this);
        System.err.println("Stopped ServerLobby");
        unregisterReceiver(receiver);
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "PartyCast Server", importance);
            channel.setDescription("Background service for keeping PartyCast server alive.");
            channel.setImportance(NotificationManager.IMPORTANCE_LOW);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }


    public class ServerLobbyServiceBinder extends Binder {
        public ServerLobbyService getService() {
            return ServerLobbyService.this;
        }
    }
}
