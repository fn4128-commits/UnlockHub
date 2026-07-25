package com.jinxin.unlockhub.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.jinxin.unlockhub.R;

public final class ShareActions {
    private ShareActions() {
    }

    public static String statusPageUrl(Context context) {
        return Prefs.backendUrl(context) + "/?syncId=" + Uri.encode(Prefs.publicId(context));
    }

    public static String loginPageUrl(Context context) {
        return Prefs.backendUrl(context) + "/login?syncId=" + Uri.encode(Prefs.publicId(context));
    }

    public static String profilePageUrl(Context context) {
        return Prefs.backendUrl(context) + "/profile";
    }

    public static String shareGuideText(Context context) {
        return context.getString(com.jinxin.unlockhub.R.string.sh_guide,
                Prefs.displayName(context), Prefs.publicId(context), statusPageUrl(context));
    }

    public static void copy(Context context, String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
        }
    }

    public static void copyUid(Context context) {
        copy(context, "UnlockHub UID", Prefs.publicId(context));
    }

    public static void copyStatusLink(Context context) {
        copy(context, "UnlockHub status page link", statusPageUrl(context));
    }

    public static void copyShareGuide(Context context) {
        copy(context, "UnlockHub share guide", shareGuideText(context));
    }

    public static void openInBrowser(Context context, String url) {
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    public static void shareGuide(Context context) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_subject));
        intent.putExtra(Intent.EXTRA_TEXT, shareGuideText(context));
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_chooser_title)));
    }
}
