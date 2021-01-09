package partycast.model;

import sk.martin64.partycast.utils.Callback;

public interface ActionBoardItem {
    int getId();
    int getItemType();
    int getInputType();
    boolean isClickable();
    String getTitle();
    String getBody();
    void submit(Object input, Callback<String> callback);

    int BOARD_ITEM_TYPE_SECTION_TITLE = 0;
    int BOARD_ITEM_TYPE_HTML = 1;
    int BOARD_ITEM_TYPE_BUTTON = 2;
    int BOARD_ITEM_TYPE_OPTION = 3;

    int BOARD_ITEM_INPUT_NONE = 0;
    int BOARD_ITEM_INPUT_ANY = 1;
    int BOARD_ITEM_INPUT_TEXT = 2;
    int BOARD_ITEM_INPUT_NUMBER = 3;
    int BOARD_ITEM_INPUT_TOGGLE = 4;
    int BOARD_ITEM_INPUT_PASSWORD = 5;
}