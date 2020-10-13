package partycast.server;

import org.json.JSONException;
import org.json.JSONObject;

public interface JSONable {

    void toJSON(JSONObject out) throws JSONException;
}