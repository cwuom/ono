package moe.ono.hooks.base.api

import android.text.TextUtils
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import de.robv.android.xposed.XC_MethodHook
import moe.ono.config.ONOConf
import moe.ono.hooks._base.ApiHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.reflex.XMethod

@HookItem(path = "API/发送消息监听")
class QQSendMsgListener : ApiHookItem() {
    override fun entry(classLoader: ClassLoader) {
        val sendMsgMethod = XMethod
            .clz("com.tencent.qqnt.kernel.nativeinterface.IKernelMsgService\$CppProxy")
            .name("sendMsg")
            .ignoreParam().get()

        hookBefore(sendMsgMethod) { param ->
            val elements = param.args[2] as ArrayList<MsgElement>

            for (listener in listeners) {
                listener(param, elements)
            }

            if (ONOConf.getBoolean("global", "sticker_panel_set_ch_change_title", false)) {
                val text: String =
                    ONOConf.getString("global", "sticker_panel_set_ed_change_title", "")
                if (!TextUtils.isEmpty(text)) {
                    for (element in elements) {
                        if (element.picElement != null) {
                            val picElement = element.picElement
                            picElement.summary = text
                        }
                    }
                }
            }
        }
    }

    companion object {
        val listeners = mutableListOf<(param: XC_MethodHook.MethodHookParam, elems: ArrayList<MsgElement>) -> Unit>()
    }
}