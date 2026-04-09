package moe.ono.core;

import static moe.ono.config.CacheConfig.addRecreateCount;
import static moe.ono.config.CacheConfig.getRecreateCount;
import static moe.ono.constants.Constants.CLAZZ_ACTIVITY_SPLASH;
import static moe.ono.constants.Constants.CLAZZ_BASE_APPLICATION_IMPL;
import static moe.ono.util.SyncUtils.postDelayed;
import static moe.ono.util.analytics.ActionReporter.reportVisitor;
import static moe.ono.util.SyncUtils.post;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.lxj.xpopup.XPopup;

import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import moe.ono.R;
import moe.ono.config.CacheConfig;
import moe.ono.creator.center.MethodFinderDialog;
import moe.ono.dexkit.TargetManager;
import moe.ono.fix.huawei.HuaweiResThemeMgrFix;
import moe.ono.hooks._core.HookItemLoader;
import moe.ono.lifecycle.Parasitics;
import moe.ono.service.PlatformUtils;
import moe.ono.service.QQInterfaces;
import moe.ono.service.inject.ServletPool;
import moe.ono.startup.StartupInfo;
import moe.ono.util.AppRuntimeHelper;
import moe.ono.util.Initiator;
import moe.ono.util.Logger;
import moe.ono.util.SyncUtils;

public class QLauncher {
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    public void init(@NonNull ClassLoader cl, @NonNull ApplicationInfo ai, @NonNull String modulePath, Context context) {
        Initiator.init(context.getClassLoader());

        HookItemLoader hookItemLoader = new HookItemLoader();
        hookItemLoader.loadHookItem(SyncUtils.getProcessType());



        XposedHelpers.findAndHookMethod(CLAZZ_BASE_APPLICATION_IMPL, cl,
            "onCreate",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Application hostApp = (Application) param.thisObject;
                    StartupInfo.setHostApp(hostApp);
                }
            });

        /* beforeDoOnCreate */
        XposedHelpers.findAndHookMethod(CLAZZ_ACTIVITY_SPLASH, cl, "beforeDoOnCreate", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                CacheConfig.setSplashActivity((Activity) param.thisObject);

                String ver = PlatformUtils.INSTANCE.getQQVersion(context);
                if (!Objects.equals(ver, TargetManager.getLastQQVersion())){
                    TargetManager.setIsNeedFindTarget(true);
                }
                reportVisitor(AppRuntimeHelper.getAccount(), "QQVersion-"+ver);
                Logger.i("QQVersion-"+ver);

                TargetManager.setLastQQVersion(ver);
                super.beforeHookedMethod(param);
            }
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                post(() -> {
                    // only for huawei
                    Resources res = context.getApplicationContext().getResources();
                    HuaweiResThemeMgrFix.initHook(context);
                    HuaweiResThemeMgrFix.fix(context, res);

                    Parasitics.injectModuleResources(res);
                });
                super.afterHookedMethod(param);
            }
        });

        /* doOnCreate */
        XposedHelpers.findAndHookMethod(CLAZZ_ACTIVITY_SPLASH, cl, "doOnCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                injectLifecycleForProcess(context);
                super.beforeHookedMethod(param);
            }
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                StartupInfo.setSplashActivity(activity);
                addRecreateCount();

                if (TargetManager.isNeedFindTarget()){
                    activity.setTheme(R.style.Theme_Ono);
                    if (getRecreateCount() == 1){
                        activity.recreate();
                        return;
                    }
                }


                postDelayed(0, () -> {
                    if (!TargetManager.isNeedFindTarget()){
                        return;
                    }
                    new XPopup.Builder(activity)
                            .hasBlurBg(true)
                            .dismissOnBackPressed(false)
                            .dismissOnTouchOutside(false)
                            .asCustom(new MethodFinderDialog(activity, cl, ai))
                            .show();
                });
                super.afterHookedMethod(param);
            }
        });




    }


    public static void injectLifecycleForProcess(Context ctx) {
        if (SyncUtils.isMainProcess()) {
            post(() -> {
                Logger.i("Inject Lifecycle For Process....");
                Parasitics.injectModuleResources(ctx.getResources());
                Parasitics.injectModuleResources(CacheConfig.getSplashActivity().getResources());
                Parasitics.initForStubActivity(ctx);
                ServletPool.INSTANCE.injectServlet();
                try {
                    QQInterfaces.Companion.update();
                } catch (Throwable e) {
                    Logger.e("QQInterfaces.update failed during startup", e);
                }
            });
        }
    }
}
