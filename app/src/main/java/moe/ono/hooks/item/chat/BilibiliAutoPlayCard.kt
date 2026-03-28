package moe.ono.hooks.item.chat

import android.annotation.SuppressLint
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks.base.api.MqqapiHandler
import moe.ono.util.Logger
import moe.ono.util.MqqApiUrlTool

@SuppressLint("DiscouragedApi")
@HookItem(path = "聊天与消息/哔哩哔哩自动转播放卡", description = "从哔哩哔哩分享的小程序卡片将会自动转换为点击就自动放视频的卡片")
class BilibiliAutoPlayCard : BaseSwitchFunctionHookItem() {
    override fun entry(classLoader: ClassLoader) {
        MqqapiHandler.listeners.add { param ->
            if (!this.configIsEnable()) return@add
            val url = param.args[2] as? String ?: return@add

            val parsedUrl = MqqApiUrlTool.parse(url)
            if (parsedUrl.path == "/to_fri"
                && parsedUrl.authority == "share"
                && parsedUrl.getParam("src_type") == "app"
                && parsedUrl.getDecodedParam("mini_program_type")?.base64Decoded == "3"
                && parsedUrl.getDecodedParam("mini_program_appid")?.base64Decoded == "1109937557") {
                val path = parsedUrl.getDecodedParam("mini_program_path")?.base64Decoded?:return@add
                val bvid = path.substringAfter("bvid=").substringBefore("&share_source")
                if (bvid.isEmpty()) return@add
                val newPath =
                    "pages/packages/fallback/webview/webview.html?url=https%3a%2f%2fwww.bilibili.com%2fblackboard%2fnewplayer.html%3fbvid%3d$bvid%26page%3d1%26autoplay%3d1"
                parsedUrl.replaceAsBase64("mini_program_path", newPath)
                Logger.d(parsedUrl.build())
                param.args[2] = parsedUrl.build()
            }
        }
    }
}

