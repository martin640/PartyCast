package sk.martin64.partycast.server;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

import fi.iki.elonen.NanoHTTPD;
import sk.martin64.partycast.core.LibraryItem;
import sk.martin64.partycast.core.LibraryProvider;
import sk.martin64.partycast.core.Lobby;
import sk.martin64.partycast.core.LobbyEventListener;
import sk.martin64.partycast.utils.Callback;

public class LocalLibraryProvider implements LibraryProvider, JSONable {

    private final Object lock;
    private NanoHTTPD imageServer;
    private List<LocalLibraryItem> items;
    private ServerLobby lobby;
    private ContentResolver resolver;

    int providerPort;

    public LocalLibraryProvider(int isp, ContentResolver resolver, ServerLobby lobby) {
        this.lock = new Object();
        this.lobby = lobby;
        this.items = new ArrayList<>();
        this.resolver = resolver;
        this.imageServer = new NanoHTTPD(this.providerPort = isp) {
            @Override
            public Response serve(IHTTPSession session) {
                String q = session.getUri();
                if (q.startsWith("/art/")) {
                    String id = q.substring(5);
                    int idi = Integer.parseInt(id);
                    LocalLibraryItem item = lobby.getLibraryItemById(idi);
                    if (item != null && item.uri != null) {
                        File imageFile = new File(item.uri.toString());
                        if (!imageFile.exists())
                            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Failed to fetch artwork: File doesn't exist");

                        try (FileInputStream fis = new FileInputStream(imageFile)) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            byte[] buffer = new byte[2048];
                            int read;
                            while ((read = fis.read(buffer)) > 0) {
                                baos.write(buffer, 0, read);
                            }

                            return newFixedLengthResponse(Response.Status.OK, "image/jpeg",
                                    new ByteArrayInputStream(baos.toByteArray()), baos.size());
                        } catch (IOException e) {
                            e.printStackTrace();
                            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Failed to fetch artwork: IOException");
                        }
                    } else return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Failed to fetch artwork: No such resource");
                }

                return super.serve(session);
            }
        };
        try {
            this.imageServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start image provider server", e);
        }
        refresh(null);
    }

    @Override
    public void refresh(Callback<LibraryProvider> callback) {
        Executors.newSingleThreadExecutor().submit(() -> {
            synchronized (lock) {
                items.clear();

                Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                String[] projection = {
                        MediaStore.Audio.AudioColumns.DATA,
                        MediaStore.Audio.AudioColumns.TITLE,
                        MediaStore.Audio.AudioColumns.ALBUM,
                        MediaStore.Audio.ArtistColumns.ARTIST,
                        MediaStore.Audio.AudioColumns.ALBUM_ID,
                };
                Cursor c = resolver.query(uri, projection, null, null, null);

                if (c != null) {
                    while (c.moveToNext()) {
                        String path = c.getString(0);
                        String name = c.getString(1);
                        String album = c.getString(2);
                        String artist = c.getString(3);
                        String albumId = c.getString(4);
                        Uri art = null;

                        Cursor cursorAlbum = resolver.query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                                new String[] {
                                        MediaStore.Audio.Albums._ID,
                                        MediaStore.Audio.Albums.ALBUM_ART
                                },
                                MediaStore.Audio.Albums._ID + "=" + albumId, null, null);

                        if (cursorAlbum != null && cursorAlbum.moveToFirst()) {
                            String albumUri = cursorAlbum.getString(cursorAlbum.getColumnIndex("album_art"));
                            cursorAlbum.close();
                            if (albumUri != null)
                                art = Uri.parse(albumUri);
                        }

                        LocalLibraryItem item = new LocalLibraryItem(art, name, artist, album, path, this);
                        lobby.registerLibItem(item);

                        items.add(item);
                    }
                    c.close();
                }
                Collections.sort(items, (o1, o2) -> o1.getTitle().compareTo(o2.getTitle()));

                if (callback != null)
                    callback.onSuccess(this);

                lobby.broadcastEvent("Event.LIBRARY_UPDATED", LocalLibraryProvider.this);

                for (LobbyEventListener l : lobby.safeListenersCopy())
                    l.onLibraryUpdated(lobby, this);

            }
        });
    }

    @Override
    public List<LibraryItem> getAll() {
        synchronized (lock) {
            return Collections.unmodifiableList(items);
        }
    }

    @Override
    public String getName() {
        return "Local library";
    }

    @Override
    public Lobby getContext() {
        return lobby;
    }

    @Override
    public void toJSON(JSONObject out) throws JSONException {
       synchronized (lock) {
           JSONArray media = new JSONArray();
           for (LocalLibraryItem med : this.items) {
               JSONObject dataJson = new JSONObject();
               med.toJSON(dataJson);
               media.put(dataJson);
           }

           out.put("class", "LibraryProvider")
                   .put("values", new JSONObject()
                           .put("items", media)
                           .put("name", getName())
                   );
       }
    }
}