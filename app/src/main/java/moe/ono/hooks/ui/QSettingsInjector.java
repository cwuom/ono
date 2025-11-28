package moe.ono.hooks.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import kotlin.collections.ArraysKt;
import kotlin.jvm.functions.Function0;
import moe.ono.R;
import moe.ono.activity.OUOSettingActivity;
import moe.ono.config.CacheConfig;
import moe.ono.hooks.XHook;
import moe.ono.hooks._base.ApiHookItem;
import moe.ono.hooks._core.annotation.HookItem;
import moe.ono.lifecycle.Parasitics;
import moe.ono.reflex.Reflex;
import moe.ono.util.Initiator;
import moe.ono.util.Logger;

@HookItem(path = "设置模块入口")
public class QSettingsInjector extends ApiHookItem {
    private void injectSettingEntryForMainSettingConfigProvider() throws ReflectiveOperationException {
        // 8.9.70+
        Class<?> kMainSettingFragment = Initiator.load("com.tencent.mobileqq.setting.main.MainSettingFragment");
        if (kMainSettingFragment != null) {
            // MainSettingConfigProvider was removed in 9.1.65.24690(9516) gray release
            Class<?> kMainSettingConfigProvider = Initiator.load("com.tencent.mobileqq.setting.main.MainSettingConfigProvider");
            // 9.1.20+, NewSettingConfigProvider, A/B test on 9.1.20
            Class<?> kNewSettingConfigProvider = Initiator.load("com.tencent.mobileqq.setting.main.NewSettingConfigProvider");
            // 9.2.30, NewSettingConfigProvider was obfuscated to b
            Class<?> kNewSettingConfigProviderObf = Initiator.load("com.tencent.mobileqq.setting.main.b");
            Method getItemProcessListOld = null;
            if (kMainSettingConfigProvider != null) {
                getItemProcessListOld = Reflex.findSingleMethod(kMainSettingConfigProvider, List.class, false, Context.class);
            }
            Method getItemProcessListNew = null;
            if (kNewSettingConfigProvider != null) {
                getItemProcessListNew = Reflex.findSingleMethod(kNewSettingConfigProvider, List.class, false, Context.class);
            }
            Method getItemProcessListNewObf = null;
            if (kNewSettingConfigProviderObf != null) {
                getItemProcessListNewObf = Reflex.findSingleMethod(kNewSettingConfigProviderObf, List.class, false, Context.class);
            }
            if (getItemProcessListOld == null && getItemProcessListNew == null && getItemProcessListNewObf == null) {
                throw new IllegalStateException("getItemProcessListOld == null && getItemProcessListNew == null && getItemProcessListNewObf == null");
            }
            Class<?> kAbstractItemProcessor = null;
            for (String possibleParent : new String[]{
                    "com.tencent.mobileqq.setting.main.processor.AccountSecurityItemProcessor",
                    "com.tencent.mobileqq.setting.main.processor.AboutItemProcessor"
            }) {
                Class<?> k = Initiator.load(possibleParent);
                if (k != null) {
                    kAbstractItemProcessor = k.getSuperclass();
                    break;
                }
            }
            if (kAbstractItemProcessor == null) {
                throw new IllegalStateException("kAbstractItemProcessor == null");
            }
            // SimpleItemProcessor has too few xrefs. I have no idea how to find it without a list of candidates.
            final String[] possibleSimpleItemProcessorNames = new String[]{
                    // 8.9.70 ~ 9.0.0
                    "com.tencent.mobileqq.setting.processor.g",
                    // 9.0.8+
                    "com.tencent.mobileqq.setting.processor.h",
                    // 9.1.50 (9006)
                    "com.tencent.mobileqq.setting.processor.i",
                    // 9.1.70.25540 (9856) gray
                    "com.tencent.mobileqq.setting.processor.j",
                    // QQ 9.1.28.21880 (8398) gray
                    "as3.i",
            };
            List<Class<?>> possibleSimpleItemProcessorCandidates = new ArrayList<>(5);
            for (String name : possibleSimpleItemProcessorNames) {
                Class<?> klass = Initiator.load(name);
                if (klass != null && klass.getSuperclass() == kAbstractItemProcessor) {
                    possibleSimpleItemProcessorCandidates.add(klass);
                }
            }
            // assert possibleSimpleItemProcessorCandidates.size() == 1;
            if (possibleSimpleItemProcessorCandidates.size() != 1) {
                throw new IllegalStateException("possibleSimpleItemProcessorCandidates.size() != 1, got " + possibleSimpleItemProcessorCandidates);
            }
            Class<?> kSimpleItemProcessor = possibleSimpleItemProcessorCandidates.get(0);
            Method setOnClickListener;
            {
                List<Method> candidates = ArraysKt.filter(kSimpleItemProcessor.getDeclaredMethods(), m -> {
                    Class<?>[] argt = m.getParameterTypes();
                    // NOSONAR java:S1872 not same class
                    return m.getReturnType() == void.class && argt.length == 1 && Function0.class.getName().equals(argt[0].getName());
                });
                candidates.sort(Comparator.comparing(Method::getName));
                // TIM 4.0.95.4001 only have one method, that is the one we need (onClick() lambda)
                if (candidates.size() != 2 && candidates.size() != 1) {
                    throw new IllegalStateException("com.tencent.mobileqq.setting.processor.g.?(Function0)V candidates.size() != 1|2");
                }
                // take the smaller one
                setOnClickListener = candidates.get(0);
            }

            XC_MethodHook callback = getXcMethodHook(kSimpleItemProcessor, setOnClickListener);

            if (getItemProcessListOld != null) {
                XposedBridge.hookMethod(getItemProcessListOld, callback);
            }
            if (getItemProcessListNew != null) {
                XposedBridge.hookMethod(getItemProcessListNew, callback);
            }
            if (getItemProcessListNewObf != null) {
                XposedBridge.hookMethod(getItemProcessListNewObf, callback);
            }
        }
    }

    @NonNull
    private XC_MethodHook getXcMethodHook(Class<?> kSimpleItemProcessor, Method setOnClickListener) {
        final Constructor<?> ctorSimpleItemProcessor;
        final int ctorArgCount;

        Constructor<?> c;
        int argc;
        try {
            // newer QQ (>= 9.1.91.x) has 5 args
            c = kSimpleItemProcessor.getDeclaredConstructor(
                    Context.class, int.class, CharSequence.class, int.class, String.class
            );
            argc = 5;
        } catch (NoSuchMethodException e) {
            try {
                // older QQ has 4 args
                c = kSimpleItemProcessor.getDeclaredConstructor(
                        Context.class, int.class, CharSequence.class, int.class
                );
                argc = 4;
            } catch (NoSuchMethodException ex) {
                throw new IllegalStateException("SimpleItemProcessor constructor not found", ex);
            }
        }

        ctorSimpleItemProcessor = c;
        ctorArgCount = argc;

        return XHook.afterAlways(50, param -> {
            List<Object> result = (List<Object>) param.getResult();
            Context ctx = (Context) param.args[0];

            // inject resources
            Parasitics.injectModuleResources(ctx.getResources());
            @SuppressLint("DiscouragedApi")
            int resId = ctx.getResources().getIdentifier("qui_tuning", "drawable", ctx.getPackageName());

            // create entryItem depending on constructor args
            Object entryItem;
            if (ctorArgCount == 5) {
                entryItem = ctorSimpleItemProcessor.newInstance(
                        ctx, R.id.OnO_settingEntryItem, "ONO", resId, null
                );
            } else {
                entryItem = ctorSimpleItemProcessor.newInstance(
                        ctx, R.id.OnO_settingEntryItem, "ONO", resId
                );
            }

            // set click listener
            Class<?> thatFunction0 = setOnClickListener.getParameterTypes()[0];
            Object theUnit = thatFunction0.getClassLoader()
                    .loadClass("kotlin.Unit").getField("INSTANCE").get(null);
            ClassLoader hostClassLoader = Initiator.getHostClassLoader();
            Object func0 = Proxy.newProxyInstance(hostClassLoader, new Class<?>[]{thatFunction0}, (proxy, method, args) -> {
                if (method.getName().equals("invoke")) {
                    onSettingEntryClick(ctx);
                    return theUnit;
                }
                return method.invoke(this, args);
            });
            setOnClickListener.invoke(entryItem, func0);

            // insert entry into settings list
            Class<?> kItemProcessorGroup = result.get(0).getClass();
            Constructor<?> ctor;
            try {
                ctor = kItemProcessorGroup.getDeclaredConstructor(List.class, CharSequence.class, CharSequence.class);
            } catch (NoSuchMethodException e) {
                // 9.2.30
                ctor = kItemProcessorGroup.getDeclaredConstructor(List.class, CharSequence.class, CharSequence.class,
                        int.class, Initiator.load("kotlin.jvm.internal.DefaultConstructorMarker"));
            }
            ArrayList<Object> list = new ArrayList<>(1);
            list.add(entryItem);
            Object group;
            if (ctor.getParameterTypes().length == 5) {
                // 9.2.30
                group = ctor.newInstance(list, "", "", 6, null);
            } else {
                group = ctor.newInstance(list, "", "");
            }
            boolean isNew = param.thisObject.getClass().getName().contains("NewSettingConfigProvider");
            int indexToInsert = isNew ? 2 : 1;
            result.add(indexToInsert, group);
        });
    }


    private void onSettingEntryClick(@NonNull Context context) {
        Intent intent = new Intent(context, OUOSettingActivity.class);
        context.startActivity(intent);
    }


    @Override
    public void entry(@NonNull ClassLoader classLoader) throws Throwable {
        if (CacheConfig.isSetEntry()){
            return;
        }
        try {
            injectSettingEntryForMainSettingConfigProvider();
            CacheConfig.setIsSetEntry(true);
        } catch (ReflectiveOperationException e) {
            Logger.e("MainSettingEntranceInjector", e);
        }
    }
}