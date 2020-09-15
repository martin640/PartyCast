package sk.martin64.partycast.ui;

import android.text.SpannableString;
import android.text.Spanned;

public final class UiHelper {

    public static CharSequence span(CharSequence toSpan, Object span) {
        SpannableString s = new SpannableString(toSpan);
        s.setSpan(span, 0, s.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        return s;
    }
}