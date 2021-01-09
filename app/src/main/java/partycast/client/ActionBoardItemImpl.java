package partycast.client;

import org.json.JSONException;
import org.json.JSONObject;

import partycast.model.ActionBoardItem;
import sk.martin64.partycast.utils.Callback;

public class ActionBoardItemImpl implements ActionBoardItem {
    private final ClientLobby lobby;
    private final int id;
    private int type, input;
    private boolean clickable;
    private String title, body;

    public ActionBoardItemImpl(ClientLobby lobby, JSONObject data) {
        this.lobby = lobby;
        this.id = data.optInt("id");
        update(data);
    }

    void update(JSONObject data) {
        if (data.has("itemType")) this.type = data.optInt("itemType");
        if (data.has("inputType")) this.input = data.optInt("inputType");
        if (data.has("clickable")) this.clickable = data.optBoolean("clickable");
        if (data.has("title")) this.title = data.optString("title");
        if (data.has("body")) this.body = data.optString("body");
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public int getItemType() {
        return type;
    }

    @Override
    public int getInputType() {
        return input;
    }

    @Override
    public boolean isClickable() {
        return clickable;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getBody() {
        return body;
    }

    @Override
    public void submit(Object input, Callback<String> callback) {
        try {
            lobby.request("ActionBoard.SUBMIT",
                    new JSONObject()
                            .put("id", id)
                            .put("value", input),
                    new Callback<JSONObject>() {
                        @Override
                        public void onSuccess(JSONObject jsonObject) {
                            if (callback != null) callback.onSuccess(null);
                        }

                        @Override
                        public void onError(Exception e) {
                            if (callback != null) callback.onError(e);
                        }
                    });
        } catch (JSONException e) {
            callback.onError(e);
        }
    }
}