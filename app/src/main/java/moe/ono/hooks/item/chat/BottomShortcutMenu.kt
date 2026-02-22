package moe.ono.hooks.item.chat

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lxj.xpopup.XPopup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedHelpers
import moe.ono.R
import moe.ono.bridge.ntapi.ChatTypeConstants
import moe.ono.config.CacheConfig
import moe.ono.config.ConfigManager
import moe.ono.constants.Constants
import moe.ono.creator.FakeFileSender
import moe.ono.creator.GetChannelArkDialog
import moe.ono.creator.JumpSchemeUriDialog
import moe.ono.creator.PacketHelperDialog
import moe.ono.creator.QQMessageTrackerDialog
import moe.ono.dexkit.TargetManager
import moe.ono.hooks.XHook
import moe.ono.hooks._base.BaseClickableFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks._core.factory.HookItemFactory.getItem
import moe.ono.hooks.base.util.Toasts
import moe.ono.hooks.item.developer.GetBknByCookie
import moe.ono.hooks.item.developer.GetCookie
import moe.ono.hooks.item.developer.JumpSchemeUri
import moe.ono.hooks.item.developer.QQHookCodec
import moe.ono.hooks.item.developer.QQPacketHelperEntry
import moe.ono.hooks.item.sigma.QQMessageTracker
import moe.ono.loader.hookapi.IShortcutMenu
import moe.ono.reflex.XMethod
import moe.ono.ui.CommonContextWrapper
import moe.ono.util.Initiator
import moe.ono.util.Logger
import moe.ono.util.Session
import moe.ono.util.SyncUtils

@SuppressLint("DiscouragedApi")
@HookItem(
    path = "聊天与消息/快捷菜单",
    description = "点击查看调出方式，部分功能依赖此选项，推荐开启\n* 重启生效"
)
class BottomShortcutMenu : BaseClickableFunctionHookItem() {
    private val classNames: List<String> = mutableListOf(
        "com.tencent.mobileqq.aio.msglist.holder.component.avatar.AIOAvatarContentComponent",
        "com.tencent.mobileqq.aio.msglist.holder.component.nick.block.MainNickNameBlock"
    )

    private fun hook() {
        try {
            if (ConfigManager.dGetInt(Constants.PrekCfgXXX + "bottom_shortcut_menu_mode", 0) == 1) {
                val method = XMethod.clz(Constants.CLAZZ_PANEL_ICON_LINEAR_LAYOUT).ret(
                    ImageView::class.java
                ).ignoreParam().get()

                hookAfter(method) { param: MethodHookParam ->
                    if (Session.getContact().chatType != ChatTypeConstants.C2C && Session.getContact().chatType != ChatTypeConstants.GROUP) {
                        return@hookAfter
                    }

                    val imageView = param.result as ImageView

                    if (!this.isEnabled) return@hookAfter
                    if ("红包".contentEquals(imageView.contentDescription)) {
                        imageView.setOnLongClickListener { view ->
                            val fixContext = CommonContextWrapper.createAppCompatContext(imageView.context)
                            popMenu(fixContext, view)
                            true
                        }
                    }
                }
            } else {
                val method = TargetManager.requireMethod(Constants.MethodCacheKey_ChatPanelBtn)
                hookAfter(method) { param: MethodHookParam? ->
                    val layout = param!!.thisObject as LinearLayout
                    if (Session.getContact().chatType != ChatTypeConstants.C2C && Session.getContact().chatType != ChatTypeConstants.GROUP) return@hookAfter
                    if (!this.isEnabled) return@hookAfter
                    for (i in 0..<layout.childCount) {
                        val child = layout.getChildAt(i)
                        if (child is ImageView && child.contentDescription != null) {
                            if ("泡泡".contentEquals(child.contentDescription)) {
                                child.addOnAttachStateChangeListener(object :
                                    View.OnAttachStateChangeListener {
                                    private val ONO_TAG = "ono_image_tag"
                                    override fun onViewAttachedToWindow(v: View) {
                                        v.post {
                                            val parent = v.parent as? ViewGroup ?: return@post

                                            if ((0 until parent.childCount).any {
                                                    parent.getChildAt(it).tag == ONO_TAG
                                                }) return@post

                                            val onoIcon = ImageView(v.context).apply {
                                                setImageResource(R.drawable.ic_ouo)
                                                tag = ONO_TAG
                                                setOnClickListener { v ->
                                                    popMenu(CommonContextWrapper.createAppCompatContext(v.context), view = v)
                                                }
                                            }

                                            parent.addView(onoIcon, minOf(4, parent.childCount))
                                        }
                                    }
                                    override fun onViewDetachedFromWindow(p0: View) {}
                                })
                            }
                        }
                    }
                }
            }

            processForInstance()
        } catch (e: NoSuchMethodException) {
            Logger.e(this.itemName, e)
        }
    }

    private fun popMenu(fixCtx: Context, view: View) {
        val sendFakeFile =
            ConfigManager.getDefaultConfig().getBooleanOrFalse(Constants.PrekSendFakeFile)
        val qqPacketHelper = ConfigManager.getDefaultConfig().getBooleanOrFalse(
            Constants.PrekClickableXXX + getItem(
                QQPacketHelperEntry::class.java
            ).path
        )
        val qqMessageTracker = ConfigManager.getDefaultConfig().getBooleanOrFalse(
            Constants.PrekClickableXXX + getItem(
                QQMessageTracker::class.java
            ).path
        )

        val messageEncryptor = ConfigManager.getDefaultConfig().getBooleanOrFalse(
            Constants.PrekClickableXXX + getItem(
                MessageEncryptor::class.java
            ).path
        )

        val getCookie = ConfigManager.getDefaultConfig().getBooleanOrFalse(Constants.PrekXXX + getItem(
            GetCookie::class.java).path)

        val getBknByCookie = ConfigManager.getDefaultConfig().getBooleanOrFalse(Constants.PrekXXX + getItem(
            GetBknByCookie::class.java).path)

        val getChannelArk = ConfigManager.getDefaultConfig().getBooleanOrFalse(Constants.PrekXXX + getItem(
            GetChannelArk::class.java).path)

        val jumpSchemeUri = ConfigManager.getDefaultConfig().getBooleanOrFalse(Constants.PrekXXX + getItem(
            JumpSchemeUri::class.java).path)

        val items = ArrayList<String>()
        if (qqPacketHelper) {
            items.add("QQPacketHelper")
        }
        if (sendFakeFile) {
            items.add("假文件")
        }
        if (qqMessageTracker) {
            items.add("已读追踪")
        }
        if (getCookie) {
            items.add("GetCookie")
        }
        if (getBknByCookie) {
            items.add("GetBknByCookie")
        }
        if (getChannelArk) {
            items.add("GetChannelArk")
        }
        if (jumpSchemeUri) {
            items.add("打开 Scheme 链接")
        }

        menus.forEach { menu ->
            if (menu.isAdd()) {
                items.add(menu.menuName)
            }
        }

        if (getItem(QQHookCodec::class.java).isEnabled) {
            if (!messageEncryptor) {
                items.add("开启加密抄送")
            } else {
                items.add("关闭加密抄送")
            }
        }



        items.add("匿名化")
        XPopup.Builder(fixCtx)
            .hasShadowBg(false)
            .atView(view)
            .asAttachList(
                items.toTypedArray<String>(),
                intArrayOf()
            ) { _: Int, text: String? ->
                menus.forEach { menu ->
                    if (text == menu.menuName) {
                        menu.clickHandle(view.context)
                        return@asAttachList
                    }
                }

                when (text) {
                    "QQPacketHelper" -> SyncUtils.runOnUiThread {
                        PacketHelperDialog.createView(
                            null,
                            view.context,
                            ""
                        )
                    }

                    "匿名化" -> autoMosaicNameNT()
                    "假文件" -> try {
                        SyncUtils.runOnUiThread { FakeFileSender.createView(view.context) }
                    } catch (e: Exception) {
                        Toasts.error(view.context, "请求失败")
                    }
                    "已读追踪" -> try {
                        SyncUtils.runOnUiThread { QQMessageTrackerDialog.createView(view.context) }
                    } catch (e: Exception) {
                        Toasts.error(view.context, "请求失败")
                    }
                    "开启加密抄送" -> {
                        ConfigManager.dPutBoolean(
                            Constants.PrekClickableXXX + getItem(
                                MessageEncryptor::class.java
                            ).path, true
                        )
                    }
                    "关闭加密抄送" -> {
                        ConfigManager.dPutBoolean(
                            Constants.PrekClickableXXX + getItem(
                                MessageEncryptor::class.java
                            ).path, false
                        )
                    }
                    "GetCookie" -> {
                        SyncUtils.runOnUiThread {
                            val builder = MaterialAlertDialogBuilder(
                                CommonContextWrapper.createAppCompatContext(view.context)
                            )

                            builder.setTitle("请输入域名")

                            val domain =
                                EditText(CommonContextWrapper.createAppCompatContext(view.context))
                            domain.setHint("请输入域名")
                            domain.setText("qzone.qq.com")

                            builder.setView(domain)

                            builder.setNegativeButton("取消") { dialog, i ->
                                dialog.dismiss()
                            }
                            builder.setPositiveButton("确定") { dialog, i ->
                                GetCookie.getCookie(view.context, domain.text.toString())
                            }

                            builder.show()
                        }
                    }
                    "GetBknByCookie" -> {
                        SyncUtils.runOnUiThread {
                            val builder = MaterialAlertDialogBuilder(CommonContextWrapper.createAppCompatContext(view.context))

                            builder.setTitle("请输入 Cookie")

                            val cookie = EditText(CommonContextWrapper.createAppCompatContext(view.context))
                            cookie.setHint("请输入 Cookie")

                            builder.setView(cookie)

                            builder.setNegativeButton("取消") { dialog, i ->
                                dialog.dismiss()
                            }
                            builder.setPositiveButton("确定") { dialog, i ->
                                GetBknByCookie.getBkn(CommonContextWrapper.createAppCompatContext(view.context), cookie.text.toString())
                            }

                            builder.show()
                        }
                    }
                    "GetChannelArk" -> {
                        SyncUtils.runOnUiThread { GetChannelArkDialog.createView(view.context) }
                    }
                    "打开 Scheme 链接" -> {
                        SyncUtils.runOnUiThread { JumpSchemeUriDialog.createView(view.context) }
                    }
                }
            }
            .show()
    }


    private fun processForInstance() {
        // com.tencent.mobileqq.aio.msglist.holder.component.avatar.AIOAvatarContentComponent
        try {
            val clazz = checkNotNull(Initiator.load(classNames[0]))
            for (method in clazz.declaredMethods) {
                try {
                    if (method.parameterCount == 1 && method.parameterTypes[0] == java.lang.Boolean.TYPE) {
                        componentMethodName = method.name

                        XHook.hookBefore(method) { param: MethodHookParam ->
                            if (instanceForComponent.size >= MAX_SIZE) {
                                instanceForComponent.removeAt(0)
                            }
                            instanceForComponent.add(param.thisObject)
                            if (CacheConfig.isAutoMosaicNameNT()) {
                                param.args[0] = true
                            }
                        }
                    }
                } catch (ignored: ArrayIndexOutOfBoundsException) {
                }
            }
        } catch (e: Exception) {
            Logger.e(e)
        }

        // com.tencent.mobileqq.aio.msglist.holder.component.nick.block.MainNickNameBlock
        try {
            val clazz = checkNotNull(Initiator.load(classNames[1]))
            for (method in clazz.declaredMethods) {
                try {
                    if (method.parameterCount == 1 && method.parameterTypes[0] == java.lang.Boolean.TYPE) {
                        componentMethodNameForMainNickNameBlock = method.name
                        XHook.hookBefore(method) { param: MethodHookParam ->
                            if (instancesForMainNickNameBlock.size >= MAX_SIZE) {
                                instancesForMainNickNameBlock.removeAt(
                                    0
                                )
                            }
                            instancesForMainNickNameBlock.add(param.thisObject)
                            if (CacheConfig.isAutoMosaicNameNT()) {
                                param.args[0] = true
                            }
                        }
                    }
                } catch (ignored: ArrayIndexOutOfBoundsException) {
                }
            }
        } catch (e: Exception) {
            Logger.e(e)
        }
    }


    private fun autoMosaicNameNT() {
        CacheConfig.setAutoMosaicNameNT(!CacheConfig.isAutoMosaicNameNT())

        val instancesCopy: List<Any> = ArrayList(instanceForComponent)

        for (instance in instancesCopy) {
            try {
                val matchingMethod = componentMethodName?.let {
                    instance.javaClass.getDeclaredMethod(
                        it,
                        Boolean::class.javaPrimitiveType
                    )
                }
                matchingMethod?.isAccessible = true
                matchingMethod?.invoke(instance, CacheConfig.isAutoMosaicNameNT())
            } catch (e: Exception) {
                Logger.e("Error invoking method on instance: " + e.message)
            }
        }

        val instancesCopyForMainNickNameBlock: List<Any> = ArrayList(
            instancesForMainNickNameBlock
        )

        for (instance in instancesCopyForMainNickNameBlock) {
            try {
                val matchingMethod = componentMethodNameForMainNickNameBlock?.let {
                    instance.javaClass.getDeclaredMethod(
                        it,
                        Boolean::class.javaPrimitiveType
                    )
                }
                matchingMethod?.isAccessible = true
                matchingMethod?.invoke(instance, CacheConfig.isAutoMosaicNameNT())
            } catch (e: Exception) {
                Logger.e("Error invoking method on instance: " + e.message)
            }
        }
    }

    @Throws(Throwable::class)
    override fun entry(classLoader: ClassLoader) {
        try {
            val clazz = Initiator.loadClass(Constants.CLAZZ_ACTIVITY_SPLASH)
            XposedHelpers.findAndHookMethod(clazz, "doOnCreate", Bundle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        hook()
                    }
                })
        } catch (e: ClassNotFoundException) {
            Logger.e(this.itemName, e)
        }
    }

    companion object {
        private val instanceForComponent = ArrayList<Any>()
        private val instancesForMainNickNameBlock = ArrayList<Any>()
        private var componentMethodName: String? = null
        private var componentMethodNameForMainNickNameBlock: String? = null
        private const val MAX_SIZE = 15

        fun showCFGDialog(context: Context) {
            val fixContext = CommonContextWrapper.createAppCompatContext(context)

            val builder = MaterialAlertDialogBuilder(fixContext)
            builder.setTitle("快捷菜单")

            val layout = LinearLayout(fixContext).apply {
                orientation = LinearLayout.VERTICAL
                val pad = 32
                setPadding(pad, pad, pad, pad)
            }

            val checkBox = MaterialCheckBox(fixContext).apply {
                text = "启用"
                isChecked = ConfigManager.dGetBoolean(
                    Constants.PrekClickableXXX + getItem(BottomShortcutMenu::class.java).path
                )
                setOnCheckedChangeListener { _, p1 ->
                    ConfigManager.dPutBoolean(
                        Constants.PrekClickableXXX + getItem(BottomShortcutMenu::class.java).path,
                        p1
                    )
                    getItem(BottomShortcutMenu::class.java).isEnabled = p1
                    Toasts.success(fixContext, "重启生效")
                }
            }
            layout.addView(checkBox)

            val tips = listOf(
                "点击聊天界面下方 ONO 图标调出菜单",
                "长按聊天界面下方红包按钮调出菜单"
            )
            val currentIndex = ConfigManager.dGetInt(Constants.PrekCfgXXX + "bottom_shortcut_menu_mode", 0)

            val tipsText = TextView(fixContext).apply {
                text = tips[currentIndex]
                textSize = 14f
                setPadding(0, 16, 0, 0)
            }
            val dropdown = AutoCompleteTextView(fixContext).apply {
                val options = listOf("增加 ONO 图标", "红包")
                isFocusable = false
                isCursorVisible = false
                isLongClickable = false
                keyListener = null
                inputType = 0
                setTextIsSelectable(false)
                setCompoundDrawablesWithIntrinsicBounds(
                    0, 0,
                    com.google.android.material.R.drawable.mtrl_dropdown_arrow, 0
                )
                compoundDrawablePadding = 16
                val adapter = ArrayAdapter(fixContext, android.R.layout.simple_list_item_1, options)
                setAdapter(adapter)
                setText(options[currentIndex], false)
                setOnClickListener { showDropDown() }
                setOnItemClickListener { _, _, position, _ ->
                    ConfigManager.dPutInt(
                        Constants.PrekCfgXXX + "bottom_shortcut_menu_mode",
                        position
                    )
                    tipsText.text = tips[position]
                }
            }
            val title = TextView(fixContext).apply {
                text = "选择 Hook 模式"
                textSize = 15f
                setPadding(0, 24, 0, 8)
            }
            layout.addView(title)
            layout.addView(dropdown)
            layout.addView(tipsText)

            builder.setView(layout)
            builder.setPositiveButton("确定") { dialog, _ -> dialog.dismiss() }
            builder.show()
        }
        val menus = arrayListOf<IShortcutMenu>()
    }
}