package moe.ono.util;

import static moe.ono.constants.Constants.PrekEnableLog;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import moe.ono.config.ConfigManager;
import moe.ono.util.io.FileUtils;

public class LogUtils {

    private static final long LOG_RETENTION_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long CLEANUP_INTERVAL_MS = 6L * 60 * 60 * 1000;
    private static volatile long sLastCleanupTimestamp;

    private LogUtils() {
    }

    @NonNull
    public static String getLogRootDirectory() {
        return new File(PathTool.getModuleDataPath(), "log").getAbsolutePath() + File.separator;
    }

    @NonNull
    public static String getRunLogDirectory() {
        return new File(getLogRootDirectory(), "RunLog").getAbsolutePath() + File.separator;
    }

    @NonNull
    public static String getErrorLogDirectory() {
        return new File(getLogRootDirectory(), "ErrorLog").getAbsolutePath() + File.separator;
    }

    public static long getLogDirectorySize() {
        return FileUtils.getDirSize(new File(getLogRootDirectory()));
    }

    public static boolean clearLogs() {
        try {
            File rootDirectory = new File(getLogRootDirectory());
            if (!rootDirectory.exists()) {
                return true;
            }
            FileUtils.deleteFile(rootDirectory);
            return !rootDirectory.exists();
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * @return 获取调用此方法的调用者
     */
    public static String getCallStack() {
        Throwable throwable = new Throwable();
        return getStackTrace(throwable);
    }

    /**
     * 获取堆栈跟踪
     *
     * @param throwable new Throwable || Exception
     * @return 堆栈跟踪
     */
    public static String getStackTrace(@NonNull Throwable throwable) {
        StringBuilder result = new StringBuilder();
        result.append(throwable).append("\n");
        StackTraceElement[] stackTraceElements = throwable.getStackTrace();
        for (StackTraceElement stackTraceElement : stackTraceElements) {
            if (stackTraceElement.getClassName().equals(LogUtils.class.getName())) {
                continue;
            }
            result.append(stackTraceElement).append("\n");
        }
        return result.toString();
    }

    public static void addError(Throwable e) {
        writeLog(android.util.Log.ERROR, "Error Log", e == null ? "null" : e.toString(), e);
    }

    public static void addRunLog(Object content) {
        addRunLog("Run Log", content);
    }

    public static void addRunLog(String tag, Object content) {
        writeLog(android.util.Log.DEBUG, tag, String.valueOf(content), null);
    }

    public static void addError(String tag, Throwable e) {
        writeLog(android.util.Log.ERROR, tag, e == null ? "null" : e.toString(), e);
    }

    public static void addError(String tag, String description, Throwable e) {
        writeLog(android.util.Log.ERROR, tag, description, e);
    }

    public static void addError(String tag, String msg) {
        writeLog(android.util.Log.ERROR, tag, msg, null);
    }

    public static void writeLog(int priority, @Nullable String tag, @Nullable String message, @Nullable Throwable throwable) {
        if (!isFileLoggingEnabled()) {
            return;
        }

        cleanupExpiredLogsIfNeeded();

        boolean isError = priority >= android.util.Log.ERROR;
        String safeTag = sanitizeTag(tag);
        String logDirectory = isError ? getErrorLogDirectory() : getRunLogDirectory();
        String fileName = safeTag + '_' + getDate() + ".log";
        String path = new File(logDirectory, fileName).getAbsolutePath();

        StringBuilder builder = new StringBuilder();
        builder.append(getTime())
                .append(' ')
                .append(levelOf(priority))
                .append('/').append(safeTag)
                .append(" [thread=").append(Thread.currentThread().getName()).append("]")
                .append('\n')
                .append(message == null ? "null" : message);
        if (throwable != null) {
            builder.append('\n').append(android.util.Log.getStackTraceString(throwable));
        }
        builder.append("\n\n");
        FileUtils.writeTextToFile(path, builder.toString(), true);
    }

    public static String getTime() {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat df = new SimpleDateFormat("[yyyy/MM/dd HH:mm:ss]", Locale.getDefault());
        Calendar calendar = Calendar.getInstance();
        return df.format(calendar.getTime());
    }

    @NonNull
    private static String getDate() {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return df.format(Calendar.getInstance().getTime());
    }

    private static boolean isFileLoggingEnabled() {
        try {
            return ConfigManager.getDefaultConfig().getBooleanOrFalse(PrekEnableLog);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @NonNull
    private static String sanitizeTag(@Nullable String tag) {
        String fallbackTag = "common";
        if (tag == null) {
            return fallbackTag;
        }
        String normalizedTag = tag.trim();
        if (normalizedTag.isEmpty()) {
            return fallbackTag;
        }
        String safeTag = normalizedTag.replaceAll("[\\\\/:*?\"<>|\s]+", "_");
        if (safeTag.length() > 64) {
            return safeTag.substring(0, 64);
        }
        return safeTag;
    }

    @NonNull
    private static String levelOf(int priority) {
        switch (priority) {
            case android.util.Log.VERBOSE:
                return "V";
            case android.util.Log.DEBUG:
                return "D";
            case android.util.Log.INFO:
                return "I";
            case android.util.Log.WARN:
                return "W";
            case android.util.Log.ERROR:
                return "E";
            case android.util.Log.ASSERT:
                return "A";
            default:
                return "U";
        }
    }

    private static void cleanupExpiredLogsIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - sLastCleanupTimestamp < CLEANUP_INTERVAL_MS) {
            return;
        }
        synchronized (LogUtils.class) {
            if (now - sLastCleanupTimestamp < CLEANUP_INTERVAL_MS) {
                return;
            }
            deleteExpiredFiles(new File(getRunLogDirectory()), now);
            deleteExpiredFiles(new File(getErrorLogDirectory()), now);
            sLastCleanupTimestamp = now;
        }
    }

    private static void deleteExpiredFiles(@NonNull File directory, long now) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file == null || !file.isFile()) {
                continue;
            }
            if (now - file.lastModified() > LOG_RETENTION_MS) {
                try {
                    file.delete();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
