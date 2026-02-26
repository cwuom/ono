package moe.ono.hooks.item.entertainment

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import moe.ono.R
import moe.ono.bridge.ntapi.MsgServiceHelper
import moe.ono.config.ConfigManager
import moe.ono.constants.Constants
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks._core.factory.HookItemFactory.getItem
import moe.ono.hooks.base.api.QQMessageViewListener
import moe.ono.hooks.base.api.QQMsgViewAdapter
import moe.ono.hooks.base.util.Toasts
import moe.ono.hooks.dispatcher.OnMenuBuilder
import moe.ono.reflex.FieldUtils
import moe.ono.reflex.Reflex
import moe.ono.ui.CommonContextWrapper
import moe.ono.util.AppRuntimeHelper
import moe.ono.util.ContextUtils
import moe.ono.util.CustomMenu
import moe.ono.util.Logger
import moe.ono.util.Session
import moe.ono.util.SyncUtils

@HookItem(path = "娱乐功能/修改文本消息内容", description = "修改显示文本\n\n* 文本消息长按菜单中使用")
class ModifyTextMessage : BaseSwitchFunctionHookItem(), OnMenuBuilder {

    companion object {
        val modifyMap = mutableMapOf<String, String>()
    }

    override fun entry(classLoader: ClassLoader) {
        QQMessageViewListener.addMessageViewUpdateListener(this,
            object : QQMessageViewListener.OnChatViewUpdateListener {
                override fun onViewUpdateAfter(msgItemView: View, msgRecord: Any) {
                    modifyMessage(msgItemView, msgRecord)
                }
            })
    }

    /**
     * 修改消息内容
     */
    private fun modifyMessage(msgItemView: View, msgRecord: Any) {
        if (!ConfigManager.getDefaultConfig().getBooleanOrFalse(Constants.PrekXXX + getItem(this::class.java).path)) return

        val contentView = QQMsgViewAdapter.getContentView(msgItemView)

        val peerUid: String = FieldUtils.create(msgRecord)
            .fieldName("peerUid")
            .fieldType(String::class.java)
            .firstValue(msgRecord)

        val msgSeq: Long = FieldUtils.create(msgRecord)
            .fieldName("msgSeq")
            .fieldType(Long::class.javaPrimitiveType)
            .firstValue(msgRecord)

        val senderUin: Long = FieldUtils.create(msgRecord)
            .fieldName("senderUin")
            .fieldType(Long::class.java)
            .firstValue(msgRecord)

        if (!modifyMap.contains("$peerUid:$msgSeq:$senderUin")) return

        val msgType: Int = FieldUtils.create(msgRecord)
            .fieldName("msgType")
            .fieldType(Int::class.java)
            .firstValue(msgRecord)

        if (msgType <= 2) {
            if (contentView is ViewGroup) {
                if (contentView.getChildAt(0) is ViewGroup) {
                    val parent = contentView.getChildAt(0) as ViewGroup
                    if (parent.getChildAt(0) is ViewGroup) {
                        val parent2 = parent.getChildAt(0) as ViewGroup
                        if (parent2.getChildAt(0) is ViewGroup) {
                            val parent3 = parent2.getChildAt(0) as ViewGroup
                            val aioMsgTextView = parent3.getChildAt(0) as TextView
                            aioMsgTextView.setText(modifyMap.get("$peerUid:$msgSeq:$senderUin"))
                        }
                    }
                }
            }
        }
    }

    override val targetTypes = arrayOf(
        "com.tencent.mobileqq.aio.msglist.holder.component.text.AIOTextContentComponent",
    )

    override fun onGetMenu(aioMsgItem: Any, targetType: String, param: MethodHookParam) {
        if (!getItem(this.javaClass).isEnabled) {
            return
        }

        val item: Any = CustomMenu.createItemIconNt(
            aioMsgItem,
            "修改",
            R.drawable.ic_baseline_border_color_24,
            R.id.item_modify_text_message
        ) {
            try {
                val msgID = Reflex.invokeVirtual(aioMsgItem, "getMsgId") as Long
                val msgIDs = java.util.ArrayList<Long>()
                msgIDs.add(msgID)
                AppRuntimeHelper.getAppRuntime()
                    ?.let {
                        MsgServiceHelper.getKernelMsgService(
                            it
                        )
                    }?.getMsgsByMsgId(
                        Session.getContact(),
                        msgIDs
                    ) { _, _, msgList ->
                        SyncUtils.runOnUiThread {
                            for (msgRecord in msgList) {
                                if (!(msgRecord.msgType == 2 && msgRecord.subMsgType == 1)) {
                                    Toasts.error(ContextUtils.getCurrentActivity(), "仅支持文本消息")
                                } else {
                                    val elem = msgRecord.elements[0].textElement
                                    if (elem == null) {
                                        Toasts.error(ContextUtils.getCurrentActivity(), "仅支持文本消息")
                                    } else {
                                        val context = CommonContextWrapper.createAppCompatContext(
                                            ContextUtils.getCurrentActivity())

                                        val dialog = MaterialAlertDialogBuilder(context)
                                        dialog.setTitle("修改文本消息")

                                        val content = EditText(context)
                                        content.setHint("请输入修改后的内容")
                                        content.setText(elem.content)

                                        dialog.setPositiveButton("修改") { dialogInterface, i ->
                                            modifyMap.put("${msgRecord.peerUid}:${msgRecord.msgSeq}:${msgRecord.senderUin}", content.text.toString())
                                            Toasts.success(ContextUtils.getCurrentActivity(), "修改成功重新进入此界面生效")
                                            dialogInterface.dismiss()
                                        }

                                        dialog.setView(content)
                                        dialog.show()
                                    }
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                Logger.e("QQPullMsgEntry.msgLongClick", e)
            }
            Unit
        }
        param.result = listOf(item) + param.result as List<*>
    }
}