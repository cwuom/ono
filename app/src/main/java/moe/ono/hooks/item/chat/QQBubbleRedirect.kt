package moe.ono.hooks.item.chat

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import de.robv.android.xposed.XposedHelpers.findMethodExact
import moe.ono.config.CacheConfig
import moe.ono.hooks._base.BaseClickableFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks._core.factory.HookItemFactory.getItem
import moe.ono.hooks.base.util.Toasts
import moe.ono.hooks.clazz
import moe.ono.util.AppRuntimeHelper
import moe.ono.util.Logger
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.reflect.Method


@SuppressLint("DiscouragedApi")
@HookItem(path = "聊天与消息/气泡重定向", description = "重定向后的气泡对 iOS 设备无效\n开启后去商城任意找一个气泡点击应用即可\n也可以在弹窗内配置 Item ID\n* 重启生效")
class QQBubbleRedirect : BaseClickableFunctionHookItem() {
    @Throws(Throwable::class)
    override fun entry(classLoader: ClassLoader) {
        try {
            val mI: Method = findMethodExact("com.tencent.mobileqq.bubble.BubbleManager".clazz,
                "I", String::class.java)

            hookBefore(mI) {
                val cacheFile = File(CACHE_FILE)
                if (cacheFile.exists()) {
                    it.args[0] = CACHE_FILE
                }
            }

        } catch (t: NoSuchMethodError) {
            val mI: Method = findMethodExact("com.tencent.mobileqq.bubble.BubbleManager".clazz,
                "J", String::class.java)

            hookBefore(mI) {
                val cacheFile = File(CACHE_FILE)
                if (cacheFile.exists()) {
                    it.args[0] = CACHE_FILE
                }
            }
        } catch (t: Throwable) {
            Logger.e("QQBubbleRedirect", t)
        }

    }



    companion object {
        @JvmStatic
        fun invokeGetAioVasMsgData() {
            if (!getItem(QQBubbleRedirect::class.java).isEnabled){
                return
            }
            try {
                val vasAioDataImplClass =
                    Class.forName("com.tencent.mobileqq.vas.api.impl.VasAioDataImpl")
                val vasAioDataImplInstance = vasAioDataImplClass.getDeclaredConstructor().newInstance()
                val getAioVasMsgDataMethod = vasAioDataImplClass.getMethod(
                    "getAioVasMsgData",
                    String::class.java
                )

                getAioVasMsgDataMethod.invoke(vasAioDataImplInstance, AppRuntimeHelper.getAccount())
            } catch (e: Exception) {
                Logger.e("InvokeGetAioVasMsgData", e)
            }
        }

        @JvmStatic
        fun injectWebViewForBubble(webView: Any, itemId: String) {
            try {
                val x5WebViewClass = Class.forName("com.tencent.smtt.sdk.WebView")
                val x5ValueCallbackClass = Class.forName("com.tencent.smtt.sdk.ValueCallback")

                createX5WebViewClient(
                    itemId = itemId,
                    x5WebViewClass = x5WebViewClass,
                    x5ValueCallbackClass = x5ValueCallbackClass
                )

                val addJsInterfaceMethod = webView.javaClass.getMethod(
                    "addJavascriptInterface",
                    Any::class.java,
                    String::class.java
                )
                val context = getWebViewContext(webView)
                addJsInterfaceMethod.invoke(webView, WebAppInterfaceForBubble(context), "obj")
            } catch (e: Exception) {
                Logger.e("注入失败: ${e.stackTraceToString()}")
            }
        }
        private fun createX5WebViewClient(
            itemId: String,
            x5WebViewClass: Class<*>,
            x5ValueCallbackClass: Class<*>
        ) {
            try {
                CacheConfig.setItemID(itemId)
                CacheConfig.setX5WebViewClass(x5WebViewClass)
                CacheConfig.setX5ValueCallbackClass(x5ValueCallbackClass)
            } catch (e: Exception) {
                Logger.e("创建 X5 WebViewClient 失败: ${e.stackTraceToString()}")
                throw RuntimeException("无法创建 X5 WebViewClient", e)
            }
        }
        fun injectJavaScriptLogic(
            x5WebView: Any?,
            itemId: String,
            x5WebViewClass: Class<*>,
            x5ValueCallbackClass: Class<*>
        ) {
            if (!getItem(QQBubbleRedirect::class.java).isEnabled) return
            try {
                val jsCode = """
            (function() {
                var existingCustomButton = document.querySelector('button[data-custom-button="true"]');
                if (existingCustomButton) {
                    return; 
                }

                var buttons = document.querySelectorAll('button[vt-itemid][vt-itemtype][vt-actionid]');
                buttons.forEach(function(button) {
                    if (button.innerText.includes('分享')) {
                        var customButton = document.createElement('button');
                        customButton.innerText = '应用';
                        customButton.style.marginLeft = '10px';
                        customButton.style.whiteSpace = 'nowrap';
                        customButton.setAttribute('data-custom-button', 'true');
                        customButton.onclick = function() {
                            alert("气泡 $itemId 已尝试应用，重启生效");
                            obj.onCustomButtonClicked("$itemId");
                        };
                        button.parentNode.insertBefore(customButton, button.nextSibling);
                    }
                });
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

         fun createCacheFile(id: String) {
            val jsonContent =
                "{\"animations\":{\"stc1\":{\"align\":\"BL\",\"alpha\":\"false\",\"count\":\"12\",\"cycle_count\":\"3\",\"rect\":[\"0\",\"-80\",\"56\",\"80\"],\"time\":\"100\",\"type\":\"static\",\"zip_name\":\"voice\"}},\"color\":\"0xFFffffff\",\"id\":2727,\"key_animations\":[{\"align\":\"BL\",\"animation\":\"stc1\",\"count\":\"12\",\"cycle_count\":\"3\",\"key_word\":[\"我\",\"你\",\"他\",\"她\",\"它\",\"爱\",\"乐\",\"美\",\"高兴\",\"笑\",\"好\",\"的\",\"那\",\"拜拜\",\"么么\",\"晚安\",\"乖\",\"帅\",\"睡\",\"想\",\"是\",\"88\",\"不\",\"啊\",\"哈\",\"嗯\",\"呵\"],\"rect\":[\"0\",\"-80\",\"56\",\"80\"],\"time\":\"100\",\"version\":1516189158,\"zip_name\":\"voice\"}],\"link_color\":\"0xFF04018d\",\"loopList\":[#>id<#],\"name\":\"七彩心情\",\"version\":1516189158,\"voice_animation\":{\"align\":\"BL\",\"animation\":\"stc1\",\"count\":\"12\",\"rect\":[\"0\",\"-80\",\"56\",\"80\"],\"time\":\"100\"},\"zoom_point\":[\"65\",\"56\"]}"
            val cacheFile = File(CACHE_FILE)
            cacheFile.delete()

            try {
                if (!cacheFile.parentFile?.exists()!!) {
                    cacheFile.parentFile?.mkdirs()
                }
                FileOutputStream(cacheFile).use { fos ->
                    fos.write(jsonContent.replace("#>id<#", id).toByteArray())
                }
                Logger.i("[QQBubbleRedirect] 缓存文件创建成功: $CACHE_FILE")
            } catch (e: IOException) {
                Logger.i("[QQBubbleRedirect] 缓存文件创建失败: ", e)
            }
        }

        fun getItemId(): String {
            try {
                val cacheFile = File(CACHE_FILE)
                if (cacheFile.exists()) {
                    val jsonContent = cacheFile.readText()
                    val json = JSONObject(jsonContent)
                    val itemId = json.optJSONArray("loopList")
                        ?.optString(0)
                    if (itemId == null) {
                        return ""
                    } else if (itemId == "#>id<#") {
                        return ""
                    }
                    return itemId
                } else {
                    return ""
                }
            } catch (e: Exception) {
                Logger.e(e)
                return ""
            }
        }

        class WebAppInterfaceForBubble(private val context: Context) {
            @JavascriptInterface
            fun onCustomButtonClicked(itemId: String) {
                Logger.d("itemID: $itemId")
                createCacheFile(itemId)
                Toasts.show(context, Toasts.TYPE_SUCCESS, "done")

            }
        }



        @SuppressLint("SdCardPath")
        const val CACHE_FILE =
            "/data/user/0/com.tencent.mobileqq/files/files/ono_bubble_cache.json"
    }
}

