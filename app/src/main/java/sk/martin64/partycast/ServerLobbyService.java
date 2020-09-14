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
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import sk.martin64.partycast.core.Lobby;
import sk.martin64.partycast.core.LobbyEventListener;
import sk.martin64.partycast.core.LobbyMember;
import sk.martin64.partycast.core.RemoteMedia;
import sk.martin64.partycast.server.ServerLobby;

public class ServerLobbyService extends Service implements LobbyEventListener {

    public static final String CHANNEL_ID = "ServerLobbyService";
    public static final int SERVER_PORT = 10784;
    public static final int ARTWORK_SERVER_PORT = 10785;

    private ServerLobbyServiceBinder binder = new ServerLobbyServiceBinder();
    private ServerLobby lobby;
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
            }
        }
    };

    public ServerLobby getLobby() {
        return lobby;
    }

    public static String pickName() {
        BluetoothAdapter myDevice = BluetoothAdapter.getDefaultAdapter();
        String serverName = myDevice.getName();

        if (TextUtils.isEmpty(serverName)) serverName = Build.MODEL;

        return serverName;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        player = new SimpleExoPlayer.Builder(this).build();

        lobby = new ServerLobby(pickName(),
                SERVER_PORT, player, new DefaultDataSourceFactory(this, "PartyCast"),
                ARTWORK_SERVER_PORT, getContentResolver(),
                this);

        registerReceiver(receiver, new IntentFilter("STOP_SERVER"));

        createNotificationChannel();
        notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_round_device_hub_24)
                .setColor(getColor(R.color.colorAccent))
                .setShowWhen(false)
                .setSubText(String.format("%s (0)", lobby.getTitle()))
                .setContentTitle("")
                .setContentText("")
                .setContentIntent(PendingIntent.getActivity(this, 1, new Intent(this, ConnectActivity.class), 0))
                .addAction(R.mipmap.ic_launcher, "Stop", PendingIntent.getBroadcast(this, 2, new Intent("STOP_SERVER"), 0))
                .setPriority(NotificationCompat.PRIORITY_MAX);
    }

    private void updateNotification() {
        if (notificationBuilder == null)
            return;

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
        notificationManager.notify(1, notificationBuilder.build());
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
