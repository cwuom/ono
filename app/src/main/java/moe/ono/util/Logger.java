package moe.ono.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.robv.android.xposed.XposedBridge;
import moe.ono.BuildConfig;

public class Logger {

    private static final String TAG = BuildConfig.TAG;

    private Logger() {
    }

    public static void e(@NonNull String msg) {
        log(android.util.Log.ERROR, null, msg, null);
    }

    public static void e(String tag, @NonNull String msg) {
        log(android.util.Log.ERROR, tag, msg, null);
    }

    public static void w(@NonNull String msg) {
        log(android.util.Log.WARN, null, msg, null);
    }

    public static void w(String tag, @NonNull String msg) {
        log(android.util.Log.WARN, tag, msg, null);
    }

    public static void i(@NonNull String msg) {
        log(android.util.Log.INFO, null, msg, null);
    }

    public static void i(String tag, @NonNull String msg) {
        log(android.util.Log.INFO, tag, msg, null);
    }

    public static void d(@NonNull String msg) {
        log(android.util.Log.DEBUG, null, msg, null);
    }

    public static void d(String tag, @NonNull String msg) {
        log(android.util.Log.DEBUG, tag, msg, null);
    }

    public static void v(@NonNull String msg) {
        log(android.util.Log.VERBOSE, null, msg, null);
    }

    public static void v(String tag, @NonNull String msg) {
        log(android.util.Log.VERBOSE, tag, msg, null);
    }

    public static void e(@NonNull Throwable e) {
        log(android.util.Log.ERROR, null, e.toString(), e);
    }

    public static void w(@NonNull Throwable e) {
        log(android.util.Log.WARN, null, e.toString(), e);
    }

    public static void i(@NonNull Throwable e) {
        log(android.util.Log.INFO, null, e.toString(), e);
    }

    public static void i(@NonNull Throwable e, boolean output) {
        log(android.util.Log.INFO, null, e.toString(), e);
        if (output) {
            XposedBridge.log(e);
        }
    }

    public static void d(@NonNull Throwable e) {
        log(android.util.Log.DEBUG, null, e.toString(), e);
    }

    public static void e(@NonNull String msg, @NonNull Throwable e) {
        log(android.util.Log.ERROR, msg, msg, e);
    }

    public static void w(@NonNull String msg, @NonNull Throwable e) {
        log(android.util.Log.WARN, msg, msg, e);
    }

    public static void i(@NonNull String msg, @NonNull Throwable e) {
        log(android.util.Log.INFO, msg, msg, e);
    }

    public static void d(@NonNull String msg, @NonNull Throwable e) {
        log(android.util.Log.DEBUG, msg, msg, e);
    }

    @NonNull
    public static String getStackTraceString(@NonNull Throwable th) {
        return android.util.Log.getStackTraceString(th);
    }

    private static void log(int priority, @Nullable String subTag, @NonNull String msg, @Nullable Throwable throwable) {
        String resolvedTag = subTag == null || subTag.trim().isEmpty() ? TAG : subTag.trim();
        String displayMessage = resolvedTag.equals(TAG) || resolvedTag.equals(msg) ? msg : resolvedTag + ": " + msg;

        if (throwable == null) {
            android.util.Log.println(priority, TAG, displayMessage);
        } else {
            android.util.Log.println(priority, TAG, displayMessage + '\n' + android.util.Log.getStackTraceString(throwable));
        }

        LogUtils.writeLog(priority, resolvedTag, msg, throwable);
    }
}
