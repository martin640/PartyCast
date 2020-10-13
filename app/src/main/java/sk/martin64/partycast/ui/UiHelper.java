package sk.martin64.partycast.ui;

import android.text.SpannableString;
import android.text.Spanned;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Locale;

public final class UiHelper {

    public static CharSequence span(CharSequence toSpan, Object span) {
        SpannableString s = new SpannableString(toSpan);
        s.setSpan(span, 0, s.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        return s;
    }

    public static String humanReadableByteCountSI(long bytes) {
        if (-1000 < bytes && bytes < 1000) {
            return bytes + " B";
        }
        CharacterIterator ci = new StringCharacterIterator("kMGTPE");
        while (bytes <= -999_950 || bytes >= 999_950) {
            bytes /= 1000;
            ci.next();
        }
        return String.format(Locale.getDefault(), "%.1f %cB", bytes / 1000.0, ci.current());
    }
}