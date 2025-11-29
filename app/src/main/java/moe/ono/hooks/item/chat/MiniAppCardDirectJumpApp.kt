package moe.ono.hooks.item.chat

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.hookAllMethods
import moe.ono.config.ConfigManager
import moe.ono.constants.Constants
import moe.ono.hooks._base.BaseClickableFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks.base.util.Toasts
import moe.ono.reflex.FieldUtils
import moe.ono.ui.CommonContextWrapper
import moe.ono.util.ContextUtils
import moe.ono.util.Logger
import moe.ono.util.Utils
import org.json.JSONArray
import org.json.JSONObject


@HookItem(path = "聊天与消息/小程序卡直接跳转 APP", description = "在点击小程序卡后直接跳转对应的 APP 而不是小程序\n* 点击进行配置\n* 配置添加弹窗有一个哔哩哔哩预设")
class MiniAppCardDirectJumpApp : BaseClickableFunctionHookItem() {
    override fun entry(classLoader: ClassLoader) {
        try {
            hookAllMethods(View::class.java, "setOnClickListener", object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val listener = param.args[0] as View.OnClickListener? ?: return

                    val cls: Class<*> = listener.javaClass
                    if (cls.name != "com.tencent.android.androidbypass.richui.viewdata.k") return

                    try {
                        val m = cls.getDeclaredMethod("onClick", View::class.java)
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                            @Throws(Throwable::class)
                            override fun beforeHookedMethod(param: MethodHookParam?) {
                                try {
                                    val viewData = FieldUtils.create(param?.thisObject).fieldTypeName("com.tencent.android.androidbypass.richui.viewdata").firstValue<Any>(param?.thisObject)
                                    val viewDataSuperClazz = viewData.javaClass.superclass
                                    val richUi = FieldUtils.create(viewDataSuperClazz).fieldTypeName("com.tencent.android.androidbypass.richui.").firstValue<Any>(viewData)
                                    // Logger.d(richUi.javaClass.toString())
                                    val tmpCommon = FieldUtils.create(richUi).fieldTypeName("com.tencent.android.androidbypass.richui.").lastValue<Any>(richUi)
                                    val business = FieldUtils.create(tmpCommon).fieldTypeName("com.tencent.mobileqq.aio.msglist.holder.component.template.business").firstValue<Any>(tmpCommon)
                                    val msgRecord = FieldUtils.create(business).fieldType(MsgRecord::class.java).firstValue<MsgRecord>(business)
                                    val ark = msgRecord.elements?.get(0)?.arkElement?.bytesData
                                    val arkJson = JSONObject(ark)
                                    val app = arkJson.getString("app")
                                    if (app == null || !app.contains("com.tencent.miniapp")) return
                                    if (arkJson.getString("view") == null || !arkJson.getString("view").contains("8C8E89B49BE609866298ADDFF2DBABA4")) return

                                    val appId = Utils.deepGet(
                                        arkJson,
                                        "meta.detail_1.appid",
                                        null
                                    ) as String? ?: return
                                    val qqDocUrl = Utils.deepGet(
                                        arkJson,
                                        "meta.detail_1.qqdocurl",
                                        null
                                    ) as String? ?: return

                                    val config = getConfig()
                                    for (i in 0 until config.length()) {
                                        val obj = config.getJSONObject(i)
                                        if (obj.getString("appid") == appId) {
                                            param?.result = null
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setPackage(obj.getString("pkg_name"))
                                                data = Uri.parse(qqDocUrl)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            intent.resolveActivity(ContextUtils.getCurrentActivity().packageManager)?.let {
                                                ContextUtils.getCurrentActivity().startActivity(intent)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Logger.e(e)
                                }
                            }
                        })
                    } catch (e: Throwable) {
                        Logger.e(e)
                    }
                }
            })

        } catch (e: Exception) {
            Logger.e(e)
        }
    }

    override fun alwaysRun(): Boolean {
        return true
    }

    /**
     * [
     *   {
     *     "name": "",
     *     "pkg_name": "",
     *     "appid": ""
     *   }
     *   ...
     * ]
     */
    fun getConfig(): JSONArray {
        return org.json.JSONArray(ConfigManager.dGetString(Constants.PrekCfgXXX + "mini_app_card_direct_jump_app", "[]"))
    }

    override fun onClick(context: Context?) {
        if (context == null) return
        val fixContext = CommonContextWrapper.createAppCompatContext(context)

        val builder = MaterialAlertDialogBuilder(fixContext)
        builder.setTitle("小程序卡直接跳转 APP")

        val config = getConfig()
        val items = arrayOfNulls<String>(config.length())

        if (!items.isEmpty()) {
            for (i in 0 until config.length())
                items[i] = config.getJSONObject(i).getString("name")

            builder.setItems(items) { dialog, which ->
                val builder1 = MaterialAlertDialogBuilder(fixContext)
                builder1.setTitle("配置信息")
                builder1.setMessage("名称: ${config.getJSONObject(which).getString("name")}\n" +
                        "跳转包名: ${config.getJSONObject(which).getString("pkg_name")}\n" +
                        "小程序 AppId: ${config.getJSONObject(which).getString("appid")}")
                builder1.setPositiveButton("关闭") {dialog, _ ->
                    dialog.dismiss()
                }
                builder1.setNegativeButton("删除") {dialog1, _ ->
                    val newConfig = JSONArray()
                    for (i in 0 until config.length()) {
                        if (i != which) {
                            newConfig.put(config.getJSONObject(i))
                        }
                    }
                    ConfigManager.dPutString(Constants.PrekCfgXXX + "mini_app_card_direct_jump_app", newConfig.toString())
                    Toasts.success(builder1.context, "删除成功")
                    dialog1.dismiss()
                    dialog.dismiss()
                }
                builder1.show()
            }
        } else {
            builder.setMessage("没有任何配置")
        }

        builder.setPositiveButton("添加") { dialog, _ ->
            val builder1 = MaterialAlertDialogBuilder(fixContext)
            builder1.setTitle("添加配置")

            val linearLayout = LinearLayout(fixContext)
            linearLayout.orientation = LinearLayout.VERTICAL

            val name = EditText(fixContext).apply { hint = "配置名称" }
            linearLayout.addView(name)

            val pkgName = EditText(fixContext).apply { hint = "跳转包名" }
            linearLayout.addView(pkgName)

            val appId = EditText(fixContext).apply { hint = "小程序 AppId" }
            linearLayout.addView(appId)

            builder1.setView(linearLayout)

            builder1.setPositiveButton("添加", null)
            builder1.setNegativeButton("取消", null)
            builder1.setNeutralButton("哔哩哔哩预设", null)

            val alertDialog = builder1.create()

            alertDialog.setOnShowListener {

                val positive = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                positive.setOnClickListener {
                    if (name.text.toString().isEmpty() ||
                        pkgName.text.toString().isEmpty() ||
                        appId.text.toString().isEmpty()
                    ) {
                        Toasts.error(builder1.context, "配置不能为空")
                        return@setOnClickListener
                    }

                    val newConfig = JSONArray().apply {
                        for (i in 0 until config.length()) {
                            put(config.getJSONObject(i))
                        }
                        put(JSONObject().apply {
                            put("name", name.text.toString())
                            put("pkg_name", pkgName.text.toString())
                            put("appid", appId.text.toString())
                        })
                    }

                    ConfigManager.dPutString(
                        Constants.PrekCfgXXX + "mini_app_card_direct_jump_app",
                        newConfig.toString()
                    )
                    Toasts.success(builder1.context, "添加成功")

                    alertDialog.dismiss()
                    dialog.dismiss()
                }

                val negative = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                negative.setOnClickListener {
                    alertDialog.dismiss()
                }

                val neutral = alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                neutral.setOnClickListener {
                    name.setText("哔哩哔哩")
                    pkgName.setText("tv.danmaku.bili")
                    appId.setText("1109937557")
                }
            }

            alertDialog.show()
        }

        builder.show()
    }
}