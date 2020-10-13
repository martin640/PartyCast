package partycast.model;

import java.util.List;

import sk.martin64.partycast.utils.Callback;

public interface LibraryProvider {

    void refresh(Callback<LibraryProvider> callback);

    List<LibraryItem> getAll();

    String getName();

    Lobby getContext();

    int pushIdFromPool();

    LibraryItem findItemById(int id);
}