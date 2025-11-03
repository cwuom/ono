package moe.ono.hooks.item.profile

import OidbSvcTrpcTcp0Xfe12
import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.interfaces.OnSelectListener
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedHelpers
import io.noties.markwon.Markwon
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks.base.util.Toasts
import moe.ono.service.QQInterfaces.Companion.receive
import moe.ono.service.QQInterfaces.Companion.sendOidbSvcTrpcTcp
import moe.ono.ui.CommonContextWrapper
import moe.ono.util.Initiator
import moe.ono.util.Logger
import moe.ono.util.SyncUtils
import moe.ono.util.Utils
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import java.util.regex.Pattern

@SuppressLint("DiscouragedApi")
@HookItem(path = "资料卡/我要更多的信息", description = "长按别人资料卡主页上的设置按钮呼出菜单")
class ProfileCardMenu : BaseSwitchFunctionHookItem() {
    private fun hookTargetActivity(activity: Activity) {
        SyncUtils.postDelayed({
            Thread {
                var setting: View? = null
                try {
                    setting = Utils.getViewByDesc(activity, "设置", 2000)
                } catch (e: InterruptedException) {
                    Logger.e("查找设置按钮时出错: ", e)
                }
                if (setting != null) {
                    val finalSetting: View = setting
                    mainHandler.post {
                        try {
                            finalSetting.setOnLongClickListener(onSettingLongClickListener())
                        } catch (ignored: Exception) { }
                    }
                } else {
                    Logger.e("设置按钮未找到")
                }
            }.start()
        }, 100)
    }

    @Throws(Throwable::class)
    override fun entry(classLoader: ClassLoader) {
        try {
            val clazz =
                Initiator.loadClass("com.tencent.mobileqq.profilecard.activity.FriendProfileCardActivity")
            val m = XposedHelpers.findMethodExact(
                clazz, "doOnCreate",
                Bundle::class.java
            )
            hookAfter(m) { param: MethodHookParam ->
                val activity = param.thisObject as Activity
                val intent = activity.intent
                if (intent != null) {
                    val extras = intent.extras
                    val allInOne: Any? =
                        intent.getParcelableExtra("AllInOne")
                    QQ =
                        extractUinFromAllInOneString(allInOne.toString())
                } else {
                    Logger.d("Intent is null")
                }
                hookTargetActivity(activity)
            }
        } catch (e: ClassNotFoundException) {
            Logger.e(this.itemName, e)
        }
    }


    private class onSettingLongClickListener : View.OnLongClickListener {
        override fun onLongClick(v: View): Boolean {
            val fixContext = CommonContextWrapper.createAppCompatContext(v.context)
            XPopup.Builder(fixContext)
                .hasShadowBg(false)
                .atView(v)
                .asAttachList(
                    arrayOf("查看等级", "尝试获取详细信息", "查找共同群"),
                    intArrayOf(),
                    object : OnSelectListener {
                        override fun onSelect(position: Int, t: String) {
                            when (position) {
                                0 -> try {
                                    Utils.jump(
                                        v,
                                        this.hashCode(),
                                        "https://club.vip.qq.com/card/friend?qq=$QQ"
                                    )
                                } catch (e: Exception) {
                                    Toasts.error(v.context, "无法打开内置浏览器")
                                }

                                1 -> {
                                    val codes = listOf(
                                        20002u, // 昵称
                                        27394u, // QID
                                        20009u, // 性别
                                        20031u, // 生日
                                        101u,   // 头像
//                                        103u,
                                        102u,   // 简介/签名
//                                        20022u,
//                                        20023u,
//                                        20024u,
//                                        24002u,
//                                        27037u,
//                                        27049u,
                                        20011u, // 手机号
                                        20016u, // 手机号
                                        20021u, // 学校
                                        20003u, // 国家
                                        20004u, // 省份
//                                        20005u,
                                        20006u, // 可能是地区
                                        20020u, // 城市
                                        20026u, // 注册时间
//                                        24007u,
                                        104u,   // 标签列表
                                        105u,   // 等级
//                                        42432u,
//                                        42362u,
//                                        41756u,
//                                        41757u,
//                                        42257u,
                                        27372u, // 状态
//                                        42315u,
//                                        107u,   // 业务列表
//                                        45160u,
//                                        45161u,
                                        27406u, // 自定义状态文本
//                                        62026u,
                                        20037u  // 年龄
                                    )




                                    val inner = OidbSvcTrpcTcp0Xfe12.Inner.newBuilder()
                                        .addAllCode(codes.map { it.toInt() })
                                        .build()

                                    val msg = QQ?.let {
                                        OidbSvcTrpcTcp0Xfe12.OidbSvcTrpcTcp0xfe1.newBuilder()
                                            .setUin(it.toLong())
                                            .setCodes(inner)
                                            .build()
                                    }

                                    val appSeq = msg?.let { sendOidbSvcTrpcTcp("OidbSvcTrpcTcp.0xfe1_2",0xfe1,2, it.toByteArray()) }
                                    Logger.d("appSeq: $appSeq")
                                    Thread {
                                        appSeq?.let { it ->
                                            receive(it)?.let {
                                                Logger.i(it.toString())
//                                                val clipboard = fixContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
//                                                clipboard.setPrimaryClip(ClipData.newPlainText("Copied Text", it.toString()))
//                                                Toasts.show(fixContext, TYPE_INFO, "已复制")

                                                val data = profileToString(it.toString())
                                                SyncUtils.runOnUiThread {
                                                    val textView = TextView(fixContext).apply {
                                                        setPadding(32, 32, 32, 32)
                                                        textSize = 16f
                                                        setTextIsSelectable(true)
                                                    }

                                                    val markwon = Markwon.create(fixContext)
                                                    markwon.setMarkdown(textView, data)

                                                    val builder = MaterialAlertDialogBuilder(fixContext)
                                                    builder.setTitle("详细信息")
                                                    builder.setView(textView)
                                                    builder.setNegativeButton("关闭", null)
                                                    builder.show()
                                                }

                                            }
                                        }

                                    }.start()
                                }

                                2 -> {
                                    Utils.jump(
                                        v,
                                        this.hashCode(),
                                        "https://ti.qq.com/friends/recall?uin=$QQ"
                                    )
                                }
                            }
                        }
                    })
                .show()
            return true
        }
    }



    companion object {
        private var QQ: String? = null
        private val mainHandler = Handler(Looper.getMainLooper())
        private val displayMap = linkedMapOf(
            20002u to "昵称",
            20026u  to "注册时间",
            27394u to "QID",
            105u    to "等级",
            20009u to "性别",
            20031u to "生日",
            102u    to "签名",
            27372u  to "在线状态",
            27406u  to "自定义状态",
            20011u  to "手机号(邮箱)",
            20016u  to "手机号",
            20021u  to "学校",
            20003u  to "国家",
            20004u  to "省份",
            20006u  to "地区(可能是)",
            20020u  to "城市",
            20037u  to "年龄",
            101u    to "头像(尺寸640)",
//            104u    to "标签",
        )

        private val statusMap = buildMap {
            put(0, "离线")
            put(1, "在线")
            listOf(3, 268435459).forEach { put(it, "离开") }
            put(4, "隐身/离线")
            put(5, "忙碌")
            put(6, "Q我吧")
            put(7, "请勿打扰")
            listOf(1770497, 270205953).forEach { put(it, "恋爱中") }
        }

        fun profileToString(raw: String): String {
            val rootObj   = JSONObject(raw)
            val code2val  = HashMap<UInt, String>()

            collectCodes(rootObj, code2val)

            val sb = StringBuilder()
            for ((code, cn) in displayMap)
                sb.append("- $cn\n> `````````````````` ${code2val[code] ?: "—"} ``````````````````\n")

            return sb.toString().trimEnd()
        }

        private fun collectCodes(node: Any?, out: MutableMap<UInt, String>) {
            when (node) {
                is JSONObject -> {
                    if (node.has("1") && node.has("2")) {
                        val codeLong = node.optLong("1", -1)
                        if (codeLong > 0) {
                            val code = codeLong.toUInt()
                            if (code in displayMap && code !in setOf(45160u, 45161u)) {
                                val raw = if (code == 102u) node else node.get("2")
                                val v = parseValue(code, raw)
                                if (v.isNotBlank() && out[code].isNullOrBlank()) {
                                    out[code] = v
                                }
                            }
                        }
                    }
                    val it = node.keys()
                    while (it.hasNext()) {
                        collectCodes(node.get(it.next()), out)
                    }
                }

                is org.json.JSONArray -> {
                    for (i in 0 until node.length()) {
                        collectCodes(node.get(i), out)
                    }
                }
            }
        }

        @SuppressLint("SimpleDateFormat")
        private fun parseValue(code: UInt, any: Any): String {
            return when (code) {
                20009u -> when ((any as? Number)?.toInt() ?: any.toString().toIntOrNull()) {
                    0 -> "女"; 1 -> "男"; else -> ""
                }
                /* ---------- 签名 ---------- */
                102u -> {
                    when (any) {
                        is JSONObject -> {              // 有时返回的是对象
                            // 优先找常见字段 2/5 ，再兜底整个对象
                            any.optString("2")
                                .ifBlank { any.optString("5") }
                                .ifBlank { any.toString() }
                        }
                        else -> any.toString()          // 旧包仍是直接字符串
                    }
                }

                /* ---------- 生日 ---------- */
                20031u -> {
                    val raw = any.toString()
                    if (raw.startsWith("hex->") && raw.length >= 13) {
                        val hex   = raw.substring(5)
                        val year  = hex.substring(0, 4).toInt(16)
                        val month = hex.substring(4, 6).toInt(16)
                        val day   = hex.substring(6, 8).toInt(16)
                        "%04d-%02d-%02d".format(year, month, day)
                    } else ""
                }

                /* ---------- 注册时间 ---------- */
                20026u -> {
                    val ts = (any as? Number)?.toLong() ?: any.toString().toLongOrNull() ?: return ""
                    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").apply {
                        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
                    }
                    fmt.format(Date(ts * 1000))
                }

                /* ---------- 标签 ---------- */
                104u -> {
                    try {
                        val json = any as? JSONObject
                        val value = json?.optJSONObject("1")?.optString("4")
                        if (!value.isNullOrEmpty()) {
                            value
                        } else {
                            json?.toString(4).orEmpty()
                        }
                    } catch (e: Exception) {
                        any.toString()
                    }
                }

                /* ---------- 状态 ---------- */
                27372u -> statusMap[(any as? Number)?.toInt() ?: any.toString().toIntOrNull()] ?: "状态码($any)"

                /* ---------- 自定义状态文本 ---------- */
                27406u -> (any as? JSONObject)?.optString("2").orEmpty()

                /* ---------- 头像 ---------- */
                101u, 103u -> {
                    val url = (any as? JSONObject)?.optString("5").orEmpty()
                    if (url.isBlank()) ""
                    else if (url.endsWith("&s=")) url + "640"
                    else url
                }


                else -> any.toString()
            }
        }

        fun extractUinFromAllInOneString(allInOneStr: String): String? {
            val pattern = Pattern.compile("uin='(\\d+)'")
            val matcher = pattern.matcher(allInOneStr)
            if (matcher.find()) {
                return matcher.group(1)
            }
            return null
        }
    }
}