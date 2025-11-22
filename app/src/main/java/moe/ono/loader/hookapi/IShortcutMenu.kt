package moe.ono.loader.hookapi

import android.content.Context

interface IShortcutMenu {
    fun isAdd(): Boolean
    val menuName: String

    fun clickHandle(
        context: Context
    )
}