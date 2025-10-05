package moe.ono.creator.center

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import moe.ono.activity.BiliLoginActivity
import moe.ono.config.ConfigManager
import moe.ono.config.ONOConf
import moe.ono.constants.Constants
import moe.ono.hooks._base.BaseClickableFunctionHookItem
import moe.ono.hooks._core.factory.HookItemFactory
import moe.ono.hooks.base.util.Toasts
import moe.ono.hooks.item.chat.QQBubbleRedirect
import moe.ono.hooks.item.chat.StickerPanelEntry
import moe.ono.hooks.item.developer.QQPacketHelperEntry
import moe.ono.hooks.item.sigma.QQMessageTracker
import moe.ono.hooks.item.sigma.QQSurnamePredictor
import moe.ono.util.Logger
import moe.ono.util.SyncUtils
import moe.ono.util.api.ark.ArkRequest
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.util.Objects
import java.util.regex.Pattern

object ClickableFunctionDialog {
    fun showCFGDialogSurnamePredictor(item: BaseClickableFunctionHookItem, context: Context?) {
        if (context == null) return
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle("猜姓氏")

        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(16, 16, 16, 16)

        val checkBox = MaterialCheckBox(context)
        checkBox.text = "启用"

        val textView = TextView(context)
        val input = EditText(context)
        input.hint = "300"
        input.setText(ConfigManager.dGetInt(Constants.PrekCfgXXX + item.path, 300).toString())
        textView.text = "操作间隔（毫秒）"
        layout.addView(checkBox)
        layout.addView(textView)
        layout.addView(input)

        builder.setView(layout)

        val warningText = TextView(context)
        layout.addView(warningText)

        builder.setNegativeButton(
            "关闭"
        ) { dialog: DialogInterface, _: Int -> dialog.cancel() }

        checkBox.isChecked = item.isEnabled

        checkBox.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            ConfigManager.dPutBoolean(
                Constants.PrekClickableXXX + HookItemFactory.getItem(
                    QQSurnamePredictor::class.java
                ).path, isChecked
            )
            item.isEnabled = isChecked
            if (isChecked) {
                item.startLoad()
            }
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                try {
                    ConfigManager.dPutInt(
                        Constants.PrekCfgXXX + HookItemFactory.getItem(
                            QQSurnamePredictor::class.java
                        ).path, s.toString().toInt()
                    )
                    warningText.text = ""
                } catch (e: NumberFormatException) {
                    warningText.text = "输入错误"
                }
            }

            override fun afterTextChanged(s: Editable) {}
        })


        builder.show()
    }

    @SuppressLint("SetTextI18n")
    fun showCFGDialogQQMessageTracker(item: BaseClickableFunctionHookItem, context: Context?) {
        if (context == null) return
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle("已读追踪")

        val layout = LinearLayout(context)
        val scrollView = ScrollView(context)
        layout.orientation = LinearLayout.VERTICAL
        val sParams: FrameLayout.LayoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )

        scrollView.layoutParams = sParams

        layout.setPadding(50, 10, 50, 50)

        val checkBox = MaterialCheckBox(context)
        checkBox.text = "启用"

        val checkBoxDef = MaterialCheckBox(context)
        checkBoxDef.text = "使用默认服务器设置"

        val i =
            "\n登陆信息: \n昵称: %s\nUID: %s\nArk-Coins: %s\n---------\n* 重新打开此窗口来刷新登陆信息"

        val textView = TextView(context)
        val tvInfo = TextView(context)
        val subtitle = TextView(context)

        textView.text =
            "\n提示：为了确保此功能不被滥用，我们需要您绑定第三方账号以便进行管理和验证。\n在未登录的情况下，此功能将无法使用。\n\n"
        tvInfo.text = String.format(i, "未知", "未知", "未知")

        subtitle.text = "服务器绑定"

        val materialButton = MaterialButton(context)
        materialButton.text = "登录您的 哔哩哔哩 账号"
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        materialButton.layoutParams = params

        Thread(object : Runnable {
            override fun run() {
                tvInfo.text = String.format(i, "获取中...", "获取中...", "获取中...")
                try {
                    val sb = stringBuilder

                    val userinfo = sb.toString()
                    val jsonObjectUserinfo = JSONObject(userinfo).optJSONObject("card")
                    val userName = Objects.requireNonNull(jsonObjectUserinfo).optString("name")
                    val userUID = Objects.requireNonNull(jsonObjectUserinfo).optString("mid")
                    val arkCoins = ArkRequest.getArkCoinsByMid(userUID, context)
                    SyncUtils.post {
                        tvInfo.text =
                            String.format(i, userName, userUID, arkCoins)
                    }
                } catch (e: Exception) {
                    Logger.e("showCFGDialogQQMessageTracker", e)
                    SyncUtils.post {
                        tvInfo.text =
                            String.format(i, "出错了！", "出错了！", "出错了！")
                    }
                }
            }

            @get:Throws(IOException::class)
            val stringBuilder: StringBuilder
                get() {
                    val urlPath = "https://account.bilibili.com/api/member/getCardByMid"
                    val url = URL(urlPath)
                    val conn = url.openConnection()

                    val cookies = ONOConf.getString("global", "cookies", "")

                    conn.setRequestProperty("Cookie", cookies)
                    conn.doInput = true
                    val br = BufferedReader(InputStreamReader(conn.getInputStream()))
                    val sb = StringBuilder()
                    var line: String?
                    while ((br.readLine().also { line = it }) != null) {
                        sb.append(line)
                    }
                    return sb
                }
        }).start()

        val signAddress = EditText(context)
        signAddress.hint = "签名服务器地址"
        signAddress.setText(
            ConfigManager.dGetString(
                Constants.PrekCfgXXX + "signAddress",
                "https://ark.ouom.fun/"
            )
        )

        val authenticationAddress = EditText(context)
        authenticationAddress.hint = "鉴权服务器地址"
        authenticationAddress.setText(
            ConfigManager.dGetString(
                Constants.PrekCfgXXX + "authenticationAddress",
                "https://q.lyhc.top/"
            )
        )

        signAddress.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                ConfigManager.dPutString(Constants.PrekCfgXXX + "signAddress", s.toString())
            }

            override fun afterTextChanged(s: Editable) {}
        })

        authenticationAddress.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                ConfigManager.dPutString(
                    Constants.PrekCfgXXX + "authenticationAddress",
                    s.toString()
                )
            }

            override fun afterTextChanged(s: Editable) {}
        })

        checkBox.isChecked = ConfigManager.dGetBoolean(
            Constants.PrekClickableXXX + HookItemFactory.getItem(
                QQMessageTracker::class.java
            ).path
        )
        checkBoxDef.isChecked =
            ConfigManager.dGetBooleanDefTrue(Constants.PrekCfgXXX + "usingDefSetting")

        layout.addView(checkBox)
        layout.addView(materialButton)
        layout.addView(textView)

        layout.addView(subtitle)
        layout.addView(signAddress)
        layout.addView(authenticationAddress)
        layout.addView(checkBoxDef)
        layout.addView(tvInfo)

        if (checkBoxDef.isChecked) {
            signAddress.isEnabled = false
            authenticationAddress.isEnabled = false
        }

        checkBoxDef.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            signAddress.isEnabled =
                !isChecked
            authenticationAddress.isEnabled = !isChecked

            ConfigManager.dPutBoolean(
                Constants.PrekCfgXXX + "usingDefSetting",
                isChecked
            )
            if (isChecked) {
                signAddress.setText("https://ark.ouom.fun/")
                authenticationAddress.setText("https://q.lyhc.top/")
            }
        }

        scrollView.addView(layout)

        builder.setView(scrollView)


        builder.setNegativeButton(
            "关闭"
        ) { dialog: DialogInterface, _: Int -> dialog.cancel() }

        checkBox.isChecked = item.isEnabled

        checkBox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            ConfigManager.dPutBoolean(
                Constants.PrekClickableXXX + item.path,
                isChecked
            )
            item.isEnabled = isChecked
            if (isChecked) {
                item.startLoad()
            }
        }

        materialButton.setOnClickListener { v: View ->
            val intent = Intent(
                v.context,
                BiliLoginActivity::class.java
            )
            v.context.startActivity(intent)
        }



        builder.show()
    }

    fun showCFGDialogStickerPanelEntry(item: BaseClickableFunctionHookItem, context: Context?) {
        if (context == null) return
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle("表情面板")

        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(16, 16, 16, 16)

        val checkBox = MaterialCheckBox(context)
        checkBox.text = "启用"

        val checkBox2 = MaterialCheckBox(context)
        checkBox2.text = "替换长按事件优先级（轻触即唤起）"

        checkBox.isChecked = ConfigManager.dGetBoolean(
            Constants.PrekClickableXXX + HookItemFactory.getItem(
                StickerPanelEntry::class.java
            ).path
        )
        checkBox2.isChecked =
            ConfigManager.dGetBoolean(Constants.PrekCfgXXX + "replaceStickerPanelClickEvent")
        val textView = TextView(context)
        textView.text = "更多设置"
        layout.addView(checkBox)
        layout.addView(textView)
        layout.addView(checkBox2)

        builder.setView(layout)

        val warningText = TextView(context)
        layout.addView(warningText)

        builder.setNegativeButton(
            "关闭"
        ) { dialog: DialogInterface, _: Int -> dialog.cancel() }

        checkBox.isChecked = item.isEnabled

        checkBox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            ConfigManager.dPutBoolean(
                Constants.PrekClickableXXX + item.path,
                isChecked
            )
            item.isEnabled = isChecked
            if (isChecked) {
                item.startLoad()
            }
        }

        checkBox2.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            ConfigManager.dPutBoolean(
                Constants.PrekCfgXXX + "replaceStickerPanelClickEvent",
                isChecked
            )
            if (isChecked) {
                item.startLoad()
            }
        }



        builder.show()
    }

    fun showCFGDialogSelfMessageReactor(
        item: BaseClickableFunctionHookItem,
        context: Context?
    ) {
        if (context == null) return

        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle("自我回应")

        val root = LinearLayout(context)
        root.orientation = LinearLayout.VERTICAL
        val pad = (16 * context.resources.displayMetrics.density).toInt()
        root.setPadding(pad, pad, pad, pad)

        val checkBox = MaterialCheckBox(context)
        checkBox.text = "启用"
        checkBox.isChecked = item.isEnabled
        root.addView(checkBox)

        val til = TextInputLayout(context)
        til.hint = "表情 ID（逗号分隔） 例：355,66"
        til.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val etInput = TextInputEditText(context)
        etInput.inputType = InputType.TYPE_CLASS_NUMBER
        etInput.keyListener = DigitsKeyListener.getInstance("0123456789,")
        etInput.setText(
            ConfigManager.dGetString(
                Constants.PrekCfgXXX + item.path, "355"
            )
        )
        til.addView(etInput)
        root.addView(til)

        builder.setView(root)

        builder.setNegativeButton(
            "关闭"
        ) { d: DialogInterface, _: Int -> d.cancel() }
        builder.setPositiveButton("确定", null) // 手动处理点击

        val pattern = Pattern.compile("^\\d+(,\\d+)*$")

        val dialog = builder.create()
        dialog.setOnShowListener {
            val btnOk = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            btnOk.isEnabled = false

            checkBox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                item.isEnabled =
                    isChecked
                ConfigManager.dPutBoolean(
                    Constants.PrekClickableXXX + item.path, isChecked
                )
                if (isChecked) item.startLoad()
            }

            val watcher: TextWatcher = object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence,
                    st: Int,
                    c: Int,
                    a: Int
                ) {
                }

                override fun afterTextChanged(s: Editable) {}
                override fun onTextChanged(
                    s: CharSequence,
                    st: Int,
                    b: Int,
                    c: Int
                ) {
                    val v = s.toString().trim()
                    if (pattern.matcher(v).matches()) {
                        til.error = null
                        btnOk.isEnabled = true
                    } else {
                        til.error = "格式错误：只能是数字和逗号，例如 355,66"
                        btnOk.isEnabled = false
                    }
                }
            }
            etInput.addTextChangedListener(watcher)

            btnOk.setOnClickListener {
                val value = if (etInput.text != null)
                    etInput.text.toString().trim()
                else
                    ""
                if (!pattern.matcher(value).matches()) {
                    til.error = "格式错误：只能是数字和逗号，例如 355,66"
                    return@setOnClickListener
                }

                ConfigManager.dPutString(
                    Constants.PrekCfgXXX + item.path, value
                )
                dialog.dismiss()
            }
            etInput.post {
                watcher.onTextChanged(
                    etInput.text, 0, 0, 0
                )
            }
        }

        dialog.show()
    }

    fun showCFGDialogMessageEncryptor(item: BaseClickableFunctionHookItem, context: Context?) {
        if (context == null) return

        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle("加密消息配置")

        val root = LinearLayout(context)
        root.orientation = LinearLayout.VERTICAL
        val pad = (16 * context.resources.displayMetrics.density).toInt()
        root.setPadding(pad, pad, pad, pad)

        val checkBox = MaterialCheckBox(context)
        checkBox.text = "启用加密"
        checkBox.isChecked = item.isEnabled
        root.addView(checkBox)

        val manualDecryptCheckBox = MaterialCheckBox(context)
        manualDecryptCheckBox.text = "启用手动解密"
        manualDecryptCheckBox.isChecked = ConfigManager.dGetBoolean(Constants.PrekCfgXXX + "manualDecrypt")
        root.addView(manualDecryptCheckBox)

        val inputMarginTop = (8 * context.resources.displayMetrics.density).toInt()

        val tilKey = TextInputLayout(context)
        tilKey.hint = "加密密钥（默认 ono）"
        val tilKeyParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        tilKey.layoutParams = tilKeyParams

        val etInputKey = TextInputEditText(context)
        etInputKey.inputType = InputType.TYPE_CLASS_TEXT
        etInputKey.setText(
            ConfigManager.dGetString(
                Constants.PrekCfgXXX + item.path, "ono"
            )
        )
        tilKey.addView(etInputKey)
        root.addView(tilKey)

        val tilDisplay = TextInputLayout(context)
        tilDisplay.hint = "自定义外显名称 (为空则是随机一言)"
        val tilDisplayParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        tilDisplayParams.topMargin = inputMarginTop
        tilDisplay.layoutParams = tilDisplayParams

        val etInputDisplay = TextInputEditText(context)
        etInputDisplay.inputType = InputType.TYPE_CLASS_TEXT

        val displayConfigKey = Constants.PrekCfgXXX + item.path + "_display"
        etInputDisplay.setText(
            ConfigManager.dGetString(displayConfigKey, "")
        )
        tilDisplay.addView(etInputDisplay)
        root.addView(tilDisplay)
        builder.setView(root)

        builder.setNegativeButton(
            "关闭"
        ) { d: DialogInterface, _: Int -> d.cancel() }
        builder.setPositiveButton("确定", null) // Manual handling for validation

        val dialog = builder.create()
        dialog.setOnShowListener {
            val btnOk = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            btnOk.isEnabled = false

            checkBox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                item.isEnabled = isChecked
                ConfigManager.dPutBoolean(
                    Constants.PrekClickableXXX + item.path, isChecked
                )
                if (isChecked) {
                    item.startLoad()
                } else {
                }
            }

            manualDecryptCheckBox.setOnCheckedChangeListener { _, isChecked ->
                ConfigManager.dPutBoolean(Constants.PrekCfgXXX + "manualDecrypt", isChecked)
            }

            val keyWatcher: TextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, st: Int, c: Int, a: Int) {}
                override fun afterTextChanged(s: Editable) {}
                override fun onTextChanged(s: CharSequence, st: Int, b: Int, c: Int) {
                    val keyText = s.toString().trim()
                    if (keyText.isNotEmpty()) {
                        tilKey.error = null
                        btnOk.isEnabled = true
                    } else {
                        tilKey.error = "加密密钥不能为空"
                        btnOk.isEnabled = false
                    }
                }
            }
            etInputKey.addTextChangedListener(keyWatcher)

            btnOk.setOnClickListener {
                val keyValue = etInputKey.text?.toString()?.trim() ?: ""

                if (keyValue.isEmpty()) {
                    tilKey.error = "加密密钥不能为空"
                    return@setOnClickListener
                }

                ConfigManager.dPutString(
                    Constants.PrekCfgXXX + item.path, keyValue
                )

                val displayValue = etInputDisplay.text?.toString()?.trim() ?: ""
                ConfigManager.dPutString(displayConfigKey, displayValue)

                dialog.dismiss()
            }

            etInputKey.post {
                keyWatcher.onTextChanged(
                    etInputKey.text ?: "", 0, 0, 0
                )
            }
        }

        dialog.show()
    }

    fun showCFGDialogQQBubbleRedirect(item: BaseClickableFunctionHookItem, context: Context?) {
        if (context == null) return
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle("气泡重定向")

        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(16, 16, 16, 16)

        val checkBox = MaterialCheckBox(context)
        checkBox.text = "启用"

        val textView = TextView(context)
        val input = EditText(context)
        input.hint = "气泡 Item ID"
        input.setText(QQBubbleRedirect.getItemId())
        textView.text = "气泡 Item ID"
        layout.addView(checkBox)
        layout.addView(textView)
        layout.addView(input)

        builder.setView(layout)

        val warningText = TextView(context)
        layout.addView(warningText)

        builder.setNegativeButton(
            "关闭"
        ) { dialog: DialogInterface, _: Int -> dialog.cancel() }

        checkBox.isChecked = item.isEnabled

        checkBox.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            ConfigManager.dPutBoolean(
                Constants.PrekClickableXXX + HookItemFactory.getItem(
                    QQBubbleRedirect::class.java
                ).path, isChecked
            )
            item.isEnabled = isChecked
            if (isChecked) {
                item.startLoad()
            }
        }

        builder.setPositiveButton("确定") { dialog, i ->
            val itemId = input.text.toString().trim()
            if (itemId.isEmpty()) {
                Toasts.error(context, "ItemID 不能为空")
                return@setPositiveButton
            }
            QQBubbleRedirect.createCacheFile(itemId)
            dialog.dismiss()
        }


        builder.show()
    }

    fun showCFGDialogQQPacketHelperEntry(item: BaseClickableFunctionHookItem, context: Context?) {
        if (context == null) return
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle("QQPacketHelper")

        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(16, 16, 16, 16)

        val checkBox = MaterialCheckBox(context)
        checkBox.text = "启用"

        val hookPanelCheckBox = MaterialCheckBox(context)
        hookPanelCheckBox.text = "覆盖加号菜单"

        layout.addView(checkBox)
        layout.addView(hookPanelCheckBox)

        builder.setView(layout)

        val warningText = TextView(context)
        warningText.text = "开启后长按加号按钮调出 QQPacketHelper\n* 开关需重启生效"
        layout.addView(warningText)

        builder.setPositiveButton(
            "确定"
        ) { dialog: DialogInterface, _: Int -> dialog.cancel() }

        checkBox.isChecked = item.isEnabled

        checkBox.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            ConfigManager.dPutBoolean(
                Constants.PrekClickableXXX + HookItemFactory.getItem(
                    QQPacketHelperEntry::class.java
                ).path, isChecked
            )
            item.isEnabled = isChecked
            if (isChecked) {
                item.startLoad()
            }
        }

        hookPanelCheckBox.isChecked = ConfigManager.dGetBooleanDefTrue(Constants.PrekCfgXXX + "QQPacketHelperHookPanel")

        hookPanelCheckBox.setOnCheckedChangeListener { view, isCheck ->
            ConfigManager.dPutBoolean(Constants.PrekCfgXXX + "QQPacketHelperHookPanel", isCheck)
        }

        builder.show()
    }

}
