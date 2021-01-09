package partycast.model;

import java.util.List;

public interface RemoteMedia {

    /**
     * @return lobby member which requested this media
     */
    LobbyMember getRequester();

    String getTitle();

    String getArtist();

    /**
     * @return uri linking to local/remote artwork file
     */
    String getArtwork();

    /**
     * @return id of media
     */
    int getId();

    /**
     * @return duration of media in milliseconds
     */
    long getDuration();

    /**
     * @return (in milliseconds) if media is currently playing or has been already played,
     *         implementation should return actual time,
     *         otherwise call static {{@link #calculateApproximateFutureStart(RemoteMedia)}}
     */
    long getStartTime();

    /**
     * @return real progress in milliseconds
     */
    long getProgressReal();

    default float getProgress() {
        long length = getDuration(), progress = getProgressReal();
        return length > 0 ? ((float) progress / length) : 0f;
    }

    Queue getQueue();

    static String optRequester(RemoteMedia media) {
        if (media == null) return "[N/A]";
        LobbyMember r = media.getRequester();
        if (r == null) return "[User left]";
        else return r.getName();
    }

    static long calculateApproximateFutureStart(RemoteMedia media) {
        QueueLooper looper = media.getQueue().getLooper();
        Lobby lobby = looper.getContext();

        if (lobby.getPlayerState() == Lobby.PLAYBACK_PLAYING) {
            long progress = media.getProgressReal();

            if (progress > 0) return System.currentTimeMillis() - progress;
            else {
                List<RemoteMedia> pending = looper.range(looper.getNowPlaying(), media);

                if (pending.size() == 0) return 0;

                RemoteMedia first = pending.get(0);
                long relativePoint = first.getStartTime() + first.getDuration();
                for (int i = 1; i < (pending.size() - 1); i++) {
                    relativePoint += pending.get(i).getDuration();
                }
                return relativePoint;
            }
        } else return 0;
    }
}