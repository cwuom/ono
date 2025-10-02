package moe.ono.loader.modern;

import static moe.ono.constants.PackageConstants.PACKAGE_NAME_QQ;
import static moe.ono.constants.PackageConstants.PACKAGE_NAME_TIM;

import android.content.pm.ApplicationInfo;

import androidx.annotation.NonNull;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import moe.ono.BuildConfig;
import moe.ono.common.ModuleLoader;
import moe.ono.startup.StartupInfo;
import moe.ono.util.Logger;

/**
 * Entry point for started Xposed API 100.
 * (Develop Xposed Modules Using Modern Xposed API)
 * */


public class ModernHookEntry extends XposedModule {
    private static XposedModuleInterface.ModuleLoadedParam mModule;
    private static PackageLoadedParam mModuleLoadedParam;

    public ModernHookEntry(@NonNull XposedInterface base, @NonNull XposedModuleInterface.ModuleLoadedParam param) {
        super(base, param);
        mModule = param;
    }


    @Override
    public void onPackageLoaded(@NonNull PackageLoadedParam param) {
        String packageName = param.getPackageName();
        String processName = param.getApplicationInfo().processName;
        if (packageName.equals(PACKAGE_NAME_QQ) || packageName.equals(PACKAGE_NAME_TIM)) {
            if (param.isFirstPackage()) {
                String modulePath = this.getApplicationInfo().sourceDir;
                StartupInfo.setModulePath(modulePath);
                mModuleLoadedParam = param;
                try {
                    handleLoadPackage(param.getClassLoader(), param.getApplicationInfo(), modulePath, processName);
                } catch (ClassNotFoundException e) {
                    Logger.e(e);
                }
            }
        }
    }



    public void handleLoadPackage(@NonNull ClassLoader cl, @NonNull ApplicationInfo ai, @NonNull String modulePath, String processName) throws ClassNotFoundException {
        String dataDir = ai.dataDir;
        android.util.Log.d(BuildConfig.TAG, "ModernHookEntry.handleLoadHostPackage: dataDir=" + dataDir + ", modulePath=" + modulePath + ", processName=" + processName);
        try {
            ModuleLoader.initialize(dataDir, cl, Lsp100HookImpl.INSTANCE, Lsp100HookImpl.INSTANCE, modulePath, true);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }



    }

}