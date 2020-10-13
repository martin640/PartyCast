package sk.martin64.partycast.ui;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.TextView;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Locale;

public final class UiHelper {

    public static CharSequence span(CharSequence toSpan, Object span) {
        SpannableString s = new SpannableString(toSpan);
        s.setSpan(span, 0, s.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        return s;
    }

    public static void html(TextView tv, String html, UrlClickListener clickAction) {
        Spanned src;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            src = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT);
        } else {
            src = Html.fromHtml(html);
        }

        SpannableString current = new SpannableString(src);
        URLSpan[] spans = current.getSpans(0, current.length(), URLSpan.class);

        for (URLSpan span : spans) {
            int start = current.getSpanStart(span);
            int end = current.getSpanEnd(span);

            current.removeSpan(span);
            current.setSpan(new CustomURLSpan(span.getURL(), clickAction), start, end, 0);
        }
        tv.setText(current);
        tv.setMovementMethod(LinkMovementMethod.getInstance());
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

    public static void runOnUiCompact(Runnable runnable) {
        if (Looper.getMainLooper() != Looper.myLooper()) {
            new Handler(Looper.getMainLooper()).post(runnable);
        } else runnable.run();
    }

    private static class CustomURLSpan extends URLSpan {
        private UrlClickListener listener;
        public CustomURLSpan(String url, UrlClickListener listener) {
            super(url);
            this.listener = listener;
        }

        @Override
        public void onClick(View widget) {
            listener.onClick((TextView) widget, getURL());
        }
    }
    public interface UrlClickListener {
        void onClick(TextView view, String url);
    }
}