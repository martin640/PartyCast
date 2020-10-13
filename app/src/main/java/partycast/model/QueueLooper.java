package partycast.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import sk.martin64.partycast.utils.Callback;

public interface QueueLooper {

    void enqueue(LibraryItem item, Callback<Void> callback);
    void enqueue(LibraryItem item, LobbyMember caller, Callback<Void> callback);

    void play(Callback<QueueLooper> callback);
    void pause(Callback<QueueLooper> callback);
    void skip(Callback<QueueLooper> callback);

    /**
     * @return currently playing queue or null
     */
    @Nullable
    Queue getCurrentQueue();

    /**
     * @param id of queue
     * @return queue by id or null
     */
    @Nullable
    Queue getQueryById(int id);

    /**
     * @return all queues
     */
    @NonNull
    List<Queue> getAll();

    /**
     * @return current queue and all future queues
     */
    @NonNull
    List<Queue> getPending();

    /**
     * @return parent lobby
     */
    Lobby getContext();
}