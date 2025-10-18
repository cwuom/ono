package moe.ono.hooks.item.entertainment

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.widget.EditText
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.ono.hooks._base.BaseClickableFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks.base.util.Toasts
import moe.ono.hooks.protocol.sendPacket
import moe.ono.ui.CommonContextWrapper
import java.time.LocalDate
import java.time.ZoneOffset


@SuppressLint("DiscouragedApi")
@HookItem(path = "娱乐功能/刷步数", description = "刷步数, 步数必须要比原步数高才会更新\n* 点击使用")
class FuckSport : BaseClickableFunctionHookItem() {

    override fun entry(classLoader: ClassLoader) {}

    companion object {
        fun showDialog(context: Context) {
            val fixContext = CommonContextWrapper.createAppCompatContext(context)

            val builder = MaterialAlertDialogBuilder(fixContext)

            builder.setTitle("刷步数")

            val stepCount = EditText(fixContext)
            stepCount.hint = "请输入步数"

            val deviceName = EditText(fixContext)
            deviceName.hint = "请输入设备代号或名称(默认自动获取)"

            val layout = LinearLayout(fixContext)
            layout.orientation = LinearLayout.VERTICAL

            layout.addView(stepCount)
            layout.addView(deviceName)

            builder.setView(layout)

            builder.setPositiveButton("确定") { dialog, i ->
                fun getDeviceModel(): String {
                    return try {
                        val process = Runtime.getRuntime().exec("getprop ro.product.model")
                        process.inputStream.bufferedReader().use { it.readLine() ?: Build.MODEL }
                    } catch (e: Exception) {
                        Build.MODEL
                    }
                }

                val body = "{\n" +
                        "  \"1\": {\n" +
                        "    \"1\": 109,\n" +
                        "    \"2\": {\n" +
                        "      \"6\": 53\n" +
                        "    },\n" +
                        "    \"3\": \"9.1.70\"\n" +
                        "  },\n" +
                        "  \"2\": \"{\\\"oauth_consumer_key\\\":1002,\\\"data\\\":[{\\\"type\\\":1,\\\"time\\\":"+ LocalDate.now().atStartOfDay().toEpochSecond(ZoneOffset.UTC) +",\\\"steps\\\":"+stepCount.text.toString()+"}],\\\"version\\\":\\\"9.1.70\\\",\\\"lastRecordTime\\\":1759919,\\\"model\\\":\\\""+(deviceName.text.toString().ifEmpty { getDeviceModel() })+"\\\",\\\"zone\\\":\\\"GMT+08:00 Asia\\\\\\/Shanghai\\\",\\\"imei\\\":\\\"\\\",\\\"mode\\\":1,\\\"stepSource\\\":0,\\\"foreground\\\":1}\"\n" +
                        "}"

                sendPacket("yundong_report.steps", body.trim())

                Toasts.info(builder.context, "请求成功")

                dialog.dismiss()
            }

            builder.setNegativeButton("取消") {dialog, i ->
                dialog.dismiss()
            }

            builder.show()
        }
    }
}