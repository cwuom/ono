package moe.ono.hooks.item.chat

import android.annotation.SuppressLint
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import moe.ono.R
import moe.ono.bridge.ntapi.ChatTypeConstants.C2C
import moe.ono.bridge.ntapi.ChatTypeConstants.GROUP
import moe.ono.bridge.ntapi.MsgServiceHelper
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks._core.factory.HookItemFactory.getItem
import moe.ono.hooks.base.util.Toasts
import moe.ono.hooks.dispatcher.OnMenuBuilder
import moe.ono.hooks.item.developer.QQMessageFetcher
import moe.ono.hooks.protocol.sendPacket
import moe.ono.reflex.Reflex
import moe.ono.util.AppRuntimeHelper
import moe.ono.util.ContextUtils
import moe.ono.util.CustomMenu
import moe.ono.util.Logger
import moe.ono.util.Session
import moe.ono.util.SyncUtils
import java.util.ArrayList

@SuppressLint("DiscouragedApi")
@HookItem(
    path = "聊天与消息/假文件撤回",
    description = "使其能够撤回假文件\n* 在长按文件的消息菜单使用"
)
class FakeFileRecall : BaseSwitchFunctionHookItem(), OnMenuBuilder {

    companion object {
        var isProtocolRecall = false
        var mPeerId = ""
        var mMsgRandom = ""
        var mMsgTime = ""
        var mMsgSeq = ""
        var mClientSeq = ""
        var unknownId = ""

        fun recallC2CMsg(peerId: String, msgRandom: String, msgTime: String, msgSeq: String, clientSeq: String) {
            val body = "{\n" +
                    "  \"1\": 1,\n" +
                    "  \"3\": \""+ peerId +"\",\n" +
                    "  \"4\": {\n" +
                    "    \"1\": "+ clientSeq +",\n" +
                    "    \"2\": "+ msgRandom +",\n" +
                    "    \"3\": "+unknownId+",\n" +
                    "    \"4\": "+ msgTime +",\n" +
                    "    \"5\": 0,\n" +
                    "    \"6\": "+ msgSeq +"\n" +
                    "  },\n" +
                    "  \"5\": {\n" +
                    "    \"1\": 0,\n" +
                    "    \"2\": 0\n" +
                    "  },\n" +
                    "  \"6\": 0\n" +
                    "}"
            sendPacket("trpc.msg.msg_svc.MsgService.SsoC2CRecallMsg", body)
        }
    }

    override val targetTypes: Array<String> = arrayOf(
        "com.tencent.mobileqq.aio.msglist.holder.component.file.AIOFileContentComponent",
        "com.tencent.mobileqq.aio.msglist.holder.component.file.AIOOnlineFileContentComponent",
    )

    @Throws(Throwable::class)
    override fun entry(classLoader: ClassLoader) {
    }


    override fun onGetMenu(aioMsgItem: Any, targetType: String, param: MethodHookParam) {
        if (!getItem(this.javaClass).isEnabled) {
            return
        }
        val item: Any = CustomMenu.createItemIconNt(
            aioMsgItem,
            "假文件撤回",
            R.drawable.ic_baseline_auto_fix_high_24, // 懒得找图片 qwq
            R.id.item_protocol_recall
        ) {
            try {
                val msgID = Reflex.invokeVirtual(aioMsgItem, "getMsgId") as Long
                val msgIDs = ArrayList<Long>()
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
                                when (msgRecord.chatType) {
                                    C2C -> {
                                        recallC2CMsg(msgRecord.peerUid, msgRecord.msgRandom.toString(), msgRecord.msgTime.toString(), msgRecord.msgSeq.toString(), msgRecord.clientSeq.toString(), msgRecord)
                                        Toasts.success(ContextUtils.getCurrentActivity(), "请求成功")
                                        break
                                    }
                                    GROUP -> {
                                        recallGroupMsg(msgRecord.peerUin.toString(), msgRecord.msgRandom.toString(), msgRecord.msgSeq.toString())
                                        Toasts.success(ContextUtils.getCurrentActivity(), "请求成功")
                                        break
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

    fun recallC2CMsg(peerId: String, msgRandom: String, msgTime: String, msgSeq: String, clientSeq: String, msgRecord: MsgRecord) {
        isProtocolRecall = true
        if (msgRecord.senderUid == Session.getContact().peerUid) return
        mPeerId = peerId
        mMsgRandom = msgRandom
        mMsgTime = msgTime
        mMsgSeq = msgSeq
        mClientSeq = clientSeq
        QQMessageFetcher.pullC2CMsg(msgRecord)
    }

    fun recallGroupMsg(uin: String, msgRandom: String, msgSeq: String) {
        val body = "{\n" +
                "  \"1\": 1,\n" +
                "  \"2\": "+uin+",\n" +
                "  \"3\": {\n" +
                "    \"1\": "+msgSeq+",\n" +
                "    \"2\": "+msgRandom+",\n" +
                "    \"3\": 0\n" +
                "  },\n" +
                "  \"4\": {\n" +
                "    \"1\": 0\n" +
                "  }\n" +
                "}"
        sendPacket("trpc.msg.msg_svc.MsgService.SsoGroupRecallMsg", body)
    }
}