package moe.ono.hooks.item.entertainment

import android.app.AlertDialog
import android.widget.EditText
import com.github.kyuubiran.ezxhelper.utils.ArgTypes
import com.github.kyuubiran.ezxhelper.utils.Args
import com.github.kyuubiran.ezxhelper.utils.invokeMethod
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.util.ContextUtils
import moe.ono.util.Logger

@HookItem(path = "娱乐功能/自定义专属红包 Uin", description = "点击专属红包页面的发送按钮后会拦截\n* 仅适配 9.2.66")
class CustomSelectFriendUin : BaseSwitchFunctionHookItem() {

    override fun entry(classLoader: ClassLoader) {
        val isConfirm = ThreadLocal.withInitial { false }

        try {
            XposedHelpers.findAndHookMethod("ru1.g", classLoader, "b", "java.util.Map", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!this@CustomSelectFriendUin.configIsEnable() || isConfirm.get() == true) return
                    val obj = param.thisObject?:return
                    val map = param.args[0] as java.util.Map<String, String>
                    if (map.containsKey("grab_uin_list")) {
                        param.result = null
                        AlertDialog.Builder(ContextUtils.getCurrentActivity()).apply {
                            setTitle("自定义专属红包 Uin")
                            val uin = EditText(ContextUtils.getCurrentActivity()).apply {
                                hint = "输入 Uin, 多个用 | 分割"
                                setText(map.get("grab_uin_list"))
                            }
                            setView(uin)
                            setPositiveButton("确定") { _, _ ->
                                map.put("grab_uin_list", uin.text.toString())
                                map.put("total_num", ((uin.text.toString().count { it == '|' }) + 1).toString())
                                isConfirm.set(true)
                                obj.invokeMethod("b", args = Args(arrayOf(map)), argTypes = ArgTypes(arrayOf(java.util.Map::class.java)))
                                isConfirm.set(false)
                            }
                        }.show()
                    }
                }
            })
        } catch (e: Exception) {
            Logger.e(e)
        }
    }
}