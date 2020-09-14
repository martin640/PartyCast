package sk.martin64.partycast.utils;

import java.util.Objects;

public final class StateLock<T> {

    private final T lockedResource;

    public StateLock(T lockedResource) {
        this.lockedResource = lockedResource;
    }

    public boolean verify(T anotherVariable) {
        return Objects.equals(lockedResource, anotherVariable);
    }
}