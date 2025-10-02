package moe.ono.hooks.base.api

import de.robv.android.xposed.XposedHelpers.findMethodExact
import moe.ono.config.CacheConfig
import moe.ono.hooks._base.ApiHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks.clazz
import moe.ono.hooks.item.chat.QQBubbleRedirect.Companion.injectJavaScriptLogic
import moe.ono.hooks.item.chat.QQBubbleRedirect.Companion.injectWebViewForBubble
import moe.ono.hooks.item.developer.InjectVConsole
import moe.ono.util.Logger
import moe.ono.util.SyncUtils
import moe.ono.util.Utils
import java.lang.reflect.Method
import java.util.regex.Pattern


@HookItem(path = "API/OnWebViewLoad")
class QQOnLoadWebView : ApiHookItem() {
    @Throws(Throwable::class)
    override fun entry(classLoader: ClassLoader) {
        val mLoadUrl: Method = findMethodExact(
            "com.tencent.qimei.webview.QmX5Webview".clazz,
            "loadUrl",
            String::class.java,
        )

        val mOnPageFinished: Method = findMethodExact(
            "com.tencent.smtt.sdk.WebViewClient".clazz,
            "onPageFinished",
            "com.tencent.smtt.sdk.WebView".clazz,
            String::class.java,
        )

        hookBefore(mOnPageFinished) {
            Logger.d("QQOnLoadWebView -> onPageFinished")

            try {
                InjectVConsole.injectJavaScriptLogic(
                    x5WebView = it.args[0],
                    x5WebViewClass = CacheConfig.getX5WebViewClass(),
                    x5ValueCallbackClass = CacheConfig.getX5ValueCallbackClass()
                )

                injectJavaScriptLogic(
                    x5WebView = it.args[0],
                    itemId = CacheConfig.getItemID(),
                    x5WebViewClass = CacheConfig.getX5WebViewClass(),
                    x5ValueCallbackClass = CacheConfig.getX5ValueCallbackClass()
                )
            } catch (e: Exception) {
                Logger.e("QQOnLoadWebView", e)
            }

        }

        hookAfter(mLoadUrl) {
            val url = it.args[0] as String
            Logger.d("loadUrl: $url")

            InjectVConsole.injectWebViewForVConsole(it.thisObject)

            if (url.startsWith("https://zb.vip.qq.com/mall/item-detail?appid=2&")) {
                val webView = it.thisObject
                val pattern = Pattern.compile("itemid=(\\d+)")
                val matcher = pattern.matcher(url)
                if (matcher.find()) {
                    val itemid = matcher.group(1)
                    Logger.d("itemid: $itemid")
                    itemid?.let { it1 -> injectWebViewForBubble(webView, it1) }
                } else {
                    Logger.e("itemid not found.")
                }
            }
        }
    }

    override fun targetProcess(): Int {
        return SyncUtils.PROC_TOOL
    }
}
