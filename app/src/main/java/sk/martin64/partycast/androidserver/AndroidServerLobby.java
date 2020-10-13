package sk.martin64.partycast.androidserver;

import android.content.ContentResolver;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.upstream.DataSource;

import partycast.model.LobbyEventListener;
import partycast.server.ServerLobby;

public class AndroidServerLobby extends ServerLobby {

    LocalLibraryProvider libraryProvider;
    ExoPlayer player;
    DataSource.Factory dataSourceFactory;
    ServerQueueLooper queueLooper;

    public AndroidServerLobby(String title, int port, String username, LobbyEventListener listener,
                              ExoPlayer player, DataSource.Factory dataSourceFactory, int artworkProviderPort, ContentResolver contentResolver) {
        super(title, port, username, listener);

        this.libraryProvider = new LocalLibraryProvider(artworkProviderPort, contentResolver, this);

        this.player = player;
        this.dataSourceFactory = dataSourceFactory;
        this.player.addListener(new Player.EventListener() {
            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                if (playbackState == ExoPlayer.STATE_ENDED) {
                    getLooper().skip(null);
                }
            }

            @Override
            public void onPlayerError(ExoPlaybackException error) {
                error.printStackTrace();
            }
        });

        this.queueLooper = new ServerQueueLooper(this);

        prepare(this.libraryProvider, this.queueLooper);
    }

    void setPlaybackState(int state) {
        playbackState = state;
    }

    @Override
    public void internalShutdown(Exception reason) {
        if (player != null) player.stop(true);
        if (libraryProvider != null) libraryProvider.shutdownServer();
        super.internalShutdown(reason);
    }
}