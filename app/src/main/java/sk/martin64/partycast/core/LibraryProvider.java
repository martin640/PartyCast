package sk.martin64.partycast.core;

import java.util.List;

import sk.martin64.partycast.utils.Callback;

public interface LibraryProvider {

    void refresh(Callback<LibraryProvider> callback);

    List<LibraryItem> getAll();

    String getName();

    Lobby getContext();
}