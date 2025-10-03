package moe.ono.creator

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.core.BasePopupView
import com.lxj.xpopup.core.BottomPopupView
import com.lxj.xpopup.util.XPopupUtils
import moe.ono.R
import moe.ono.hooks.base.util.Toasts
import moe.ono.ui.CommonContextWrapper
import moe.ono.ui.view.JsonViewer
import moe.ono.util.AppRuntimeHelper
import moe.ono.util.analytics.ActionReporter
import org.json.JSONObject

@SuppressLint("ResourceType")
class JsonViewerDialog(context: Context) : BottomPopupView(context) {
    @SuppressLint("SetTextI18n", "ServiceCast")
    override fun onCreate() {
        super.onCreate()
        Handler(Looper.getMainLooper()).postDelayed({
            val jsonViewer = findViewById<JsonViewer>(R.id.rv_json)
            val btnCopy = findViewById<Button>(R.id.btn_copy)
            val rgType = findViewById<RadioGroup>(R.id.rg_type)
            val title = findViewById<TextView>(R.id.title)
            var copyText: String

            title.text = "Json Viewer"

            jsonViewer.setJson(content)

            copyText = jsonViewer.getJSONString()

            rgType.visibility = GONE

            btnCopy.setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Copied Text", copyText))
                Toasts.info(context, "已复制")
            }


        }, 100)
    }


    override fun getImplLayoutId(): Int {
        return R.layout.layout_qq_message_fetcher_result
    }

    companion object {
        private var popupView: BasePopupView? = null
        private var content: JSONObject? = null

        fun createView(context: Context, content: JSONObject) {
            val fixContext = CommonContextWrapper.createAppCompatContext(context)
            val newPop = XPopup.Builder(fixContext).moveUpToKeyboard(true).isDestroyOnDismiss(true)
            newPop.maxHeight((XPopupUtils.getScreenHeight(context) * .90f).toInt())
            newPop.popupHeight((XPopupUtils.getScreenHeight(context) * .90f).toInt())
            Companion.content = content


            ActionReporter.reportVisitor(
                AppRuntimeHelper.getAccount(),
                "CreateView-JsonViewerDialog"
            )

            popupView = newPop.asCustom(JsonViewerDialog(fixContext))
            popupView?.show()
        }
    }
}



