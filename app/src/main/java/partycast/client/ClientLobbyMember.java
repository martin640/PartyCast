package partycast.client;

import androidx.annotation.DrawableRes;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetAddress;
import java.net.UnknownHostException;

import sk.martin64.partycast.R;
import sk.martin64.partycast.utils.Callback;
import partycast.model.Lobby;
import partycast.model.LobbyMember;

public class ClientLobbyMember implements LobbyMember {

    private String name, agent;
    private int id, permissions;
    private InetAddress address;
    private ClientLobby lobby;

    ClientLobbyMember(JSONObject data, ClientLobby lobby) {
        this.lobby = lobby;
        update(data);
    }

    void update(JSONObject data) {
        if ("LobbyMember".equals(data.optString("class")) && data.has("values")) {
            try {
                JSONObject values = data.getJSONObject("values");

                this.name = values.optString("name");
                this.agent = values.optString("agent");
                this.id = values.optInt("id");
                this.permissions = values.optInt("permissions");

                String add = values.optString("address");
                if (this.id == lobby.hostId) {
                    this.address = lobby.socketClient.getRemoteSocketAddress().getAddress();
                } else {
                    this.address = InetAddress.getByName(add);
                }
            } catch (UnknownHostException | JSONException e) {
                e.printStackTrace();
            }
        } else throw new IllegalArgumentException("Supplied data is not for LobbyMember");
    }

    @Override
    public void changeName(String name, Callback<LobbyMember> callback) {
        try {
            lobby.request("LobbyCtl.UPDATE_USER",
                    new JSONObject()
                            .put("id", id)
                            .put("name", name),
                    new Callback<JSONObject>() {
                        @Override
                        public void onSuccess(JSONObject jsonObject) {
                            if (callback != null) callback.onSuccess(ClientLobbyMember.this);
                        }

                        @Override
                        public void onError(Exception e) {
                            if (callback != null) callback.onError(e);
                        }
                    });
        } catch (JSONException e) { }
    }

    @Override
    public void changePermissions(int permissions, Callback<LobbyMember> callback) {
        try {
            lobby.request("LobbyCtl.UPDATE_USER",
                    new JSONObject()
                            .put("id", id)
                            .put("permissions", permissions),
                    new Callback<JSONObject>() {
                        @Override
                        public void onSuccess(JSONObject jsonObject) {
                            if (callback != null) callback.onSuccess(ClientLobbyMember.this);
                        }

                        @Override
                        public void onError(Exception e) {
                            if (callback != null) callback.onError(e);
                        }
                    });
        } catch (JSONException e) { }
    }

    @Override
    public void kick(Callback<Void> callback) {
        callback.onError(new UnsupportedOperationException("Remote kicking is not supported in this version"));
    }

    @Override
    public void ban(Callback<Void> callback) {
        callback.onError(new UnsupportedOperationException("Remote banning is not supported in this version"));
    }

    @Override
    public String getUserAgent() {
        return agent;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public int getPermissions() {
        return permissions;
    }

    @Override
    public InetAddress getAddress() {
        return address;
    }

    @Override
    public Lobby getContext() {
        return lobby;
    }

    public static String getAgentName(LobbyMember member) {
        String agent;
        if (member == null || (agent = member.getUserAgent()) == null) return "Unknown";

        if (agent.contains("PartyCast")) return "Android client";
        if (agent.contains("Windows")) return "Windows client";

        return "Unknown";
    }

    @DrawableRes
    public static int getAgentIcon(LobbyMember member) {
        String agent;
        if (member == null || (agent = member.getUserAgent()) == null) return 0;

        if (agent.contains("PartyCast")) return R.drawable.ic_round_device_hub_24;
        if (agent.contains("Windows")) return R.drawable.ic_round_laptop_24;

        return R.drawable.ic_round_account_circle_24;
    }
}
