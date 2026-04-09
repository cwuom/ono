package moe.ono.util;

import android.content.Context;
import android.os.Environment;

import java.io.File;

import moe.ono.HostInfo;


public class PathTool {


    public static String getDataSavePath(Context context, String dirName) {
        //getExternalFilesDir()：SDCard/Android/data/你的应用的包名/files/dirName
        return context.getExternalFilesDir(dirName).getAbsolutePath();
    }

    public static String getStorageDirectory() {
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    public static String getModuleDataPath() {
        String directory = getStorageDirectory() + "/Android/data/" + HostInfo.getHostInfo().getPackageName() + "/ONO";
        File file = new File(directory);
        if (!file.exists()) {
            file.mkdirs();
        }
        return directory;
    }

    public static String getModuleCachePath(String dirName) {
        File cache = new File(getModuleDataPath() + "/cache/" + dirName);
        if (!cache.exists()) {
            cache.mkdirs();
        }
        return cache.getAbsolutePath();
    }


}
