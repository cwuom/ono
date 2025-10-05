package moe.ono.hooks.item.developer

import android.annotation.SuppressLint
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem

@SuppressLint("DiscouragedApi")
@HookItem(
    path = "开发者选项/打开 Scheme 链接",
    description = "* 打开后在快捷菜单使用"
)
class JumpSchemeUri : BaseSwitchFunctionHookItem() {
    override fun entry(classLoader: ClassLoader) {}
}