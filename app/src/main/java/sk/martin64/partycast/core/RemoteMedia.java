package sk.martin64.partycast.core;

import java.util.ArrayList;
import java.util.List;

public interface RemoteMedia {

    /**
     * @return lobby member which requested this media
     */
    LobbyMember getRequester();

    String getTitle();

    String getArtist();

    /**
     * @return id of media
     */
    int getId();

    /**
     * @return duration of audio/video in milliseconds
     */
    long getDuration();

    /**
     * @return (in milliseconds) start of playback if playing/played,
     * otherwise approximate start time is calculated (or 0 if playback is paused/stopped)
     */
    long getStartTime();

    float getProgress();

    Queue getQueue();

    static long calculateStart(RemoteMedia media, long actualStart) {
        QueueLooper looper = media.getQueue().getLooper();
        Lobby lobby = looper.getContext();
        if (lobby.getPlayerState() == Lobby.PLAYBACK_PLAYING) {
            if (actualStart > 0) return actualStart;
            else {
                List<RemoteMedia> pending = new ArrayList<>();
                for (Queue queue : looper.getPending()) {
                    pending.addAll(queue.getPending());
                }
                pending = pending.subList(0, pending.indexOf(media));
                if (pending.size() == 0) return 0;

                RemoteMedia first = pending.get(0);
                long relativePoint = first.getStartTime() + first.getDuration();
                for (int i = 1; i < pending.size(); i++) {
                    relativePoint += pending.get(i).getDuration();
                }
                return relativePoint;
            }
        } else return 0;
    }
}