package sk.martin64.partycast.server;

import android.content.ContentResolver;
import android.text.TextUtils;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.upstream.DataSource;

import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import sk.martin64.partycast.core.LibraryProvider;
import sk.martin64.partycast.core.Lobby;
import sk.martin64.partycast.core.LobbyEventListener;
import sk.martin64.partycast.core.LobbyMember;
import sk.martin64.partycast.core.OperationRejectedException;
import sk.martin64.partycast.core.QueueLooper;
import sk.martin64.partycast.utils.Callback;

public class ServerLobby implements Lobby, JSONable {

    /**
     * Determines whether server should cache user data by IP address.
     * This should be false in public server.
     */
    public static final boolean FLAG_USER_CACHE = true;

    private WebSocketServer socketServer;
    private String title;
    private List<ServerLobbyMember> members;
    private Map<WebSocket, ServerLobbyMember> memberMap;
    private Map<String, ServerLobbyMember> userCache;
    private ServerLobbyMember server;

    private ServerQueueLooper looper;

    final List<LobbyEventListener> listenersUnsafe;

    // Android-specific
    LocalLibraryProvider libraryProvider;
    ExoPlayer player;
    DataSource.Factory dataSourceFactory;
    //

    private int memberIdPool, libraryItemIdPool;
    private int state;
    int playbackState;

    private Map<Integer, LocalLibraryItem> localLibraryItemMap;

    public ServerLobby(String title,
                       int port,
                       ExoPlayer player,
                       DataSource.Factory dataSourceFactory,
                       int artworkProviderPort,
                       ContentResolver contentResolver,
                       String username,
                       LobbyEventListener listener) {
        this.title = title;
        this.members = new ArrayList<>();
        this.memberMap = new HashMap<>();
        this.userCache = new HashMap<>();
        this.listenersUnsafe = new ArrayList<>();
        this.listenersUnsafe.add(listener);
        this.localLibraryItemMap = new HashMap<>();

        this.libraryProvider = new LocalLibraryProvider(artworkProviderPort, contentResolver, this);

        this.player = player;
        this.dataSourceFactory = dataSourceFactory;
        this.player.addListener(new Player.EventListener() {
            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                if (playbackState == ExoPlayer.STATE_ENDED) {
                    looper.skip(null);
                }
            }

            @Override
            public void onPlayerError(ExoPlaybackException error) {
                error.printStackTrace();
            }
        });

        this.looper = new ServerQueueLooper(this);

        InetSocketAddress socketAddress = new InetSocketAddress(port);
        this.server = new ServerLobbyMember(username, ++memberIdPool, LobbyMember.PERMISSION_HOST, "Server", null, this) {
            @Override
            void changePermissionsInternally(int permissions) {
                // disallow changing permissions for admin
            }

            @Override
            public void kick(Callback<Void> callback) { callback.onError(new OperationRejectedException("Cannot kick server owner")); }

            @Override
            public void ban(Callback<Void> callback) { callback.onError(new OperationRejectedException("Cannot ban server owner")); }
        };
        this.members.add(this.server);

        this.state = STATE_CONNECTING;
        socketServer = new WebSocketServer(socketAddress) {

            @Override
            public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(WebSocket conn, Draft draft, ClientHandshake request) throws InvalidDataException {
                ServerHandshakeBuilder builder = super.onWebsocketHandshakeReceivedAsServer(conn, draft, request);
                builder.put("PartyCast-Lobby-Name", title);
                return builder;
            }

            @Override
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                ServerLobbyMember newMember;

                if (FLAG_USER_CACHE) {
                    newMember = userCache.get(conn.getRemoteSocketAddress().getAddress().getHostAddress());
                    if (newMember != null) {
                        if (members.contains(newMember)) {
                            conn.send("Connect failed: IP already connected");
                            conn.close(CloseFrame.REFUSE, "IP already connected");
                            return;
                        }

                        newMember.connection = conn;
                    }
                }

                if (newMember == null) {
                    String newName = handshake.getFieldValue("PartyCast-Username");

                    if (TextUtils.isEmpty(newName))
                        newName = conn.getRemoteSocketAddress().getAddress().getHostName();

                    if (TextUtils.isEmpty(newName))
                        newName = conn.getRemoteSocketAddress().getAddress().getHostAddress();

                    if (TextUtils.isEmpty(newName))  {
                        conn.send("Connect failed: Invalid name provided");
                        conn.close(CloseFrame.REFUSE, "Invalid name provided");
                        return;
                    }

                    for (ServerLobbyMember member : members) {
                        if (Objects.equals(member.getName(), newName)) {
                            conn.send("Connect failed: Username is already in use");
                            conn.close(CloseFrame.REFUSE, "Username is already in use");
                            return;
                        }
                    }

                    newMember = new ServerLobbyMember(newName, ++memberIdPool, LobbyMember.PERMISSIONS_DEFAULT,
                            handshake.getFieldValue("User-Agent"), conn, ServerLobby.this);
                }

                // acknowledge existing users before pushing new member to list
                broadcastEvent("Event.USER_JOINED", newMember);
                members.add(newMember);
                memberMap.put(conn, newMember);

                if (FLAG_USER_CACHE)
                    userCache.put(conn.getRemoteSocketAddress().getAddress().getHostAddress(), newMember);

                // push all data to new client
                sendEvent(newMember, "LobbyCtl.DATA_PUSH", ServerLobby.this);

                for (LobbyEventListener l : safeListenersCopy())
                    l.onUserJoined(ServerLobby.this, newMember);
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                ServerLobbyMember member = memberMap.get(conn);
                if (member != null) {
                    memberMap.remove(conn);
                    members.remove(member);
                    broadcastEvent("Event.USER_LEFT", member);

                    for (LobbyEventListener l : safeListenersCopy())
                        l.onUserLeft(ServerLobby.this, member);
                }
            }

            @Override
            public void onMessage(WebSocket conn, String message) {
                ServerLobbyMember member = memberMap.get(conn);
                if (member != null) {
                    try {
                        JSONObject messageData = new JSONObject(message);
                        String eventType = messageData.getString("type");
                        int mid = messageData.optInt("id", -1);

                        if (eventType.equals("LobbyCtl.UPDATE_USER")) {
                            JSONObject data = messageData.getJSONObject("value");
                            if (data.optInt("id", member.getId()) == member.getId() &&
                                    Lobby.checkPermission(member, LobbyMember.PERMISSION_CHANGE_NAME)) {

                                handleUsernameChange(conn, member, mid, data);
                            } else if (Lobby.checkPermission(member, LobbyMember.PERMISSION_MANAGE_USERS)) {
                                handleUserUpdate(conn, getMemberById(data.optInt("id", member.getId())), mid, data);
                            } else {
                                conn.send(new JSONObject()
                                        .put("id", mid)
                                        .put("type", "LobbyCtl.RESPONSE")
                                        .put("status", -5)
                                        .put("message", "Action rejected")
                                        .toString());
                            }
                        } else if (eventType.equals("LobbyCtl.ENQUEUE") && Lobby.checkPermission(member, LobbyMember.PERMISSION_QUEUE)) {
                            JSONObject data = messageData.getJSONObject("value");
                            int id = data.optInt("id");
                            LocalLibraryItem item = getLibraryItemById(id);
                            if (item != null) {
                                looper.enqueueByMember(item, member, responseOnCallback(conn, mid));
                            } else {
                                conn.send(new JSONObject()
                                        .put("id", mid)
                                        .put("type", "LobbyCtl.RESPONSE")
                                        .put("status", -5)
                                        .put("message", "Action rejected")
                                        .toString());
                            }
                        } else if (eventType.equals("LobbyCtl.PLAYBACK_PLAY") && Lobby.checkPermission(member, LobbyMember.PERMISSION_MANAGE_QUEUE)) {
                            looper.play(responseOnCallback(conn, mid));
                        } else if (eventType.equals("LobbyCtl.PLAYBACK_PAUSE") && Lobby.checkPermission(member, LobbyMember.PERMISSION_MANAGE_QUEUE)) {
                            looper.pause(responseOnCallback(conn, mid));
                        } else if (eventType.equals("LobbyCtl.PLAYBACK_SKIP") && Lobby.checkPermission(member, LobbyMember.PERMISSION_MANAGE_QUEUE)) {
                            looper.skip(responseOnCallback(conn, mid));
                        } else {
                            System.err.println("Unhandled message: " + eventType);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        conn.send("Failed to handle message: Json parse error");
                    }
                } else {
                    conn.send("Unable to identify remote user: Unauthorized");
                }
            }

            @Override
            public void onError(WebSocket conn, Exception ex) {
                ex.printStackTrace();

                if (conn == null) { // exception on server level
                    internalShutdown(ex);
                }
            }

            @Override
            public void onStart() {
                state = STATE_OPEN;
                System.out.println("ServerLobby started");

                for (LobbyEventListener l : safeListenersCopy())
                    l.onConnected(ServerLobby.this);
            }
        };
        socketServer.start();
    }

    void internalShutdown(Exception reason) {
        try {
            socketServer.stop();

            player.stop(true);
            state = STATE_CLOSED;

            for (LobbyEventListener l : safeListenersCopy())
                l.onError(ServerLobby.this, reason);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void sendEvent(ServerLobbyMember member, String type, JSONable data) {
        WebSocket ws = member.getConnection();
        if (ws != null && ws.isOpen()) {
            try {
                JSONObject dataJson = new JSONObject();
                data.toJSON(dataJson);
                ws.send(new JSONObject()
                        .put("type", type)
                        .put("data", dataJson)
                        .put("clientId", member.getId())
                        .toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    void broadcastEvent(String type, JSONable data) {
        for (ServerLobbyMember member : members) {
            WebSocket conn = member.getConnection();
            if (conn != null && conn.isOpen()) {
                try {
                    JSONObject dataJson = new JSONObject();
                    data.toJSON(dataJson);
                    conn.send(new JSONObject()
                            .put("type", type)
                            .put("data", dataJson)
                            .put("clientId", member.getId())
                            .toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    <T> Callback<T> responseOnCallback(WebSocket conn, int mid) {
        return new Callback<T>() {
            @Override
            public void onError(Exception e) {
                try {
                    conn.send(new JSONObject()
                            .put("id", mid)
                            .put("type", "LobbyCtl.RESPONSE")
                            .put("status", -1)
                            .put("message", e.getMessage())
                            .toString());
                } catch (JSONException ee) {}
            }

            @Override
            public void onSuccess(T looper) {
                try {
                    conn.send(new JSONObject()
                            .put("id", mid)
                            .put("type", "LobbyCtl.RESPONSE")
                            .put("status", 0)
                            .put("message", "OK")
                            .toString());
                } catch (JSONException e) {}
            }
        };
    }

    private void handleUsernameChange(WebSocket conn, ServerLobbyMember user, int messageId, JSONObject data) throws JSONException {
        String newName = data.optString("name");
        if (TextUtils.isEmpty(newName)) newName = conn.getRemoteSocketAddress().getAddress().getHostAddress();
        if (TextUtils.isEmpty(newName)) {
            conn.send(new JSONObject()
                    .put("id", messageId)
                    .put("type", "LobbyCtl.RESPONSE")
                    .put("status", -1)
                    .put("message", "Invalid name provided")
                    .toString());
            return;
        }
        for (ServerLobbyMember mem : members) {
            if (Objects.equals(mem.getName(), newName)) {
                conn.send(new JSONObject()
                        .put("id", messageId)
                        .put("type", "LobbyCtl.RESPONSE")
                        .put("status", -1)
                        .put("message", "Username is already in use")
                        .toString());
                return;
            }
        }

        user.changeNameInternally(newName);
        conn.send(new JSONObject()
                .put("id", messageId)
                .put("type", "LobbyCtl.RESPONSE")
                .put("status", 0)
                .put("message", "Username updated")
                .toString());

        broadcastEvent("Event.USER_UPDATED", user);

        for (LobbyEventListener l : safeListenersCopy())
            l.onUserUpdated(ServerLobby.this, user);
    }
    private void handleUserUpdate(WebSocket conn, ServerLobbyMember user, int messageId, JSONObject data) throws JSONException {
        String newName = data.optString("name", null);
        NameCheck: if (newName != null) {
            if (TextUtils.isEmpty(newName)) break NameCheck;
            for (ServerLobbyMember mem : members) {
                if (Objects.equals(mem.getName(), newName)) {
                    conn.send(new JSONObject()
                            .put("id", messageId)
                            .put("type", "LobbyCtl.RESPONSE")
                            .put("status", -1)
                            .put("message", "Username is already in use")
                            .toString());
                    return;
                }
            }

            user.changeName(newName, null);
            conn.send(new JSONObject()
                    .put("id", messageId)
                    .put("type", "LobbyCtl.RESPONSE")
                    .put("status", 0)
                    .put("message", "Username updated")
                    .toString());
        }
        int permissions = data.optInt("permissions", user.getPermissions());
        user.changePermissionsInternally(permissions);

        broadcastEvent("Event.USER_UPDATED", user);

        for (LobbyEventListener l : safeListenersCopy())
            l.onUserUpdated(ServerLobby.this, user);
    }

    /**
     * Create safe copy of event listeners in case any of listeners decided to unregister self during pending iteration
     */
    List<LobbyEventListener> safeListenersCopy() {
        synchronized (listenersUnsafe) {
            return new ArrayList<>(listenersUnsafe);
        }
    }

    void registerLibItem(LocalLibraryItem item) {
        item.id = ++libraryItemIdPool;
        localLibraryItemMap.put(item.id, item);
    }

    LocalLibraryItem getLibraryItemById(int id) {
        return localLibraryItemMap.get(id);
    }

    public void stop() {
        internalShutdown(new Exception("External stop request"));
    }

    @Override
    public void changeTitle(String newName, Callback<Lobby> callback) {
        this.title = newName;

        if (callback != null) callback.onSuccess(this);
        broadcastEvent("Event.LOBBY_UPDATED", this);

        for (LobbyEventListener l : safeListenersCopy())
            l.onLobbyStateChanged(this);
    }

    @Override
    public void addEventListener(LobbyEventListener listener) {
        synchronized (listenersUnsafe) {
            this.listenersUnsafe.add(listener);
        }
    }

    @Override
    public void removeEventListener(LobbyEventListener listener) {
        synchronized (listenersUnsafe) {
            this.listenersUnsafe.remove(listener);
        }
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public int getConnectionState() {
        return state;
    }

    @Override
    public QueueLooper getLooper() {
        return looper;
    }

    @Override
    public LibraryProvider getLibrary() {
        return libraryProvider;
    }

    @Override
    public int getPlayerState() {
        return playbackState;
    }

    @Override
    public List<LobbyMember> getMembers() {
        return Collections.unmodifiableList(members);
    }

    @Override
    public ServerLobbyMember getMemberById(int id) {
        for (ServerLobbyMember member : members)
            if (member.getId() == id) return member;
        return null;
    }

    @Override
    public LobbyMember getHost() {
        return server;
    }

    @Override
    public LobbyMember getClient() {
        return server;
    }

    @Override
    public void toJSON(JSONObject out) throws JSONException {
        JSONArray membersArray = new JSONArray();
        for (ServerLobbyMember member : members) {
            JSONObject dataJson = new JSONObject();
            member.toJSON(dataJson);
            membersArray.put(dataJson);
        }

        JSONObject looperJson = new JSONObject();
        looper.toJSON(looperJson);

        JSONObject libraryJson = new JSONObject();
        libraryProvider.toJSON(libraryJson);

        out.put("class", "Lobby")
                .put("values", new JSONObject()
                        .put("title", getTitle())
                        .put("hostId", server.getId())
                        .put("members", membersArray)
                        .put("looper", looperJson)
                        .put("library", libraryJson)
                        .put("playerState", playbackState)
                );
    }
}