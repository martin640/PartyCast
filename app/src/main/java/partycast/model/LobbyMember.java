package partycast.model;

import java.net.InetAddress;

import sk.martin64.partycast.utils.Callback;

public interface LobbyMember {

    int PERMISSION_CHANGE_NAME = 1;
    int PERMISSION_QUEUE = 2;
    int PERMISSION_MEMBER_LIST = 4;
    int PERMISSION_MANAGE_USERS = 8;
    int PERMISSION_MANAGE_QUEUE = 16;
    int PERMISSION_OWNER = 64;
    int PERMISSION_HOST = 0b111111111111111111111111111111;

    int PERMISSIONS_DEFAULT = PERMISSION_CHANGE_NAME | PERMISSION_QUEUE | PERMISSION_MEMBER_LIST;
    int PERMISSIONS_MOD = PERMISSIONS_DEFAULT | PERMISSION_MANAGE_USERS | PERMISSION_MANAGE_QUEUE;

    String getName();

    String getUserAgent();

    int getId();

    int getPermissions();

    InetAddress getAddress();

    default String getAddressString() {
        return getAddress() != null ? getAddress().getHostAddress() : null;
    }

    Lobby getContext();

    void changeName(String name, Callback<LobbyMember> callback);

    void changePermissions(int permissions, Callback<LobbyMember> callback);

    void kick(Callback<Void> callback);

    void ban(Callback<Void> callback);
}