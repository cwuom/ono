package moe.ono.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import moe.ono.reflex.Reflex;
import moe.ono.util.Logger;
import moe.ono.util.SyncUtils;


public class FaultyDialog {

    private FaultyDialog() {
        throw new AssertionError("No " + getClass().getName() + " instances for you!");
    }

    public static void show(@NonNull Context ctx, @NonNull Throwable e) {
        show(ctx, null, e, true);
    }

    public static void show(@NonNull Context ctx, @Nullable String title, @NonNull Throwable e) {
        show(ctx, title, e, true);
    }

    public static void show(@NonNull Context ctx, @Nullable String title, @NonNull Throwable e, boolean cancelable) {
        Logger.e(e);
        String t = TextUtils.isEmpty(title) ? Reflex.getShortClassName(e) : title;
        SyncUtils.runOnUiThread(() -> showImpl(ctx, t, e, cancelable));
    }

    public static void show(@NonNull Context ctx, @NonNull String title, @NonNull String msg) {
        show(ctx, title, msg, false);
    }

    public static void show(@NonNull Context ctx, @NonNull String title, @NonNull String msg, boolean cancelable) {
        SyncUtils.runOnUiThread(() -> showImpl(ctx, title, msg, cancelable));
    }

    private static void showImpl(@NonNull Context ctx, @NonNull String title, @NonNull Throwable e, boolean cancelable) {
        Context c = CommonContextWrapper.createAppCompatContext(ctx);
        String msg = Logger.getStackTraceString(e);
        new AlertDialog.Builder(c)
                .setTitle(title)
                .setMessage(msg)
                .setCancelable(cancelable)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(android.R.string.copy, (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("error", msg);
                    clipboard.setPrimaryClip(clip);
                })
                .show();
    }

    private static void showImpl(@NonNull Context ctx, @NonNull String title, @NonNull CharSequence msg, boolean cancelable) {
        Context c = CommonContextWrapper.createAppCompatContext(ctx);
        new AlertDialog.Builder(c)
                .setTitle(title)
                .setMessage(msg)
                .setCancelable(cancelable)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
