package moe.ono.hooks.base.api

import android.content.Intent
import com.tencent.qphone.base.remote.ToServiceMsg
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import moe.ono.hooks._base.ApiHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.loader.hookapi.IMsfHandler
import moe.ono.util.Initiator.loadClass
import moe.ono.util.Logger


@HookItem(path = "API/QQ MSF 数据包监听")
class QQMsfReqHandler : ApiHookItem() {
    override fun entry(classLoader: ClassLoader) {
        XposedHelpers.findAndHookMethod(
            "com.tencent.qqnt.kernel.msf.KernelServlet",
            classLoader,
            "sendToMSF",
            Intent::class.java,
            loadClass("com.tencent.qphone.base.remote.ToServiceMsg"),
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val toServiceMsg = param.args[1] as ToServiceMsg
                    val intent = param.args[0] as Intent
                    Logger.i(toServiceMsg.serviceCmd)
                    handler.forEach { handler ->
                        if (toServiceMsg.serviceCmd == handler.cmd) {
                            handler.onHandle(param, intent, toServiceMsg)
                        }
                    }
                }
            })
    }

    companion object {
        val handler = arrayListOf<IMsfHandler>()
    }
}