package moe.ono.dexkit;

import static moe.ono.config.ConfigManager.cGetBoolean;
import static moe.ono.config.ConfigManager.cGetInt;
import static moe.ono.config.ConfigManager.cGetString;
import static moe.ono.config.ConfigManager.cPutBoolean;
import static moe.ono.config.ConfigManager.cPutInt;
import static moe.ono.config.ConfigManager.cPutString;
import static moe.ono.constants.Constants.ClazzCacheKey_AbstractQQCustomMenuItem;
import static moe.ono.constants.Constants.MethodCacheKey_AIOParam;
import static moe.ono.constants.Constants.MethodCacheKey_ChatPanelBtn;
import static moe.ono.constants.Constants.MethodCacheKey_InputRoot;
import static moe.ono.constants.Constants.MethodCacheKey_JumpParser;
import static moe.ono.constants.Constants.MethodCacheKey_MarkdownAIO;
import static moe.ono.constants.Constants.MethodCacheKey_getBuddyName;
import static moe.ono.constants.Constants.MethodCacheKey_getDiscussionMemberShowName;
import static moe.ono.util.Initiator.loadClass;
import static moe.ono.util.Initiator.getHostClassLoader;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;

import moe.ono.config.ConfigManager;
import moe.ono.util.Logger;

public class TargetManager {
    private static final int TARGET_CACHE_VERSION = 2;
    private static final String TARGET_CACHE_VERSION_KEY = "targetCacheVersion";

    /* =========================================================
     *  Config helpers
     * ========================================================= */

    public static boolean isNeedFindTarget() {
        return cGetInt(TARGET_CACHE_VERSION_KEY, 0) != TARGET_CACHE_VERSION
                || cGetBoolean("isNeedFindTarget", true);
    }

    public static void setIsNeedFindTarget(boolean b) {
        if (!b) cPutInt(TARGET_CACHE_VERSION_KEY, TARGET_CACHE_VERSION);
        cPutBoolean("isNeedFindTarget", b);
    }

    public static String getLastQQVersion() {
        return cGetString("LastQQVersion", "");
    }

    public static void setLastQQVersion(String version) {
        cPutString("LastQQVersion", version);
    }

    /* =========================================================
     *  Core: batch search in one DexKit execute()
     * ========================================================= */

    public static void runMethodFinder(ApplicationInfo ai, ClassLoader cl, Activity activity, OnTaskCompleteListener cb) {
        new Thread(() -> {
            DexKitExecutor executor = new DexKitExecutor(ai.sourceDir, cl);
            StringBuilder out = new StringBuilder();

            boolean completed = executor.execute((bridge, loader) -> {
                // Methods
                findAndCache(bridge, cl, MethodMatcher.create().usingStrings("rootVMBuild"), MethodCacheKey_AIOParam, out);
                if (!findAndCache(bridge, cl, FindMethod.create()
                        .searchPackages("com.tencent.mobileqq.aio.input")
                        .matcher(MethodMatcher.create()
                                .usingStrings("inputRoot", "sendBtn", "binding", "findViewById(...)")
                                .paramTypes("androidx.viewbinding.ViewBinding").returnType("void")),
                        MethodCacheKey_InputRoot, out)) {
                    findAndCache(bridge, cl, FindMethod.create()
                            .searchPackages("com.tencent.mobileqq.aio.input")
                            .matcher(MethodMatcher.create().usingStrings("inputRoot.findViewById(R.id.send_btn)")),
                            MethodCacheKey_InputRoot, out);
                }
                if (!findAndCache(bridge, cl, MethodMatcher.create()
                        .declaredClass("com.tencent.mobileqq.aio.msglist.holder.component.markdown.MarkdownMsgItem")
                        .usingStrings("AIOMarkdown.parseMarkdown").paramCount(0).returnType("void"),
                        MethodCacheKey_MarkdownAIO, out)) {
                    findAndCache(bridge, cl, MethodMatcher.create()
                            .usingStrings("AIOMarkdownContentComponent", "bind status=").paramCount(2),
                            MethodCacheKey_MarkdownAIO, out);
                }
                findAndCache(bridge, cl, MethodMatcher.create().usingStrings("getBuddyName()"), MethodCacheKey_getBuddyName, out);
                findAndCache(bridge, cl, MethodMatcher.create().usingStrings("getDiscussionMemberShowName uin is null"), MethodCacheKey_getDiscussionMemberShowName, out);
                findAndCache(bridge, cl, MethodMatcher.create()
                        .declaredClass("com.tencent.qqnt.aio.shortcutbar.PanelIconLinearLayout")
                        .usingStrings("peerUid", "panelCallback").returnType("void"), MethodCacheKey_ChatPanelBtn, out);
                findAndCache(bridge, cl, MethodMatcher.create().usingStrings("JumpParser parser Exception =", "keyJumpParserUtilSceneErrorKey"), MethodCacheKey_JumpParser, out);

                // Class
                findAndCacheClass(bridge, cl,
                        ClassMatcher.create().usingStrings("QQCustomMenuItem{title="),
                        "com.tencent.qqnt.aio.menu.ui",
                        ClazzCacheKey_AbstractQQCustomMenuItem,
                        out);
            });

            out.append(completed
                    ? "\n\n搜索结束，已找到的目标已缓存，未找到的目标不会沿用旧缓存"
                    : "\n\n搜索未完成，请重试或查看日志");
            String result = out.toString();
            activity.runOnUiThread(() -> {
                if (cb != null) cb.onTaskComplete(result, completed);
            });
        }).start();
    }

    private static boolean findAndCache(DexKitBridge bridge, ClassLoader cl, MethodMatcher matcher, String key, StringBuilder log) {
        return findAndCache(bridge, cl, FindMethod.create().matcher(matcher), key, log);
    }

    private static boolean findAndCache(DexKitBridge bridge, ClassLoader cl, FindMethod query, String key, StringBuilder log) {
        ConfigManager.getCache().edit().remove(key).apply();
        try {
            MethodData md = bridge.findMethod(query).single();
            Method m = md.getMethodInstance(cl);
            String sig = new DexMethodDescriptor(m).getDescriptor();
            cPutString(key, sig);
            log.append("\n").append(key).append("-> ").append(sig);
            return true;
        } catch (LinkageError e) {
            throw e;
        } catch (Throwable t) {
            log.append("\n").append(key).append(" -> 未找到唯一目标: ").append(t.getClass().getSimpleName());
            Logger.e(key, t);
            return false;
        }
    }

    private static void findAndCacheClass(DexKitBridge bridge, ClassLoader cl, ClassMatcher matcher, String pkg, String key, StringBuilder log) {
        ConfigManager.getCache().edit().remove(key).apply();
        try {
            FindClass fc = FindClass.create().searchPackages(pkg).matcher(matcher);
            ClassData cd = bridge.findClass(fc).singleOrThrow(() -> new IllegalStateException("Non‑unique class"));
            Class<?> clazz = cd.getInstance(cl);
            cPutString(key, clazz.getName());
            log.append("\n").append(key).append("-> ").append(clazz.getName());
        } catch (LinkageError e) {
            throw e;
        } catch (Throwable t) {
            log.append("\n").append(key).append(" -> 未找到唯一目标: ").append(t.getClass().getSimpleName());
            Logger.e(key, t);
        }
    }


    public static void removeAllMethodSignature() {
        ConfigManager cfg = ConfigManager.getCache();
        SharedPreferences.Editor e = cfg.edit();
        for (String k : cfg.getAll().keySet()) if (k.startsWith("method_")) e.remove(k);
        e.apply();
    }

    public static Method requireMethod(String key) {
        String signature = cGetString(key, null);
        if (signature == null) return null;
        try {
            if (signature.contains("->")) {
                return new DexMethodDescriptor(signature).getMethodInstance(getHostClassLoader());
            }
            String[] p = signature.split("#");
            Method result = null;
            for (Method method : loadClass(p[0]).getDeclaredMethods()) {
                if (method.getName().equals(p[1])) {
                    if (result != null) throw new NoSuchMethodException("Ambiguous legacy method: " + signature);
                    result = method;
                }
            }
            return result;
        } catch (Throwable t) {
            Logger.e(key, t);
            return null;
        }
    }

    public static Class<?> requireClazz(String key) {
        try {
            return loadClass(cGetString(key, null));
        } catch (Throwable t) {
            Logger.e(key, t);
            return null;
        }
    }

    public interface OnTaskCompleteListener {
        void onTaskComplete(String result, boolean completed);
    }
}
