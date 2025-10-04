/**
 * License
 * 本文件及代码仅供 cwuom/ono 使用
 * 基于 cwuom/ono 开发的开源项目需保证文件中声明本信息
 * 禁止 私有项目、闭源项目和以收费形式二次分发的项目 使用
 */

package moe.ono.hooks.item.developer

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.ono.bridge.ManagerHelper
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks.base.util.Toasts
import moe.ono.ui.CommonContextWrapper
import moe.ono.util.Logger
import moe.ono.util.QAppUtils.getCurrentUin
import moe.ono.util.SystemServiceUtils

@SuppressLint("DiscouragedApi")
@HookItem(
    path = "开发者选项/GetCookie",
    description = "依赖快捷菜单, 打开后在快捷菜单使用"
)
class GetCookie : BaseSwitchFunctionHookItem() {
    override fun entry(classLoader: ClassLoader) {}

    companion object {
        fun getCookie(context: Context, domain: String) {
            try {
                val ticketManager = ManagerHelper.getManager(2)
                val uin = getCurrentUin().toString()

                val getPSkeyMethod = ticketManager.javaClass.getDeclaredMethod("getPskey", String::class.java, String::class.java)
                val getSkeyMethod = ticketManager.javaClass.getDeclaredMethod("getSkey", String::class.java)
//                val getSuperkeyMethod = ticketManager.javaClass.getDeclaredMethod("getSuperkey", String::class.java)
                val getPt4TokenMethod = ticketManager.javaClass.getDeclaredMethod("getPt4Token", String::class.java, String::class.java)

                val pskey = getPSkeyMethod.invoke(ticketManager, uin, domain) as String
                val skey = getSkeyMethod.invoke(ticketManager, uin) as String
//                val superkey = getSuperkeyMethod.invoke(ticketManager, uin) as String?
                val pt4Token = getPt4TokenMethod.invoke(ticketManager, uin, domain) as String?
                val p_uin = "o$uin"

                val cookieMap: MutableMap<String, String> = HashMap()

                Logger.d("PSkey: $pskey skey: $skey pt4Token: $pt4Token")

                cookieMap["uin"] = p_uin
                cookieMap["p_uin"] = p_uin
                cookieMap["skey"] = skey
                cookieMap["p_skey"] = pskey
//                if (superkey != null) {
//                    cookieMap["superkey"] = superkey
//                }
                if (pt4Token != null) {
                    cookieMap["pt4Token"] = pt4Token
                }

                val cookie = buildString {
                    cookieMap.forEach { (key, value) ->
                        append("$key=$value; ")
                    }
                }.removeSuffix("; ")

                val mContext = CommonContextWrapper.createAppCompatContext(context)
                val builder = MaterialAlertDialogBuilder(mContext)

                builder.setTitle("Cookie")
                builder.setMessage("注意, 千万不要泄露你的 Cookie 给任何人\n\n$cookie")

                builder.setNegativeButton("关闭") { dialog, count ->
                    dialog.dismiss()
                }
                builder.setPositiveButton("复制") { dialog, i ->
                    SystemServiceUtils.copyToClipboard(builder.context, cookie)
                    Toasts.success(builder.context, "复制成功")
                    dialog.dismiss()
                }

                builder.show()
            } catch (e: Exception) {
                Logger.e(e)
            }
        }

        fun getCookie(domain: String): String? {
            try {
                val ticketManager = ManagerHelper.getManager(2)
                val uin = getCurrentUin().toString()

                val getPSkeyMethod = ticketManager.javaClass.getDeclaredMethod("getPskey", String::class.java, String::class.java)
                val getSkeyMethod = ticketManager.javaClass.getDeclaredMethod("getSkey", String::class.java)
                // val getSuperkeyMethod = ticketManager.javaClass.getDeclaredMethod("getSuperkey", String::class.java)
                val getPt4TokenMethod = ticketManager.javaClass.getDeclaredMethod("getPt4Token", String::class.java, String::class.java)

                val pskey = getPSkeyMethod.invoke(ticketManager, uin, domain) as String
                val skey = getSkeyMethod.invoke(ticketManager, uin) as String
                // val superkey = getSuperkeyMethod.invoke(ticketManager, uin) as String?
                val pt4Token = getPt4TokenMethod.invoke(ticketManager, uin, domain) as String?
                val p_uin = "o$uin"

                val cookieMap: MutableMap<String, String> = HashMap()

                Logger.d("PSkey: $pskey skey: $skey pt4Token: $pt4Token")

                cookieMap["uin"] = p_uin
                cookieMap["p_uin"] = p_uin
                cookieMap["skey"] = skey
                cookieMap["p_skey"] = pskey
//                if (superkey != null) {
//                    cookieMap["superkey"] = superkey
//                }
                if (pt4Token != null) {
                    cookieMap["pt4Token "] = pt4Token
                }

                val cookie = buildString {
                    cookieMap.forEach { (key, value) ->
                        append("$key=$value; ")
                    }
                }.removeSuffix("; ")

                return cookie
            } catch (e: Exception) {
                Logger.e(e)
            }
            return null
        }

        fun getBknByPSkey(PSkey: String): Int {
            var hash = 5381
            for (c in PSkey) {
                hash += (hash shl 5) + c.code
            }
            return hash and 0x7FFFFFFF
        }

        fun getBknByCookie(cookie: String): String {
            val pskey = cookie.split("; ")[0].replace("p_skey=", "")

            val bkn = getBknByPSkey(pskey).toString()
            return bkn
        }
    }
}