package sk.martin64.partycast.client;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import sk.martin64.partycast.core.LibraryItem;
import sk.martin64.partycast.core.Lobby;
import sk.martin64.partycast.core.Queue;
import sk.martin64.partycast.core.QueueLooper;
import sk.martin64.partycast.utils.Callback;

public class ClientQueueLooper implements QueueLooper {

    private ClientLobby lobby;
    private List<ClientQueue> rounds;
    private int currentRound = -1;

    ClientQueueLooper(JSONObject data, ClientLobby lobby) {
        this.lobby = lobby;
        this.rounds = new ArrayList<>();
        update(data);
    }

    void update(JSONObject data) {
        if ("QueueLooper".equals(data.optString("class")) && data.has("values")) {
            try {
                JSONObject values = data.getJSONObject("values");

                this.currentRound = values.optInt("currentQueue");

                JSONArray roundsJson = values.getJSONArray("rounds");
                if (this.rounds.isEmpty()) {
                    for (int i = 0; i < roundsJson.length(); i++) {
                        this.rounds.add(new ClientQueue(roundsJson.getJSONObject(i), this));
                    }
                } else {
                    A: for (int i = 0; i < roundsJson.length(); i++) {
                        ClientQueue m = new ClientQueue(roundsJson.getJSONObject(i), this);
                        for (int a = 0; a < this.rounds.size(); a++) {
                            if (this.rounds.get(a).getId() == m.getId()) {
                                this.rounds.get(a).update(roundsJson.getJSONObject(i));
                                continue A;
                            }
                        }
                        this.rounds.add(m);
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else throw new IllegalArgumentException("Supplied data is not for LobbyMember");
    }

    @Override
    public void enqueue(LibraryItem item, Callback<Void> callback) {
        try {
            lobby.request("LobbyCtl.ENQUEUE",
                    new JSONObject().put("id", item.getUniqueId()),
                    new Callback<JSONObject>() {
                        @Override
                        public void onSuccess(JSONObject jsonObject) {
                            if (callback != null) callback.onSuccess(null);
                        }

                        @Override
                        public void onError(Exception e) {
                            if (callback != null) callback.onError(e);
                        }
                    });
        } catch (JSONException e) { }
    }

    @Override
    public void play(Callback<QueueLooper> callback) {
        lobby.request("LobbyCtl.PLAYBACK_PLAY", new JSONObject(),
                new Callback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject jsonObject) {
                        if (callback != null) callback.onSuccess(null);
                    }

                    @Override
                    public void onError(Exception e) {
                        if (callback != null) callback.onError(e);
                    }
                });
    }

    @Override
    public void pause(Callback<QueueLooper> callback) {
        lobby.request("LobbyCtl.PLAYBACK_PAUSE", new JSONObject(),
                new Callback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject jsonObject) {
                        if (callback != null) callback.onSuccess(null);
                    }

                    @Override
                    public void onError(Exception e) {
                        if (callback != null) callback.onError(e);
                    }
                });
    }

    @Override
    public void skip(Callback<QueueLooper> callback) {
        lobby.request("LobbyCtl.PLAYBACK_SKIP", new JSONObject(),
                new Callback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject jsonObject) {
                        if (callback != null) callback.onSuccess(null);
                    }

                    @Override
                    public void onError(Exception e) {
                        if (callback != null) callback.onError(e);
                    }
                });
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

    @Override
    public Lobby getContext() {
        return lobby;
    }
}