package partycast.client;

import org.json.JSONException;
import org.json.JSONObject;

import partycast.model.LibraryItem;
import partycast.model.LibraryProvider;

public class ClientRemoteLibraryItem implements LibraryItem {

    private String title, artist, album, imageUrl;
    private int id;
    private ClientRemoteLibraryProvider provider;

    ClientRemoteLibraryItem(JSONObject data, ClientRemoteLibraryProvider provider) {
        this.provider = provider;
        update(data);
    }

    void update(JSONObject data) {
        if ("LibraryItem".equals(data.optString("class")) && data.has("values")) {
            try {
                JSONObject values = data.getJSONObject("values");

                this.id = values.optInt("id");
                this.title = values.optString("title");
                this.artist = values.optString("artist");
                this.album = values.optString("album");

                this.imageUrl = values.optString("imageUrl", null);
                if (this.imageUrl != null) {
                    this.imageUrl = this.imageUrl.replaceAll("\\[HOST]", provider.getContext().getHost().getAddressString());
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else throw new IllegalArgumentException("Supplied data is not for LibraryItem");
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
        return imageUrl;
    }

    @Override
    public LibraryProvider getProvider() {
        return provider;
    }
}
