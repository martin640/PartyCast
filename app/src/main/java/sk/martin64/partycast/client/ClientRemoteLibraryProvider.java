package sk.martin64.partycast.client;

import android.content.ContentResolver;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import fi.iki.elonen.NanoHTTPD;
import sk.martin64.partycast.core.LibraryItem;
import sk.martin64.partycast.core.LibraryProvider;
import sk.martin64.partycast.core.Lobby;
import sk.martin64.partycast.server.LocalLibraryItem;
import sk.martin64.partycast.server.ServerLobby;
import sk.martin64.partycast.utils.Callback;

public class ClientRemoteLibraryProvider implements LibraryProvider {

    private String name;
    private List<ClientRemoteLibraryItem> items;
    private ClientLobby lobby;

    ClientRemoteLibraryProvider(JSONObject data, ClientLobby lobby) {
        this.lobby = lobby;
        this.items = new ArrayList<>();
        update(data);
    }

    void update(JSONObject data) {
        if ("LibraryProvider".equals(data.optString("class")) && data.has("values")) {
            try {
                JSONObject values = data.getJSONObject("values");

                this.name = values.optString("name");

                JSONArray mediaJson = values.getJSONArray("items");
                if (this.items.isEmpty()) {
                    for (int i = 0; i < mediaJson.length(); i++) {
                        this.items.add(new ClientRemoteLibraryItem(mediaJson.getJSONObject(i), this));
                    }
                } else {
                    A: for (int i = 0; i < mediaJson.length(); i++) {
                        ClientRemoteLibraryItem m = new ClientRemoteLibraryItem(mediaJson.getJSONObject(i), this);
                        for (int a = 0; a < this.items.size(); a++) {
                            if (this.items.get(a).getUniqueId() == m.getUniqueId()) {
                                this.items.get(a).update(mediaJson.getJSONObject(i));
                                continue A;
                            }
                        }
                        this.items.add(m);
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else throw new IllegalArgumentException("Supplied data is not for LibraryProvider");
    }

    @Override
    public void refresh(Callback<LibraryProvider> callback) {
        lobby.request("", null, new Callback<JSONObject>() {
            @Override
            public void onError(Exception e) {

            }

            @Override
            public void onSuccess(JSONObject jsonObject) {

            }
        });
    }

    @Override
    public List<LibraryItem> getAll() {
        return Collections.unmodifiableList(items);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Lobby getContext() {
        return lobby;
    }
}