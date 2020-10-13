package sk.martin64.partycast.androidserver;

import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import partycast.model.LibraryItem;
import partycast.model.LibraryProvider;
import partycast.server.JSONable;

public class LocalLibraryItem implements LibraryItem, JSONable {

    Uri uri;
    String title, artist, album, path;
    LocalLibraryProvider provider;
    int id;

    public LocalLibraryItem(Uri uri, String title, String artist, String album, String path, LocalLibraryProvider provider) {
        this.uri = uri;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.path = path;
        this.provider = provider;
    }

    public Uri getUri() {
        return uri;
    }

    public String getPath() {
        return path;
    }

    @Override
    public int getUniqueId() {
        return id;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getArtist() {
        return artist;
    }

    @Override
    public String getAlbum() {
        return album;
    }

    @Override
    public String getImageUrl() {
        return uri != null ? uri.toString() : null;
    }

    public String getRemoteImageUrl() {
        return uri != null ? String.format("http://[HOST]:%s/art/%s", provider.providerPort, id) : null;
    }

    @Override
    public LibraryProvider getProvider() {
        return provider;
    }

    @Override
    public void toJSON(JSONObject out) throws JSONException {
        out.put("class", "LibraryItem")
                .put("values", new JSONObject()
                        .put("id", id)
                        .put("title", title)
                        .put("artist", artist)
                        .put("album", album)
                        .put("imageUrl", getRemoteImageUrl())
                );
    }
}