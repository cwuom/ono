package moe.ono.hooks.item.developer

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import moe.ono.config.CacheConfig
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks._core.factory.HookItemFactory.getItem
import moe.ono.hooks.base.util.Toasts
import moe.ono.util.Logger


@SuppressLint("DiscouragedApi")
@HookItem(path = "开发者选项/InjectVConsole", description = "对所有的网页尝试注入 VConsole\n* 重启生效")
class InjectVConsole : BaseSwitchFunctionHookItem() {
    @Throws(Throwable::class)
    override fun entry(classLoader: ClassLoader) {
    }



    companion object {
        @JvmStatic
        fun injectWebViewForVConsole(webView: Any) {
            try {
                val x5WebViewClass = Class.forName("com.tencent.smtt.sdk.WebView")
                val x5ValueCallbackClass = Class.forName("com.tencent.smtt.sdk.ValueCallback")

                createX5WebViewClient(
                    x5WebViewClass = x5WebViewClass,
                    x5ValueCallbackClass = x5ValueCallbackClass
                )

                val addJsInterfaceMethod = webView.javaClass.getMethod(
                    "addJavascriptInterface",
                    Any::class.java,
                    String::class.java
                )
                val context = getWebViewContext(webView)
                addJsInterfaceMethod.invoke(webView, WebAppInterfaceForVConsole(context), "obj")
            } catch (e: Exception) {
                Logger.e("注入失败: ${e.stackTraceToString()}")
            }
        }
        private fun createX5WebViewClient(
            x5WebViewClass: Class<*>,
            x5ValueCallbackClass: Class<*>
        ) {
            try {
                CacheConfig.setX5WebViewClass(x5WebViewClass)
                CacheConfig.setX5ValueCallbackClass(x5ValueCallbackClass)
            } catch (e: Exception) {
                Logger.e("创建 X5 WebViewClient 失败: ${e.stackTraceToString()}")
                throw RuntimeException("无法创建 X5 WebViewClient", e)
            }
        }
        fun injectJavaScriptLogic(
            x5WebView: Any?,
            x5WebViewClass: Class<*>,
            x5ValueCallbackClass: Class<*>
        ) {
            if (!getItem(InjectVConsole::class.java).isEnabled) return
            try {
                val jsCode = """
            (function injectVConsole() {
  if (window.VConsole) return;
  const s = document.createElement('script');
  s.src = 'https://unpkg.com/vconsole@latest/dist/vconsole.min.js';
  s.onload = () => {
    try { window.vConsole = new VConsole(); console.log('vConsole injected') }
    catch (e) { console.error('vConsole init failed', e) }
  };
  s.onerror = () => obj.toast("注入失败");
  document.head.appendChild(s);
})();
        """.trimIndent()

                val evaluateMethod = x5WebViewClass.getMethod(
                    "evaluateJavascript",
                    String::class.java,
                    x5ValueCallbackClass
                )

                evaluateMethod.invoke(x5WebView, jsCode, null)

            } catch (e: Exception) {
                Logger.e("JS注入失败: ${e.stackTraceToString()}")
            }
        }


        private fun getWebViewContext(webView: Any): Context {
            return try {
                webView.javaClass.getMethod("getContext").invoke(webView) as Context
            } catch (e: NoSuchMethodException) {
                val field = webView.javaClass.getDeclaredField("mContext").apply {
                    isAccessible = true
                }
                field.get(webView) as Context
            } catch (e: Exception) {
                throw RuntimeException("无法获取WebView Context", e)
            }
        }

        class WebAppInterfaceForVConsole(private val context: Context) {
            @JavascriptInterface
            fun toast(text: String) {
                Toasts.error(context, text)
            }
        }
    }
}