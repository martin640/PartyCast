package partycast.model;

public interface LibraryItem {

    /**
     * @return unique item id provided by lobby
     */
    int getUniqueId();

    /**
     * @return item title
     */
    String getTitle();

    /**
     * @return item artist
     */
    String getArtist();

    /**
     * @return item album
     */
    String getAlbum();

    /**
     * @return url of item artwork/image/preview
     */
    String getImageUrl();

    LibraryProvider getProvider();
}