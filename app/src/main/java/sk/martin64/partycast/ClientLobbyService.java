package sk.martin64.partycast;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.palette.graphics.Palette;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.Random;

import partycast.client.ClientLobby;
import partycast.model.Lobby;
import partycast.model.LobbyEventListener;
import partycast.model.LobbyMember;
import partycast.model.RemoteMedia;
import sk.martin64.partycast.ui.UiHelper;
import sk.martin64.partycast.utils.StateLock;

public class ClientLobbyService extends Service implements LobbyEventListener {

    public static final String CHANNEL_ID = "ClientLobbyService";
    public static final String CHANNEL_MESSAGES_ID = "ClientLobbyServiceMsg";

    private ClientLobbyServiceBinder binder = new ClientLobbyServiceBinder() {};
    private ClientLobby lobby;
    private NotificationCompat.Builder notificationBuilder;

    private boolean started;

    @Override
    public void onDestroy() {
        lobby.removeEventListener(this);
        lobby.close();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!started) {
            createNotificationChannel();
            notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_round_device_hub_24)
                    .setColor(0xFFFFFFFF)
                    .setColorized(true)
                    .setShowWhen(false)
                    .setContentTitle("Connecting to remote host...")
                    .setContentText(intent.getStringExtra("server"))
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentIntent(PendingIntent.getActivity(this, 1, new Intent(this, ConnectActivity.class), 0))
                    .addAction(R.drawable.ic_round_close_24, "Disconnect", PendingIntent.getBroadcast(this, 2, new Intent("STOP_CLIENT"), 0))
                    .setPriority(NotificationCompat.PRIORITY_MAX);

            lobby = new ClientLobby(String.format("ws://%s:%s", intent.getStringExtra("server"), ServerLobbyService.SERVER_PORT),
                    intent.getStringExtra("username"), this);
            lobby.connect();

            started = true;
        }

        startForeground(2, notificationBuilder.build());
        return START_STICKY;
    }

    public ClientLobby getLobby() {
        return lobby;
    }

    @Override
    public void onConnected(Lobby lobby) {
        updateNotification();
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
    public void onDisconnect(Lobby lobby, int code, String reason) {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel(CHANNEL_MESSAGES_ID, "PartyCast Client Messages", NotificationManager.IMPORTANCE_HIGH);

            channel.setDescription("Channel for receiving messages from server and displaying them in notifications");
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_MESSAGES_ID)
                .setSmallIcon(R.drawable.ic_round_device_hub_24)
                .setColor(getColor(R.color.colorPrimary))
                .setContentTitle("Disconnected from lobby")
                .setContentText("Reason: " + (reason != null ? reason : "Unknown"))
                .setContentIntent(PendingIntent.getActivity(this, 1, new Intent(this, ConnectActivity.class), 0))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        notificationManager.notify(124 + new Random().nextInt(500), builder.build());

        stopSelf();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void updateNotification() {
        if (notificationBuilder == null)
            return;

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        int size = lobby.getMembers().size()-1;
        notificationBuilder.setSubText(String.format("%s (%s)", lobby.getTitle(), size));
        notificationBuilder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle());

        if (lobby.getPlayerState() == Lobby.PLAYBACK_READY) {
            notificationBuilder.setContentTitle("");
            notificationBuilder.setContentText("");
            notificationBuilder.setLargeIcon(null);
        } else {
            RemoteMedia nowPlaying = lobby.getLooper().getCurrentQueue().getCurrentlyPlaying();
            if (nowPlaying == null) {
                Log.w("CLService", "ClientLobby claims playback state == playing/paused but currently playing media is null; skipping notification update");
                return;
            }

            StateLock<RemoteMedia> nowPlayingLock = new StateLock<>(nowPlaying);

            UiHelper.runOnUiCompact(() -> {
                Glide.with(this)
                        .asBitmap()
                        .centerCrop()
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
                                            notificationManager.notify(2, notificationBuilder.build());
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

        notificationManager.notify(2, notificationBuilder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel(CHANNEL_ID, "PartyCast Client", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Background service for keeping PartyCast client connection alive.");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public class ClientLobbyServiceBinder extends Binder {
        public ClientLobbyService getService() {
            return ClientLobbyService.this;
        }
    }
}
