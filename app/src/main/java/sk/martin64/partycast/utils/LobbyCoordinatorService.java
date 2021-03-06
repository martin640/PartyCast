package sk.martin64.partycast.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import partycast.model.ActionBoardItem;
import partycast.model.LibraryProvider;
import partycast.model.Lobby;
import partycast.model.LobbyEventListener;
import partycast.model.LobbyMember;
import partycast.model.QueueLooper;
import partycast.model.VolumeControl;
import sk.martin64.partycast.ClientLobbyService;
import sk.martin64.partycast.ServerLobbyService;

import static android.content.Context.BIND_AUTO_CREATE;

public class LobbyCoordinatorService implements LobbyEventListener {

    private static final LobbyCoordinatorService singleton = new LobbyCoordinatorService();
    public static final int STATE_CREATED = 0;
    public static final int STATE_CONNECTING_CLIENT = 1;
    public static final int STATE_CREATING_SERVER = 2;
    public static final int STATE_OPEN = 3;
    public static final int STATE_CLOSING = 3;

    public static LobbyCoordinatorService getInstance() {
        return singleton;
    }

    private final Object lock = new Object();
    private List<Callback<Lobby>> waitingCallbacks = new ArrayList<>();
    private List<SafeCallback<LobbyCoordinatorService>> disconnectHandlers = new ArrayList<>();
    private Lobby activeLobby;
    private int state;

    public Lobby getActiveLobby() {
        return activeLobby;
    }

    public int getState() {
        return state;
    }

    /**
     * Get lobby status without making permanent connection
     */
    public void head(String server, Callback<Lobby> callback) {
        Executors.newSingleThreadExecutor().submit(() -> {
            HttpURLConnection urlConnection = null;
            System.setProperty("http.keepAlive", "false");
            try {
                URL url = new URL(String.format("http://%s:%s", server, ServerLobbyService.SERVER_PORT));
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("HEAD");

                String lobbyTitle = urlConnection.getHeaderField("PartyCast-Lobby-Name");
                callback.onSuccess(new AbstractHeadLobby(lobbyTitle, new ArrayList<>(), null));

                urlConnection.getInputStream().close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
        });
    }

    public void connectClient(Activity activity, String server, String username, Callback<Lobby> callback) {
        synchronized (lock) {
            if (state != STATE_CREATED) {
                callback.onError(new IllegalStateException("Attempt to connect to the server but one is already being started/running"));
                return;
            }
            state = STATE_CONNECTING_CLIENT;

            activity.startService(new Intent(activity, ClientLobbyService.class)
                    .putExtra("server", server)
                    .putExtra("username", username));
            activity.bindService(new Intent(activity, ClientLobbyService.class), new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                    ClientLobbyService service = ((ClientLobbyService.ClientLobbyServiceBinder) iBinder).getService();
                    Lobby lobby = service.getLobby();
                    handleLobbyState(activity, lobby, this, callback);
                }

                @Override
                public void onServiceDisconnected(ComponentName componentName) {}
            }, BIND_AUTO_CREATE);
        }
    }

    public void createServer(Activity activity, Callback<Lobby> callback) {
        synchronized (lock) {
            if (state != STATE_CREATED) {
                callback.onError(new IllegalStateException("Attempt to create server but one is already being started/running"));
                return;
            }
            state = STATE_CREATING_SERVER;

            activity.startService(new Intent(activity, ServerLobbyService.class));
            activity.bindService(new Intent(activity, ServerLobbyService.class), new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                    ServerLobbyService service = ((ServerLobbyService.ServerLobbyServiceBinder) iBinder).getService();
                    Lobby lobby = service.getLobby();
                    handleLobbyState(activity, lobby, this, callback);
                }

                @Override
                public void onServiceDisconnected(ComponentName componentName) {}
            }, BIND_AUTO_CREATE);
        }
    }

    public void join(Callback<Lobby> callback) {
        synchronized (lock) {
            if (state == STATE_CREATED) { // avoid putting to queue, pass lobby directly instead
                callback.onError(new Exception("No pending connection attempt"));
                return;
            }
            if (state == STATE_OPEN) { // avoid putting to queue, pass lobby directly instead
                callback.onSuccess(activeLobby);
                return;
            }
            waitingCallbacks.add(callback);
        }
    }

    public void registerDisconnectHandler(SafeCallback<LobbyCoordinatorService> callback) {
        synchronized (lock) {
            disconnectHandlers.add(callback);
        }
    }

    public void unregisterDisconnectHandler(SafeCallback<LobbyCoordinatorService> callback) {
        synchronized (lock) {
            disconnectHandlers.remove(callback);
        }
    }

    public void awaitClose(Activity activity, Callback<Void> callback) {
        synchronized (lock) {
            if (state != STATE_OPEN) {
                callback.onError(new Exception("No active connection"));
                return;
            }

            disconnectHandlers.add(new Callback<LobbyCoordinatorService>() {
                @Override
                public void onError(Exception e) {
                    if (callback != null) callback.onError(e);
                }

                @Override
                public void onSuccess(LobbyCoordinatorService lobbyCoordinatorService) {
                    if (callback != null) callback.onSuccess(null);
                }
            });
            state = STATE_CLOSING;
            activity.stopService(new Intent(activity, ClientLobbyService.class));
            activity.stopService(new Intent(activity, ServerLobbyService.class));
        }
    }

    private void handleLobbyState(Activity activity,
                                  Lobby lobby,
                                  ServiceConnection connection,
                                  Callback<Lobby> callback) {

        synchronized (lock) {
            if (lobby.getConnectionState() == Lobby.STATE_CLOSED) {
                handleError(activity, connection, new Exception(state == STATE_CREATING_SERVER ? "Failed to create server" : "Failed to connect to the server"), callback);
            } else if (lobby.getConnectionState() == Lobby.STATE_OPEN) {
                handleConnect(lobby, callback);
            } else lobby.addEventListener(new LobbyEventListener() {
                @Override
                public void onConnected(Lobby lobby) {
                    lobby.removeEventListener(this);
                    activity.runOnUiThread(() -> handleConnect(lobby, callback));
                }

                @Override
                public void onError(Lobby lobby, Exception e) {
                    lobby.removeEventListener(this);
                    activity.runOnUiThread(() -> handleError(activity, connection, e, callback));
                }

                @Override
                public void onInitializationFailed(Lobby lobby, Exception cause) {
                    lobby.removeEventListener(this);
                    activity.runOnUiThread(() -> handleError(activity, connection, cause, callback));
                }
            });
        }
    }

    private void handleConnect(Lobby lobby, Callback<Lobby> callback) {
        activeLobby = lobby;
        state = STATE_OPEN;
        StateLock<Lobby> lobbyStateLock = new StateLock<>(lobby);
        activeLobby.addEventListener(new LobbyEventListener() {
            @Override
            public void onDisconnect(Lobby lobby, int code, String reason) {
                if (lobbyStateLock.verify(activeLobby)) {
                    synchronized (lock) {
                        for (SafeCallback<LobbyCoordinatorService> c : new ArrayList<>(disconnectHandlers))
                            c.onSuccess(LobbyCoordinatorService.this);

                        state = STATE_CREATED;
                        activeLobby = null;
                    }
                }
            }

            @Override
            public void onError(Lobby lobby, Exception e) {
                onDisconnect(lobby, 0, null);
            }
        });

        callback.onSuccess(activeLobby);
        for (Callback<Lobby> c : waitingCallbacks) c.onSuccess(activeLobby);
    }

    private void handleError(Activity activity, ServiceConnection connection, Exception e, Callback<Lobby> callback) {
        activeLobby = null;
        state = STATE_CREATED;

        callback.onError(e);
        for (Callback<Lobby> c : waitingCallbacks) c.onError(e);

        activity.unbindService(connection);
        activity.stopService(new Intent(activity, ClientLobbyService.class));
        activity.stopService(new Intent(activity, ServerLobbyService.class));
    }

    private boolean isMyServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private static class AbstractHeadLobby implements Lobby {
        private String title;
        private List<LobbyMember> members;
        private LobbyMember host;

        public AbstractHeadLobby(String title, List<LobbyMember> members, LobbyMember host) {
            this.title = title;
            this.members = members;
            this.host = host;
        }

        @Override
        public void addEventListener(LobbyEventListener listener) { }

        @Override
        public void removeEventListener(LobbyEventListener listener) { }

        @Override
        public void changeTitle(String newName, Callback<Lobby> callback) {
            if (callback != null) callback.onError(new UnsupportedOperationException("This lobby doesn't support title change"));
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public List<LobbyMember> getMembers() {
            return members;
        }

        @Override
        public LobbyMember getHost() {
            return host;
        }

        @Override
        public LobbyMember getClient() {
            throw new UnsupportedOperationException("Not connected");
        }

        @Override
        public QueueLooper getLooper() {
            throw new UnsupportedOperationException("Not connected");
        }

        @Override
        public LibraryProvider getLibrary() {
            throw new UnsupportedOperationException("Not connected");
        }

        @Override
        public int getPlayerState() {
            return 0;
        }

        @Override
        public int getConnectionState() {
            return Lobby.STATE_CLOSED;
        }

        @Override
        public VolumeControl getVolumeControl() {
            return null;
        }

        @Override
        public List<ActionBoardItem> getBoard() { return null; }
    }
}