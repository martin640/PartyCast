package sk.martin64.partycast.server;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import sk.martin64.partycast.core.Queue;
import sk.martin64.partycast.core.QueueLooper;
import sk.martin64.partycast.core.RemoteMedia;

public class ServerQueue implements Queue, JSONable {

    private ServerQueueLooper looper;
    int id, playingIndex = -1;
    List<ServerLocalAudioFileRef> mediaQueue;

    public ServerQueue(ServerQueueLooper looper) {
        this.looper = looper;
        this.mediaQueue = new ArrayList<>();
    }

    @Override
    public int getId() {
        return id;
    }

    @Nullable
    @Override
    public RemoteMedia getCurrentlyPlaying() {
        return playingIndex >= 0 ? mediaQueue.get(playingIndex) : null;
    }

    @Nullable
    @Override
    public RemoteMedia getQueryById(int id) {
        return mediaQueue.get(id);
    }

    @NonNull
    @Override
    public List<RemoteMedia> getAll() {
        return Collections.unmodifiableList(mediaQueue);
    }

    @NonNull
    @Override
    public List<RemoteMedia> getPending() {
        return playingIndex >= 0 ? Collections.unmodifiableList(mediaQueue.subList(playingIndex, mediaQueue.size())) : getAll();
    }

    @Override
    public QueueLooper getLooper() {
        return looper;
    }

    @Override
    public void toJSON(JSONObject out) throws JSONException {
        JSONArray media = new JSONArray();
        for (ServerLocalAudioFileRef med : this.mediaQueue) {
            JSONObject dataJson = new JSONObject();
            med.toJSON(dataJson);
            media.put(dataJson);
        }

        out.put("class", "Queue")
                .put("values", new JSONObject()
                        .put("media", media)
                        .put("id", id)
                        .put("playing", playingIndex)
                );
    }
}
