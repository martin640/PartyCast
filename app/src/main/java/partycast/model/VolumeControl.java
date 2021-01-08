package partycast.model;

public interface VolumeControl {
    float getLevel();
    boolean isMuted();

    /**
     * Sets volume level to given value. Method should never thrown an exception.
     * @param v new value in range 0-1
     */
    void setLevel(float v);

    /**
     * Sets muted flag to given value. Method should never thrown an exception.
     * @param m new state
     */
    void setMuted(boolean m);
}