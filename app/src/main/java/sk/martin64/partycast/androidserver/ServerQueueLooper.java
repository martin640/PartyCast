package sk.martin64.partycast.androidserver;

import android.media.MediaMetadataRetriever;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

import partycast.model.LibraryItem;
import partycast.model.Lobby;
import partycast.model.LobbyEventListener;
import partycast.model.LobbyMember;
import partycast.model.Queue;
import partycast.model.QueueLooper;
import partycast.model.RemoteMedia;
import partycast.server.JSONable;
import partycast.server.ServerLobbyMember;
import sk.martin64.partycast.ui.UiHelper;
import sk.martin64.partycast.utils.Callback;

public class ServerQueueLooper implements QueueLooper, JSONable {

    private final Object lock = new Object();
    private AndroidServerLobby lobby;
    private List<ServerQueue> rounds;
    private int currentRound = -1;

    public ServerQueueLooper(AndroidServerLobby lobby) {
        this.lobby = lobby;
        this.rounds = new ArrayList<>();

        lobby.player.setPlayWhenReady(true);
    }

    @Override
    public void enqueue(LibraryItem item, Callback<Void> callback) {
        if (!(item instanceof LocalLibraryItem)) {
            if (callback != null)
                callback.onError(new Exception("Invalid LibraryItem type!"));
            return;
        }
        enqueue(item, lobby.getHost(), callback);
    }

    @Override
    public void enqueue(LibraryItem itemTmp, LobbyMember caller, Callback<Void> callback) {
        if (!(itemTmp instanceof LocalLibraryItem)) {
            if (callback != null)
                callback.onError(new Exception("Invalid LibraryItem type!"));
            return;
        }
        LocalLibraryItem item = (LocalLibraryItem) itemTmp;

        Executors.newSingleThreadExecutor().submit(() -> {
            synchronized (lock) {
                try {
                    MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();
                    metaRetriever.setDataSource(item.path);

                    @SuppressWarnings("ConstantConditions")
                    long dur = Long.parseLong(metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                    String title = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                    String artist = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);

                    ServerQueue queue;
                    if (rounds.size() == 0) {
                        queue = new ServerQueue(this);
                        queue.id = 0;
                        rounds.add(queue);
                        currentRound = 0;
                    } else {
                        int r = currentRound;
                        AL: while (true) {
                            if (r >= rounds.size()) {
                                queue = new ServerQueue(this);
                                rounds.add(queue);
                                queue.id = rounds.indexOf(queue);
                            } else {
                                queue = rounds.get(r);

                                for (ServerLocalAudioFileRef a : queue.mediaQueue) {
                                    if (a.getRequester().getId() == caller.getId()) {
                                        r++;
                                        continue AL; // member already queued song in this round, move to next
                                    }
                                }
                            }
                            break;
                        }
                    }

                    ServerLocalAudioFileRef ref = new ServerLocalAudioFileRef((ServerLobbyMember) caller, new File(item.path), dur, title, artist, item.getImageUrl(), item.getRemoteImageUrl(), queue);
                    queue.mediaQueue.add(ref);
                    ref.id = queue.mediaQueue.indexOf(ref);

                    metaRetriever.release();

                    callback.onSuccess(null);

                    if (lobby.getPlayerState() == Lobby.PLAYBACK_READY) {
                        skip(null);
                    } else {
                        broadcastQueueUpdate();
                    }
                } catch (Exception e) {
                    if (callback != null)
                        callback.onError(e);
                }
            }
        });
    }

    @Override
    public void play(Callback<QueueLooper> callback) {
        UiHelper.runOnUiCompact(() -> {
            int playerState = lobby.getPlayerState();
            if (playerState == Lobby.PLAYBACK_PAUSED) {
                ServerQueue currentQueue = (ServerQueue) getCurrentQueue();
                if (currentQueue != null) {
                    ServerLocalAudioFileRef ref = (ServerLocalAudioFileRef) currentQueue.getCurrentlyPlaying();
                    if (ref != null) {
                        ref.start = System.currentTimeMillis() - lobby.player.getCurrentPosition();
                        ref.lastKnownProgress = lobby.player.getCurrentPosition();
                        ref.lastUpdate = System.currentTimeMillis();
                    }
                }

                lobby.player.setPlayWhenReady(true);
                lobby.setPlaybackState(Lobby.PLAYBACK_PLAYING);

                if (callback != null) callback.onSuccess(this);
                broadcastLobbyUpdate();
            } else if (playerState == Lobby.PLAYBACK_READY) {
                skip(callback);
            } else callback.onError(new Exception("Player is already playing"));
        });
    }

    @Override
    public void pause(Callback<QueueLooper> callback) {
        UiHelper.runOnUiCompact(() -> {
            lobby.player.setPlayWhenReady(false);
            lobby.setPlaybackState(Lobby.PLAYBACK_PAUSED);

            ServerQueue currentQueue = (ServerQueue) getCurrentQueue();
            if (currentQueue != null) {
                ServerLocalAudioFileRef ref = (ServerLocalAudioFileRef) currentQueue.getCurrentlyPlaying();
                if (ref != null) {
                    ref.lastKnownProgress = lobby.player.getCurrentPosition();
                    ref.lastUpdate = System.currentTimeMillis();
                }
            }

            if (callback != null) callback.onSuccess(this);
            broadcastLobbyUpdate();
        });
    }

    @Override
    public void skip(Callback<QueueLooper> callback) {
        UiHelper.runOnUiCompact(() -> {
            synchronized (lock) {
                if (currentRound < 0) { // nothing has been queued yet
                    if (callback != null) callback.onError(new Exception("No more songs in queue"));
                    broadcastLobbyUpdate();

                    return;
                }

                ServerQueue currentQueue = rounds.get(currentRound);
                int nextId = currentQueue.playingIndex + 1;
                if (nextId >= currentQueue.mediaQueue.size()) { // move to next queue
                    currentRound++;
                    if (currentRound < rounds.size()) {
                        currentQueue = rounds.get(currentRound);
                    } else {
                        currentQueue = new ServerQueue(this);
                        currentQueue.id = currentRound;
                        rounds.add(currentQueue);

                        a_stopforcefullyfromskip();

                        if (callback != null) callback.onError(new Exception("No more songs in queue"));
                        broadcastLobbyUpdate();

                        return; // next queue is empty, pause playback ;; keep playingIndex at -1
                    }
                }
                if ((currentQueue.playingIndex + 1) >= currentQueue.mediaQueue.size()) {
                    a_stopforcefullyfromskip();

                    if (callback != null) callback.onError(new Exception("No more songs in queue"));
                    broadcastLobbyUpdate();

                    return; // no next song in queue
                }
                ServerLocalAudioFileRef nextSong = currentQueue.mediaQueue.get(currentQueue.playingIndex + 1);

                MediaSource mediaSource = new ProgressiveMediaSource.Factory(lobby.dataSourceFactory)
                        .createMediaSource(Uri.fromFile(nextSong.getFile()));
                lobby.player.setPlayWhenReady(true);
                lobby.player.prepare(mediaSource);
                nextSong.start = System.currentTimeMillis();
                nextSong.lastKnownProgress = 0;
                nextSong.lastUpdate = System.currentTimeMillis();
                currentQueue.playingIndex = nextSong.id;

                lobby.setPlaybackState(Lobby.PLAYBACK_PLAYING);

                if (callback != null) callback.onSuccess(this);
                broadcastLobbyUpdate();
            }
        });
    }

    private void a_stopforcefullyfromskip() {
        if (lobby.getPlayerState() == Lobby.PLAYBACK_PLAYING) {
            lobby.setPlaybackState(Lobby.PLAYBACK_READY);
            lobby.player.setPlayWhenReady(false);
        }
    }

    private void broadcastLobbyUpdate() {
        lobby.broadcastEvent("Event.LOBBY_UPDATED", lobby);

        for (LobbyEventListener l : lobby.safeListenersCopy())
            l.onLobbyStateChanged(lobby);
    }

    private void broadcastQueueUpdate() {
        lobby.broadcastEvent("Event.QUEUE_UPDATED", this);

        for (LobbyEventListener l : lobby.safeListenersCopy())
            l.onLooperUpdated(lobby, this);
    }

    @Nullable
    @Override
    public Queue getCurrentQueue() {
        return currentRound >= 0 ? rounds.get(currentRound) : null;
    }

    @Nullable
    @Override
    public Queue getQueryById(int id) {
        return rounds.get(id);
    }

    @NonNull
    @Override
    public List<Queue> getAll() {
        return Collections.unmodifiableList(rounds);
    }

    @NonNull
    @Override
    public List<Queue> getPending() {
        return currentRound >= 0 ? Collections.unmodifiableList(rounds.subList(currentRound, rounds.size())) : getAll();
    }

    @NonNull
    @Override
    public List<RemoteMedia> range(RemoteMedia first, RemoteMedia last) {
        if (first == null && last == null) {
            List<RemoteMedia> result = new ArrayList<>();

            for (Queue queue : rounds) result.addAll(queue.getAll());

            return result;
        } else {
            List<RemoteMedia> result = new ArrayList<>();
            boolean startFound = first == null;

            for (Queue queue : rounds) {
                for (RemoteMedia m : queue.getAll()) {
                    if (startFound) result.add(m);
                    else if (m == first) {
                        startFound = true;
                        result.add(m);
                    }

                    if (m == last) return result;
                }
            }

            return result;
        }
    }

    @Override
    public Lobby getContext() {
        return lobby;
    }

    @Override
    public void toJSON(JSONObject out) throws JSONException {
        JSONArray rounds = new JSONArray();
        for (ServerQueue queue : this.rounds) {
            JSONObject dataJson = new JSONObject();
            queue.toJSON(dataJson);
            rounds.put(dataJson);
        }

        out.put("class", "QueueLooper")
                .put("values", new JSONObject()
                        .put("currentQueue", currentRound)
                        .put("rounds", rounds)
                );
    }
}