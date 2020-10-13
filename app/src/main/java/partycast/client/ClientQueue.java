package partycast.client;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import partycast.model.Queue;
import partycast.model.QueueLooper;
import partycast.model.RemoteMedia;

public class ClientQueue implements Queue {

    private ClientQueueLooper looper;
    private int id, playingIndex;
    private List<ClientRemoteMedia> mediaQueue;

    ClientQueue(JSONObject data, ClientQueueLooper looper) {
        this.looper = looper;
        this.mediaQueue = new ArrayList<>();
        update(data);
    }

    void update(JSONObject data) {
        if ("Queue".equals(data.optString("class")) && data.has("values")) {
            try {
                JSONObject values = data.getJSONObject("values");

                this.id = values.optInt("id");
                this.playingIndex = values.optInt("playing");

                JSONArray mediaJson = values.getJSONArray("media");
                if (this.mediaQueue.isEmpty()) {
                    for (int i = 0; i < mediaJson.length(); i++) {
                        this.mediaQueue.add(new ClientRemoteMedia(mediaJson.getJSONObject(i), this));
                    }
                } else {
                    A: for (int i = 0; i < mediaJson.length(); i++) {
                        ClientRemoteMedia m = new ClientRemoteMedia(mediaJson.getJSONObject(i), this);
                        for (int a = 0; a < this.mediaQueue.size(); a++) {
                            if (this.mediaQueue.get(a).getId() == m.getId()) {
                                this.mediaQueue.get(a).update(mediaJson.getJSONObject(i));
                                continue A;
                            }
                        }
                        this.mediaQueue.add(m);
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else throw new IllegalArgumentException("Supplied data is not for LobbyMember");
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
}