package partycast.client;

import org.json.JSONException;
import org.json.JSONObject;

import partycast.model.Lobby;
import partycast.model.LobbyMember;
import partycast.model.Queue;
import partycast.model.RemoteMedia;

public class ClientRemoteMedia implements RemoteMedia {

    private LobbyMember requester;
    private String title, artist, artwork;
    private int id;
    private long length;
    private ClientQueue queue;

    long lastKnownProgress, lastUpdate;

    ClientRemoteMedia(JSONObject data, ClientQueue queue) {
        this.queue = queue;
        update(data);
    }

    @SuppressWarnings("ConstantConditions")
    void update(JSONObject data) {
        if ("RemoteMedia".equals(data.optString("class")) && data.has("values")) {
            try {
                JSONObject values = data.getJSONObject("values");

                this.title = values.optString("title");
                this.artist = values.optString("artist");
                this.artwork = values.optString("artwork", null);
                if (this.artwork != null) {
                    this.artwork = this.artwork.replaceAll("\\[HOST]", queue.getLooper().getContext().getHost().getAddressString());
                }
                this.length = values.optLong("length");
                this.id = values.optInt("id");
                int requester = values.optInt("requester");
                ClientLobby clientLobby = (ClientLobby) queue.getLooper().getContext();
                this.requester = clientLobby.findMemberInCache(requester);

                this.lastKnownProgress = values.optLong("progress");
                this.lastUpdate = System.currentTimeMillis();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else throw new IllegalArgumentException("Supplied data is not for RemoteMedia");
    }

    @Override
    public LobbyMember getRequester() {
        return requester;
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
    public String getArtwork() {
        return artwork;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public long getDuration() {
        return length;
    }

    @Override
    public long getStartTime() {
        return RemoteMedia.calculateStart(this);
    }

    @Override
    public long getProgressReal() {
        if (lastUpdate == 0) return 0;

        if (getQueue().getLooper().getContext().getPlayerState() == Lobby.PLAYBACK_PLAYING) {
            return lastKnownProgress + (System.currentTimeMillis() - lastUpdate);
        } else return lastKnownProgress;
    }

    @Override
    public Queue getQueue() {
        return queue;
    }
}