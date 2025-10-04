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
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks.base.util.Toasts
import moe.ono.util.Logger
import moe.ono.util.SystemServiceUtils

@SuppressLint("DiscouragedApi")
@HookItem(
    path = "开发者选项/GetBknByCookie",
    description = "依赖快捷菜单, 打开后在快捷菜单使用"
)
class GetBknByCookie : BaseSwitchFunctionHookItem() {
    override fun entry(classLoader: ClassLoader) {}

    companion object {
        fun getBkn(context: Context, cookie: String) {
            try {
                val mContext = context
                val builder = MaterialAlertDialogBuilder(mContext)

                val bkn = GetCookie.getBknByCookie(cookie)

                builder.setTitle("Bkn")
                builder.setMessage("注意, 千万不要泄露你的 Bkn 给任何人\n\n$bkn")

                builder.setNegativeButton("关闭") { dialog, count ->
                    dialog.dismiss()
                }
                builder.setPositiveButton("复制") { dialog, i ->
                    SystemServiceUtils.copyToClipboard(builder.context, bkn)
                    Toasts.success(builder.context, "复制成功")
                    dialog.dismiss()
                }

                builder.show()
            } catch (e: Exception) {
                Logger.e(e)
            }
        }
    }
}