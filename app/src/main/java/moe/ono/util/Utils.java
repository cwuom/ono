package moe.ono.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;

import com.tencent.qqnt.kernel.nativeinterface.MsgAttributeInfo;
import com.tencent.qqnt.kernel.nativeinterface.VASMsgAvatarPendant;
import com.tencent.qqnt.kernel.nativeinterface.VASMsgBubble;
import com.tencent.qqnt.kernel.nativeinterface.VASMsgElement;
import com.tencent.qqnt.kernel.nativeinterface.VASMsgFont;
import com.tencent.qqnt.kernel.nativeinterface.VASMsgIceBreak;
import com.tencent.qqnt.kernel.nativeinterface.VASMsgNamePlate;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    private static Handler sHandler;

    public static List<View> getAllViews(Activity act) {
        return getAllChildViews(act.getWindow().getDecorView());
    }

    private static List<View> getAllChildViews(View view) {
        List<View> allChildren = new ArrayList<>();
        if (view instanceof ViewGroup vp) {
            for (int i = 0; i < vp.getChildCount(); i++) {
                View viewChild = vp.getChildAt(i);
                allChildren.add(viewChild);
                allChildren.addAll(getAllChildViews(viewChild));
            }
        }
        return allChildren;
    }

    public static View getViewByDesc(Activity act, String desc, int limit) throws InterruptedException {
        for (int x = 0; x < limit; x++){
            for (View view : getAllViews(act)) {
                try {
                    if (view.getContentDescription().equals(desc)) {
                        return view;
                    }
                } catch (Exception ignored) {}

            }
            Thread.sleep(200);
        }



        return null;
    }

    public static View getViewByDesc(Activity act, String desc) {
        try {
            for (View view : getAllViews(act)) {
                try {
                    if (view.getContentDescription().equals(desc)) {
                        return view;
                    }
                } catch (Exception ignored) {}

            }
        } catch (Exception e) {
            Logger.e(e);
        }


        return null;
    }

    public static void printStackTrace() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        Logger.e("---------------------- [Stack Trace] ----------------------");
        for (StackTraceElement element : stackTrace) {
            Logger.d("    at " + element.toString());
        }
        Logger.e("^---------------------- over ----------------------^");
    }


    public static void printIntentExtras(String TAG, Intent intent) {
        if (intent == null) {
            Logger.e("Intent is null or has no extras.");
            return;
        }

        Logger.i("*-------------------- " + TAG + " --------------------*");
        Bundle extras = intent.getExtras();
        if (extras != null) {
            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                Logger.d(key + " = " + Objects.requireNonNull(value) + "(" + value.getClass() + ")");
            }
        } else {
            Logger.w("No extras found in the Intent.");
        }

        Logger.i("^-------------------- " + "OVER~" + " --------------------^");
    }


    public static MsgAttributeInfo getDefaultAttributeInfo() {

        VASMsgNamePlate plate = new VASMsgNamePlate(258, 64, 0, 0, 0, 0, 258, 0, new ArrayList<>(), 0, 0);
        VASMsgBubble bubble = new VASMsgBubble(0, 0, 0, 0);
        VASMsgFont font = new VASMsgFont(65536, 0L, 0, 0, 0);
        VASMsgAvatarPendant pendant = new VASMsgAvatarPendant();
        VASMsgIceBreak iceBreak = new VASMsgIceBreak(null, null);
        VASMsgElement element = new VASMsgElement(plate, bubble, pendant, font, iceBreak);
        try {
            Class<?> msgAttributeInfoClazz = Initiator.load("com.tencent.qqnt.kernel.nativeinterface.MsgAttributeInfo");
            if (msgAttributeInfoClazz != null) {
                for (Constructor<?> constructor : msgAttributeInfoClazz.getDeclaredConstructors()) {
                    if (constructor.getParameterTypes().length < 1) continue;
                    List<Object> args = new ArrayList<>();
                    args.add(0);
                    args.add(0);
                    args.add(element);
                    for (int i = 0; i < constructor.getParameterTypes().length - 3; i++) {
                        args.add(null);
                    }
                    return (MsgAttributeInfo) constructor.newInstance(args.toArray());
                }
            }
        } catch (Throwable e) {
            Logger.e(e);
        }
        return null;
    }

    public static String[] replaceInvalidLinks(String message) {
        String regex = "!\\[.*?\\]\\((https?://.*?)\\)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(message);

        StringBuffer result = new StringBuffer();
        boolean isReplaced = false;

        String fullUrl = "";
        while (matcher.find()) {
            fullUrl = matcher.group(1);
            if (!fullUrl.startsWith("https://qqbot.ugcimg.cn/")) {
                matcher.appendReplacement(result, matcher.group(0).replace(fullUrl, "[非法链接]"));
                isReplaced = true;
            } else {
                matcher.appendReplacement(result, matcher.group(0));
            }
        }
        matcher.appendTail(result);
        return new String[]{result.toString(), String.valueOf(isReplaced), fullUrl};
    }

    public static Activity getActivityFromView(View view) {
        Context context = view.getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }


    public static Activity getActivityFromContext(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    public static void jump(View v, int hashCode, String webUrl) throws ClassNotFoundException {
        Class<?> browserClass = Initiator.loadClass("com.tencent.mobileqq.activity.QQBrowserDelegationActivity");
        Intent browserIntent = new Intent(v.getContext(), browserClass);

        browserIntent.putExtra("fling_action_key", 2);
        browserIntent.putExtra("fling_code_key", hashCode);
        browserIntent.putExtra("useDefBackText", true);
        browserIntent.putExtra("param_force_internal_browser", true);
        browserIntent.putExtra("url", webUrl);

        v.getContext().startActivity(browserIntent);
    }

    public static void jump(Context context,String webUrl) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(webUrl));
        context.startActivity(intent);
    }

    public static String convertTimestampToDate(long timestamp) {
        Date date = new Date(timestamp);
        @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(date);
    }

    public static Method findMethodByName(Class<?> clazz, String methodName) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        throw new IllegalArgumentException("Method not found: " + methodName);
    }

    public static String timeToFormat(long time) {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(time);
    }

    public static String[] parseURLComponents(String url) {
        String host = "";
        String type = "";
        try {
            URL Url = new URL(url);
            host = Url.getHost();
            type = Url.toURI().getScheme();
        } catch (Exception ignored) {}
        return new String[] {host, type};
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static Object deepGet(Object obj, String path, Object def) {
        try {
            String[] keys = path.split("\\.");

            Object current = obj;

            for (String key : keys) {

                if (current == null) return def;

                if (current instanceof JSONObject json) {

                    if (json.has(key)) {
                        current = json.opt(key);
                        continue;
                    }

                    return def;
                }

                else if (current instanceof JSONArray arr) {

                    if (!key.matches("\\d+")) return def;

                    int index = Integer.parseInt(key);
                    if (index < 0 || index >= arr.length()) return def;

                    current = arr.opt(index);
                }

                else {
                    return def;
                }
            }

            return current != null ? current : def;
        } catch (Exception e) {
            return def;
        }
    }
}
