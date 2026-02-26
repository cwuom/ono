package moe.ono.loader.hookapi

import android.content.Intent
import com.tencent.qphone.base.remote.ToServiceMsg
import de.robv.android.xposed.XC_MethodHook

interface IMsfHandler {
    fun onHandle(param: XC_MethodHook.MethodHookParam, intent: Intent, toServiceMsg: ToServiceMsg)
    val cmd: String
}