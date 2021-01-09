package partycast;

import android.util.Log;

public final class $ {
    public static boolean assertObject(Object o, String msg) {
        if (o == null) {
            Log.w("PartyCastUtil", msg);
            return true;
        } else return false;
    }
}