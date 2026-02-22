package moe.ono.hooks.base.api

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import de.robv.android.xposed.XposedBridge
import moe.ono.bridge.kernelcompat.ContactCompat
import moe.ono.config.ConfigManager
import moe.ono.config.ONOConf
import moe.ono.constants.Constants
import moe.ono.hooks._base.ApiHookItem
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks._core.factory.ExceptionFactory
import moe.ono.hooks._core.factory.HookItemFactory
import moe.ono.hooks.item.chat.SelfMessageReactor
import moe.ono.hooks.protocol.buildMessage
import moe.ono.hooks.protocol.sendPacket
import moe.ono.hostInfo
import moe.ono.reflex.ClassUtils
import moe.ono.reflex.FieldUtils
import moe.ono.reflex.Ignore
import moe.ono.reflex.MethodUtils
import moe.ono.service.QQInterfaces
import moe.ono.util.Initiator.loadClass
import moe.ono.util.Logger
import moe.ono.util.QAppUtils
import java.lang.ref.WeakReference
import java.lang.reflect.Method


@HookItem(path = "API/监听QQMsgView更新")
class QQMessageViewListener : ApiHookItem() {
    var contact: ContactCompat? = null

    companion object {

        private val ON_AIO_CHAT_VIEW_UPDATE_LISTENER_MAP: HashMap<BaseSwitchFunctionHookItem, OnChatViewUpdateListener> =
            HashMap()

        /**
         * 添加消息监听器 责任链模式
         */
        @JvmStatic
        fun addMessageViewUpdateListener(
            hookItem: BaseSwitchFunctionHookItem,
            onMsgViewUpdateListener: OnChatViewUpdateListener
        ) {
            ON_AIO_CHAT_VIEW_UPDATE_LISTENER_MAP[hookItem] = onMsgViewUpdateListener
        }
    }

    override fun entry(loader: ClassLoader) {
        try {
            val onMsgViewUpdate =
                MethodUtils.create("com.tencent.mobileqq.aio.msglist.holder.AIOBubbleMsgItemVB").methodName("handleUIState").first()
            hookAfter(onMsgViewUpdate) { param ->
                val thisObject = param.thisObject
                val msgView = FieldUtils.create(thisObject)
                    .fieldType(View::class.java)
                    .firstValue<View>(thisObject)

                val aioMsgItem = FieldUtils.create(thisObject)
                    .fieldType(ClassUtils.findClass("com.tencent.mobileqq.aio.msg.AIOMsgItem"))
                    .firstValue<Any>(thisObject)

                onViewUpdate(aioMsgItem, msgView)
            }
        } catch (e: Exception) {
            Logger.e(e)
        }
    }

    private fun onViewUpdate(aioMsgItem: Any, msgView: View) {
        val msgRecord: MsgRecord = MethodUtils.create(aioMsgItem.javaClass)
            .methodName("getMsgRecord")
            .callFirst(aioMsgItem)

        val peerUid = msgRecord.peerUid
        val msgSeq = msgRecord.msgSeq
        val isSendBySelf = msgRecord.senderUin == QAppUtils.getCurrentUin().toLong()



        for ((switchFunctionHookItem, listener) in ON_AIO_CHAT_VIEW_UPDATE_LISTENER_MAP.entries) {
            if (switchFunctionHookItem.isEnabled) {
                try {
                    listener.onViewUpdateAfter(msgView, msgRecord)
                } catch (e: Throwable) {
                    ExceptionFactory.add(switchFunctionHookItem, e)
                }
            }
        }


        ONOConf.setInt("ChatScrollMemory", peerUid, msgSeq.toInt())
    }

    interface OnChatViewUpdateListener {
        fun onViewUpdateAfter(msgItemView: View, msgRecord: Any)
    }
}