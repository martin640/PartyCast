package partycast.server;

import org.java_websocket.WebSocket;
import org.java_websocket.framing.CloseFrame;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetAddress;

import sk.martin64.partycast.utils.Callback;
import partycast.model.Lobby;
import partycast.model.LobbyEventListener;
import partycast.model.LobbyMember;

public class ServerLobbyMember implements LobbyMember, JSONable {

    private String name, agent;
    private int id, permissions;
    WebSocket connection;
    private ServerLobby lobby;

    ServerLobbyMember(String name, int id, int permissions, String agent, WebSocket connection, ServerLobby lobby) {
        this.name = name;
        this.id = id;
        this.permissions = permissions;
        this.agent = agent;
        this.connection = connection;
        this.lobby = lobby;
    }

    WebSocket getConnection() {
        return connection;
    }

    /**
     * Changes name of user without triggering event broadcast
     */
    void changeNameInternally(String name) {
        this.name = name;
    }

    /**
     * Changes permissions of user without triggering event broadcast
     */
    void changePermissionsInternally(int permissions) {
        this.permissions = permissions;
    }

    @Override
    public void changeName(String name, Callback<LobbyMember> callback) {
        changeNameInternally(name);

        if (callback != null) callback.onSuccess(this);
        lobby.broadcastEvent("Event.USER_UPDATED", this);

        for (LobbyEventListener l : lobby.safeListenersCopy())
            l.onUserUpdated(lobby, this);
    }

    @Override
    public void changePermissions(int permissions, Callback<LobbyMember> callback) {
        changePermissionsInternally(permissions);

        if (callback != null) callback.onSuccess(this);
        lobby.broadcastEvent("Event.USER_UPDATED", this);

        for (LobbyEventListener l : lobby.safeListenersCopy())
            l.onUserUpdated(lobby, this);
    }

    @Override
    public void kick(Callback<Void> callback) {
        connection.close(CloseFrame.REFUSE, "You were kicked out of the session");
        callback.onSuccess(null);
    }

    @Override
    public void ban(Callback<Void> callback) {
        callback.onError(new UnsupportedOperationException("Banning is not supported in current version"));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getUserAgent() {
        return agent;
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
        return connection != null ? connection.getRemoteSocketAddress().getAddress() : null;
    }

    @Override
    public String getAddressString() {
        return connection != null ? connection.getRemoteSocketAddress().getAddress().getHostAddress() : "";
    }

    @Override
    public Lobby getContext() {
        return lobby;
    }

    @Override
    public void toJSON(JSONObject out) throws JSONException {
        out.put("class", "LobbyMember")
                .put("values", new JSONObject()
                        .put("name", getName())
                        .put("agent", getUserAgent())
                        .put("id", getId())
                        .put("permissions", getPermissions())
                        .put("address", getAddressString())
                );
    }
}