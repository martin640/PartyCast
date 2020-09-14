package sk.martin64.partycast.server;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

import sk.martin64.partycast.core.Lobby;
import sk.martin64.partycast.core.Queue;
import sk.martin64.partycast.core.RemoteMedia;

public class ServerLocalAudioFileRef implements RemoteMedia, JSONable {

    private String title, artist;
    private ServerLobbyMember requester;
    private File src;
    private ServerQueue queue;
    int id;
    long length, start;

    long lastKnownProgress, lastUpdate;


    public ServerLocalAudioFileRef(ServerLobbyMember requester, File src, long length, String title, String artist, ServerQueue queue) {
        this.requester = requester;
        this.src = src;
        this.queue = queue;
        this.length = length;
        this.title = title;
        this.artist = artist;
    }

    public File getFile() {
        return src;
    }

    @Override
    public ServerLobbyMember getRequester() {
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
    public int getId() {
        return id;
    }

    @Override
    public long getDuration() {
        return length;
    }

    @Override
    public long getStartTime() {
        return RemoteMedia.calculateStart(this, start);
    }

    private long getActualProgress() {
        if (lastUpdate == 0) return 0;

        if (getQueue().getLooper().getContext().getPlayerState() == Lobby.PLAYBACK_PLAYING) {
            return lastKnownProgress + (System.currentTimeMillis() - lastUpdate);
        } else return lastKnownProgress;
    }

    @Override
    public float getProgress() {
        return length > 0 ? ((float) getActualProgress() / length) : 0f;
    }

    @Override
    public Queue getQueue() {
        return queue;
    }

    @Override
    public void toJSON(JSONObject out) throws JSONException {
        out.put("class", "RemoteMedia")
                .put("values", new JSONObject()
                        .put("requester", requester.getId())
                        .put("id", id)
                        .put("title", title)
                        .put("artist", artist)
                        .put("length", length)
                        .put("start", start)
                        .put("lastKnownProgress", getActualProgress())
                );
    }
}
