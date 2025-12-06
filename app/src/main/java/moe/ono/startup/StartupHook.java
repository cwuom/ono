package moe.ono.startup;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import moe.ono.BuildConfig;
import moe.ono.util.IoUtils;

/**
 * Startup hook for QQ They should act differently according to the process they belong to.
 * <p>
 * I don't want to cope with them anymore, enjoy it as long as possible.
 * <p>
 * DO NOT MODIFY ANY CODE HERE UNLESS NECESSARY.
 *
 * @author cinit
 */
public class StartupHook {

    private static StartupHook sInstance;
    private static boolean sSecondStageInit = false;

    private StartupHook() {
    }

    /**
     * Entry point for static or dynamic initialization. NOTICE: Do NOT change the method name or signature.
     *
     * @param ctx         Application context for host
     * @param step        Step instance
     * @param lpwReserved null, not used
     * @param bReserved   false, not used
     */
    public static void execStartupInit(@NonNull Context ctx, @Nullable Object step, String lpwReserved, boolean bReserved) {
        if (sSecondStageInit) {
            throw new IllegalStateException("Second stage init already executed");
        }
        HybridClassLoader.setHostClassLoader(ctx.getClassLoader());
        StartupRoutine.execPostStartupInit(ctx, step, lpwReserved, bReserved);
        sSecondStageInit = true;
        deleteDirIfNecessaryNoThrow(ctx);

    }

    static void deleteDirIfNecessaryNoThrow(Context ctx) {
        try {
            deleteFile(new File(ctx.getDataDir(), "app_qqprotect"));
        } catch (Throwable e) {
            log_e(e);
        }
    }

    public static StartupHook getInstance() {
        if (sInstance == null) {
            sInstance = new StartupHook();
        }
        return sInstance;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static boolean deleteFile(File file) {
        if (!file.exists()) {
            return false;
        }
        if (file.isFile()) {
            file.delete();
        } else if (file.isDirectory()) {
            File[] listFiles = file.listFiles();
            if (listFiles != null) {
                for (File deleteFile : listFiles) {
                    deleteFile(deleteFile);
                }
            }
            file.delete();
        }
        return !file.exists();
    }

    static void log_e(Throwable th) {
        if (th == null) {
            return;
        }
        String msg = Log.getStackTraceString(th);
        Log.e(BuildConfig.TAG, msg);
        try {
            StartupInfo.getLoaderService().log(th);
        } catch (NoClassDefFoundError | NullPointerException e) {
            Log.e("Xposed", msg);
            Log.e("EdXposed-Bridge", msg);
        }
    }

    public void initializeAfterAppCreate(@NonNull Context ctx) {
        execStartupInit(ctx, null, null, false);
        deleteDirIfNecessaryNoThrow(ctx);
    }

    public void initializeBeforeAppCreate(@NonNull ClassLoader rtLoader) {
        try {
            XC_MethodHook startup = new XC_MethodHook(51) {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    ClassLoader cl = param.thisObject.getClass().getClassLoader();
                    assert cl != null;
                    Context app = StartupAgent.getBaseApplicationImpl(cl);
                    execStartupInit(app, param.thisObject, null, false);
                }
            };
            Class<?> loadDex = findLoadDexTaskClass(rtLoader);
            Method[] ms = loadDex.getDeclaredMethods();
            Method run = null;
            Method doStep = null;
            for (Method method : ms) {
                // QQ NT: 8.9.58.11040 (4054)+
                // public void run(Context)
                if (method.getReturnType() == void.class && method.getParameterTypes().length == 1 &&
                        method.getParameterTypes()[0] == Context.class) {
                    run = method;
                }
                // public boolean doStep()
                if (method.getReturnType() == boolean.class && method.getParameterTypes().length == 0) {
                    doStep = method;
                }
            }
            // We should try `public void LoadDexTask.run(Context)` first,
            // because there exists `public void LoadDexTask.blockUntilFinish()`.
            if (run == null && doStep == null) {
                throw new RuntimeException("neither LoadDexTask.run(Context) nor LoadDex.doStep() found");
            }
            Method m = run != null ? run : doStep;
            XposedBridge.hookMethod(m, startup);
        } catch (Throwable e) {
            if (e.toString().contains("com.bug.zqq")) {
                return;
            }
            if (e.toString().contains("com.google.android.webview")) {
                return;
            }
            log_e(e);
            throw IoUtils.unsafeThrow(e);
        }
        try {
            XposedHelpers.findAndHookMethod(rtLoader.loadClass("com.tencent.mobileqq.qfix.QFixApplication"),
                    "attachBaseContext", Context.class, new XC_MethodHook() {
                        @Override
                        public void beforeHookedMethod(MethodHookParam param) {
                            deleteDirIfNecessaryNoThrow((Context) param.args[0]);
                        }
                    });
        } catch (ClassNotFoundException ignored) {
        }
    }


    @SuppressWarnings("unchecked")
    private static Class<?> findLoadDexTaskClass(ClassLoader cl) throws ClassNotFoundException {
        try {
            return cl.loadClass("com.tencent.mobileqq.startup.step.LoadDex");
        } catch (ClassNotFoundException ignored) {
            // ignore
        }
        // for NT QQ
        // com.tencent.qqnt.startup.task.ITaskFactory
        Class<?> kITaskFactory;
        {
            Class<?> kNtTask = cl.loadClass("com.tencent.qqnt.startup.task.NtTask");
            String[] kITaskFactoryNameCandidates = {
                    // QQ [8.9.58, 9.2.30)
                    "com.tencent.qqnt.startup.task.d",
                    // QQ 9.2.30+
                    "com.tencent.qqnt.startup.task.c"
            };
            // ITaskFactory is an interface having one method returning NtTask
            Class<?> kFound = null;
            for (String name : kITaskFactoryNameCandidates) {
                try {
                    Class<?> kCandidate = cl.loadClass(name);
                    if (kCandidate.isInterface()) {
                        Method[] methods = kCandidate.getDeclaredMethods();
                        if (methods.length == 1 && methods[0].getReturnType() == kNtTask) {
                            kFound = kCandidate;
                            break;
                        }
                    }
                } catch (ClassNotFoundException ignored) {
                    // try next one
                }
            }
            if (kFound == null) {
                throw new IllegalStateException("ITaskFactory not found");
            }
            kITaskFactory = kFound;
        }
        // com.tencent.mobileqq.startup.task.config.TaskFactory
        // to find TaskFactory: grep -r Inject_ColdStartupTaskFactory */sources/com/tencent/mobileqq/startup
        Class<?> kTaskFactory;
        {
            String[] kTaskFactoryNameCandidates = {
                    // QQ [8.9.63.11390_4194, 9.0.0.13955_5220]
                    "com.tencent.mobileqq.startup.task.config.a",
                    // QQ [9.0.8.14755_5540, 9.2.30.31725_12330]+, TIM 4.0.95_4001
                    "com.tencent.mobileqq.startup.task.config.b"
            };
            Class<?> kFound = null;
            for (String name : kTaskFactoryNameCandidates) {
                try {
                    Class<?> kCandidate = cl.loadClass(name);
                    if (kITaskFactory.isAssignableFrom(kCandidate)) {
                        kFound = kCandidate;
                        break;
                    }
                } catch (ClassNotFoundException ignored) {
                    // try next one
                }
            }
            if (kFound == null) {
                throw new IllegalStateException("TaskFactory not found");
            }
            kTaskFactory = kFound;
        }
        Field taskClassMapField = null;
        for (Field field : kTaskFactory.getDeclaredFields()) {
            if (field.getType() == HashMap.class && Modifier.isStatic(field.getModifiers())) {
                taskClassMapField = field;
                break;
            }
        }
        if (taskClassMapField == null) {
            throw new AssertionError("taskClassMapField not found");
        }
        taskClassMapField.setAccessible(true);
        HashMap<String, Class<?>> taskClassMap;
        try {
            // XXX: this will cause <clinit>() to be called, check whether it will cause any problem
            taskClassMap = (HashMap<String, Class<?>>) taskClassMapField.get(null);
        } catch (IllegalAccessException e) {
            // should not happen
            throw new AssertionError(e);
        }
        assert taskClassMap != null;
        Class<?> loadDexTaskClass = taskClassMap.get("LoadDexTask");
        if (loadDexTaskClass == null) {
            throw new AssertionError("loadDexTaskClass not found");
        }
        return loadDexTaskClass;
    }

}
