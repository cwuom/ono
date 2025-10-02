package moe.ono.hooks.item.developer

import android.annotation.SuppressLint
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import moe.ono.R
import moe.ono.bridge.ntapi.ChatTypeConstants.C2C
import moe.ono.bridge.ntapi.ChatTypeConstants.GROUP
import moe.ono.bridge.ntapi.MsgServiceHelper
import moe.ono.config.CacheConfig.setMsgRecord
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks._core.factory.HookItemFactory.getItem
import moe.ono.hooks.base.util.Toasts
import moe.ono.hooks.dispatcher.OnMenuBuilder
import moe.ono.hooks.protocol.sendPacket
import moe.ono.reflex.Reflex
import moe.ono.util.AppRuntimeHelper
import moe.ono.util.ContextUtils
import moe.ono.util.CustomMenu
import moe.ono.util.Logger
import moe.ono.util.Session
import moe.ono.util.SyncUtils

@SuppressLint("DiscouragedApi")
@HookItem(path = "开发者选项/Element(s) 反序列化", description = "长按消息点击“拉取”进行反序列化操作")
class QQMessageFetcher : BaseSwitchFunctionHookItem(), OnMenuBuilder {
    override fun entry(classLoader: ClassLoader) {}

    override fun onGetMenu(aioMsgItem: Any, targetType: String, param: MethodHookParam) {
        if (!getItem(this.javaClass).isEnabled) {
            return
        }

        val item: Any = CustomMenu.createItemIconNt(
            aioMsgItem,
            "拉取",
            R.drawable.ic_get_app_24,
            R.id.item_pull_msg
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
                                val chatType = msgRecord.chatType

                                when (chatType) {
                                    C2C -> {
                                        pullC2CMsg(msgRecord)
                                        setMsgRecord(msgRecord)
                                    }
                                    GROUP -> {
                                        pullGroupMsg(msgRecord)
                                        setMsgRecord(msgRecord)
                                    }
                                    else -> {
                                        Toasts.info(ContextUtils.getCurrentActivity(), "不支持的聊天类型")
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

    companion object {
        fun pullGroupMsg(msgRecord: MsgRecord){
            val seq = msgRecord.msgSeq
            sendPacket("MessageSvc.PbGetGroupMsg", """{"1": ${msgRecord.peerUid}, "2": ${seq}, "3": ${seq}, "6": 0}""")
        }
    }

    private fun pullC2CMsg(msgRecord: MsgRecord){
        sendPacket("MessageSvc.PbGetOneDayRoamMsg", """{"1": ${msgRecord.peerUin}, "2": ${msgRecord.msgTime}, "3": 0, "4": 1}""")
    }
}