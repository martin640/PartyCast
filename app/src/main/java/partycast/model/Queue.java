package partycast.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public interface Queue {

    /**
     * @return id of queue
     */
    int getId();

    /**
     * @return currently playing media or null
     */
    @Nullable
    RemoteMedia getCurrentlyPlaying();

    /**
     * @param id of media
     * @return media by id or null
     */
    @Nullable
    RemoteMedia getQueryById(int id);

    /**
     * @return all media in queue
     */
    @NonNull
    List<RemoteMedia> getAll();

    /**
     * @return current media and all future
     */
    @NonNull
    List<RemoteMedia> getPending();

    /**
     * @return parent queue looper
     */
    QueueLooper getLooper();
}