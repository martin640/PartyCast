package sk.martin64.partycast.core;

public interface LobbyEventListener {

    default void onConnected(Lobby lobby) {}
    default void onUserJoined(Lobby lobby, LobbyMember member) {}
    default void onUserLeft(Lobby lobby, LobbyMember member) {}
    default void onUserUpdated(Lobby lobby, LobbyMember member) {}
    default void onDisconnect(Lobby lobby, int code, String reason) {}
    default void onError(Lobby lobby, Exception e) {}
    default void onLobbyStateChanged(Lobby lobby) {}
    default void onLooperUpdated(Lobby lobby, QueueLooper looper) {}
    default void onLibraryUpdated(Lobby lobby, LibraryProvider libraryProvider) {}
}