package sk.martin64.partycast;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.Random;

import sk.martin64.partycast.client.ClientLobby;
import sk.martin64.partycast.core.Lobby;
import sk.martin64.partycast.core.LobbyEventListener;
import sk.martin64.partycast.core.LobbyMember;
import sk.martin64.partycast.core.RemoteMedia;

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
                    .setColor(getColor(R.color.colorPrimary))
                    .setShowWhen(false)
                    .setContentTitle("Connecting to remote host...")
                    .setContentText(intent.getStringExtra("server"))
                    .setContentIntent(PendingIntent.getActivity(this, 1, new Intent(this, ConnectActivity.class), 0))
                    .addAction(R.mipmap.ic_launcher, "Disconnect", PendingIntent.getBroadcast(this, 2, new Intent("STOP_CLIENT"), 0))
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
        int size = lobby.getMembers().size()-1;
        notificationBuilder.setSubText(String.format("%s (%s)", lobby.getTitle(), size));
        if (lobby.getPlayerState() == Lobby.PLAYBACK_READY) {
            notificationBuilder.setContentTitle("");
            notificationBuilder.setContentText("");
        } else {
            RemoteMedia nowPlaying = lobby.getLooper().getCurrentQueue().getCurrentlyPlaying();

            notificationBuilder.setContentTitle(nowPlaying.getTitle());
            notificationBuilder.setContentText(nowPlaying.getArtist());
        }

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
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
