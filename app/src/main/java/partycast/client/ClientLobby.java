package partycast.client;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import partycast.model.LibraryProvider;
import partycast.model.Lobby;
import partycast.model.LobbyEventListener;
import partycast.model.LobbyMember;
import partycast.model.QueueLooper;
import sk.martin64.partycast.BuildConfig;
import sk.martin64.partycast.ui.UiHelper;
import sk.martin64.partycast.utils.Callback;

public class ClientLobby implements Lobby {

    public static final String VERSION_NAME = "1.0.16";

    WebSocketClient socketClient;
    String title;
    List<ClientLobbyMember> members, membersCache;
    int hostId, clientId;
    ClientQueueLooper looper;
    ClientRemoteLibraryProvider libraryProvider;

    List<LobbyEventListener> listeners;

    Map<Integer, Callback<JSONObject>> requests;
    int requestsIdPool;

    int state, playerState;
    boolean stateLoaded;

    public ClientLobby(String server, String clientName, LobbyEventListener listener) {
        this.members = new ArrayList<>();
        this.membersCache = new ArrayList<>();
        this.listeners = new ArrayList<>();
        this.listeners.add(listener);
        this.requests = new HashMap<>();
        URI uri;
        try {
            uri = new URI(server);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        this.state = STATE_CONNECTING;
        this.socketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshakeData) {
                state = STATE_OPEN;
                System.err.println("[WebSocketClient::onOpen] Websocket established");

                Executors.newSingleThreadExecutor().submit(() -> {
                    System.err.println("[WebSocketClient::onOpen] Waiting 5 seconds for initial block of data from sever...");
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.err.println("[WebSocketClient::onOpen] Thread has been interrupted. Checking now...");
                    }

                    if (!stateLoaded) {
                        System.err.println("[WebSocketClient::onOpen] Lobby has not been initialized after 5 seconds. Aborting connection...");
                        ClientLobby.this.close();

                        for (LobbyEventListener l : listeners)
                            l.onInitializationFailed(ClientLobby.this, new TimeoutException("Lobby initialization timeout"));
                    }
                });
            }

            @Override
            public void onMessage(String message) {
                System.out.format("[WebSocketClient::onMessage] Accepted %s of data\n", UiHelper.humanReadableByteCountSI(message.getBytes(StandardCharsets.UTF_8).length));
                try {
                    JSONObject messageData = new JSONObject(message);
                    String eventType = messageData.getString("type");
                    clientId = messageData.optInt("clientId", clientId);
                    if (eventType.equals("LobbyCtl.DATA_PUSH")) { // full data push
                        loadAll(messageData.getJSONObject("data"));

                        for (LobbyEventListener l : listeners)
                            l.onConnected(ClientLobby.this);

                    } else if (eventType.equals("LobbyCtl.RESPONSE")) {
                        int id = messageData.optInt("id");
                        if (id >= 0) {
                            int status = messageData.optInt("status");
                            String msg = messageData.optString("message");

                            Callback<JSONObject> requestHandler = requests.get(id);
                            if (requestHandler != null) {
                                if (status == 0)
                                    requestHandler.onSuccess(messageData);
                                else
                                    requestHandler.onError(new Exception(msg));
                            }
                        }
                    } else if (eventType.startsWith("Event.")) {
                        handleEvent(messageData.optJSONObject("data"), eventType);
                    } else {
                        System.err.println("Unhandled event: " + eventType);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                state = STATE_CLOSED;

                for (LobbyEventListener l : listeners)
                    l.onDisconnect(ClientLobby.this, code, reason);
            }

            @Override
            public void onError(Exception ex) {
                for (LobbyEventListener l : listeners)
                    l.onError(ClientLobby.this, ex);
            }
        };
        this.socketClient.removeHeader("User-Agent");
        this.socketClient.addHeader("User-Agent", String.format("PartyCast/%s ClientLobby/%s", BuildConfig.VERSION_NAME, VERSION_NAME));
        this.socketClient.addHeader("PartyCast-Username", clientName);
    }

    public void connect() {
        this.socketClient.connect();
    }

    public void close() {
        this.socketClient.close();
    }

    public void request(String type, Object value, Callback<JSONObject> requestHandler) {
        if (this.socketClient.isOpen()) {
            int id = ++requestsIdPool;
            try {
                this.requests.put(id, requestHandler);
                this.socketClient.send(new JSONObject()
                        .put("id", id)
                        .put("type", type)
                        .put("value", value)
                        .toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void loadAll(JSONObject lobbyData) {
        if ("Lobby".equals(lobbyData.optString("class")) && lobbyData.has("values")) {
            try {
                JSONObject values = lobbyData.getJSONObject("values");

                this.title = values.optString("title");
                this.hostId = values.optInt("hostId");
                this.playerState = values.optInt("playerState");

                JSONArray membersArray = values.getJSONArray("members");
                for (int i = 0; i < membersArray.length(); i++) {
                    ClientLobbyMember m = new ClientLobbyMember(membersArray.getJSONObject(i), this);
                    this.members.add(m);
                    this.membersCache.add(m);
                }

                this.looper = new ClientQueueLooper(values.getJSONObject("looper"), this);
                this.libraryProvider = new ClientRemoteLibraryProvider(values.getJSONObject("library"), this);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else throw new IllegalArgumentException("Supplied data is not for Lobby");
    }

    private void handleEvent(JSONObject messageData, String event) {
        switch (event) {
            case "Event.USER_JOINED": {
                ClientLobbyMember d = new ClientLobbyMember(messageData, this);
                members.add(d);
                membersCache.add(d);

                for (LobbyEventListener l : listeners)
                    l.onUserJoined(this, d);
                break;
            }
            case "Event.USER_LEFT": {
                ClientLobbyMember d = new ClientLobbyMember(messageData, this);
                for (ListIterator<ClientLobbyMember> it = members.listIterator(); it.hasNext(); ) {
                    ClientLobbyMember member = it.next();
                    if (member.getId() == d.getId()) {
                        it.remove();
                    }
                }

                for (LobbyEventListener l : listeners)
                    l.onUserLeft(this, d);
                break;
            }
            case "Event.USER_UPDATED": {
                ClientLobbyMember d = new ClientLobbyMember(messageData, this);
                for (ClientLobbyMember member : members) {
                    if (member.getId() == d.getId()) {
                        member.update(messageData);

                        for (LobbyEventListener l : listeners)
                            l.onUserUpdated(this, d);
                        break;
                    }
                }
                break;
            }
            case "Event.LOBBY_UPDATED":
                if ("Lobby".equals(messageData.optString("class")) && messageData.has("values")) {
                    try {
                        JSONObject values = messageData.getJSONObject("values");

                        this.title = values.optString("title");
                        this.playerState = values.optInt("playerState");
                        this.looper.update(values.getJSONObject("looper"));

                        for (LobbyEventListener l : listeners)
                            l.onLobbyStateChanged(this);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else throw new IllegalArgumentException("Supplied data is not for Lobby");
                break;
            case "Event.QUEUE_UPDATED":
                this.looper.update(messageData);
                for (LobbyEventListener l : listeners)
                    l.onLooperUpdated(this, this.looper);
                break;
            case "Event.LIBRARY_UPDATED":
                this.libraryProvider.update(messageData);
                for (LobbyEventListener l : listeners)
                    l.onLibraryUpdated(this, this.libraryProvider);
                break;
        }
    }

    ClientLobbyMember findMemberInCache(int id) {
        for (ClientLobbyMember m : membersCache) {
            if (m.getId() == id) return m;
        }
        return null;
    }

    @Override
    public ClientLobbyMember getMemberById(int id) {
        for (ClientLobbyMember member : members)
            if (member.getId() == id) return member;
        return null;
    }

    @Override
    public int getConnectionState() {
        return state;
    }

    @Override
    public void addEventListener(LobbyEventListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void removeEventListener(LobbyEventListener listener) {
        this.listeners.remove(listener);
    }

    @Override
    public void changeTitle(String newName, Callback<Lobby> callback) {
        if (callback != null)
            callback.onError(new UnsupportedOperationException("Not Implemented"));
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public List<LobbyMember> getMembers() {
        return Collections.unmodifiableList(members);
    }

    @Override
    public LobbyMember getHost() {
        return getMemberById(hostId);
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
        return playerState;
    }

    @Override
    public LobbyMember getClient() {
        return getMemberById(clientId);
    }
}