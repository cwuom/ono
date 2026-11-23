package moe.ono.dexkit;

import org.luckypray.dexkit.DexKitBridge;

import moe.ono.util.Logger;

public class DexKitExecutor {
    private final String apkPath;
    private final ClassLoader classLoader;

    public DexKitExecutor(String apkPath, ClassLoader classLoader) {
        this.apkPath = apkPath;
        this.classLoader = classLoader;
    }

    public boolean execute(DexKitTask task) {
        try (DexKitBridge bridge = DexKitBridge.create(apkPath)) {
            task.execute(bridge, classLoader);
            return true;
        } catch (Exception | LinkageError e) {
            Logger.e("DexKitExecutor", e);
            return false;
        }
    }

}
