package sk.martin64.partycast.utils;

public interface Callback<T> extends SafeCallback<T> {
    void onError(Exception e);
}