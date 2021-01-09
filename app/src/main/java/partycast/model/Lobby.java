package partycast.model;

import java.util.List;

import sk.martin64.partycast.utils.Callback;

public interface Lobby {

    void addEventListener(LobbyEventListener listener);

    void removeEventListener(LobbyEventListener listener);

    void changeTitle(String newName, Callback<Lobby> callback);

    String getTitle();

    List<LobbyMember> getMembers();

    LobbyMember getHost();

    LobbyMember getClient();

    QueueLooper getLooper();

    LibraryProvider getLibrary();

    VolumeControl getVolumeControl();

    List<ActionBoardItem> getBoard();

    int getPlayerState();

    int getConnectionState();

    default LobbyMember getMemberById(int id) {
        for (LobbyMember member : getMembers()) {
            if (member.getId() == id) return member;
        }
        return null;
    }

    static boolean isHost(LobbyMember member) {
        return member != null && (member.getContext().getHost().getId() == member.getId());
    }

    static boolean isSelf(LobbyMember member) {
        return member != null && (member.getContext().getClient().getId() == member.getId());
    }

    static boolean checkPermission(LobbyMember member, int perm) {
        return member != null && ((member.getPermissions() & perm) == perm);
    }

    int STATE_CREATED = 0;
    int STATE_CONNECTING = 1;
    int STATE_OPEN = 2;
    int STATE_CLOSED = 3;

    int PLAYBACK_READY = 0;
    int PLAYBACK_PLAYING = 1;
    int PLAYBACK_PAUSED = 2;
}