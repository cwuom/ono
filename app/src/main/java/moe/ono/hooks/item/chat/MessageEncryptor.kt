package moe.ono.hooks.item.chat

import android.annotation.SuppressLint
import com.google.protobuf.ByteString
import com.google.protobuf.UnknownFieldSet
import com.tencent.mobileqq.fe.FEKit
import de.robv.android.xposed.XC_MethodHook
import kotlinx.io.core.BytePacketBuilder
import kotlinx.io.core.readBytes
import kotlinx.io.core.writeFully
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import moe.ono.R
import moe.ono.bridge.ntapi.ChatTypeConstants.C2C
import moe.ono.bridge.ntapi.ChatTypeConstants.GROUP
import moe.ono.bridge.ntapi.MsgServiceHelper
import moe.ono.config.ConfigManager
import moe.ono.config.DailySaying
import moe.ono.constants.Constants
import moe.ono.ext.getUnknownObject
import moe.ono.ext.getUnknownObjects
import moe.ono.ext.toInnerValuesString
import moe.ono.hooks._base.BaseClickableFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks.base.util.Toasts
import moe.ono.hooks.dispatcher.OnMenuBuilder
import moe.ono.hooks.item.developer.QQHookCodec
import moe.ono.hooks.item.developer.QQMessageFetcher
import moe.ono.hooks.protocol.entries.QQSsoSecureInfo
import moe.ono.hooks.protocol.entries.TextMsgExtPbResvAttr
import moe.ono.hostInfo
import moe.ono.loader.hookapi.IHijacker
import moe.ono.reflex.Reflex
import moe.ono.util.AesUtils.aesEncrypt
import moe.ono.util.AesUtils.md5
import moe.ono.util.AppRuntimeHelper
import moe.ono.util.ContextUtils
import moe.ono.util.CustomMenu
import moe.ono.util.Logger
import moe.ono.util.Session
import moe.ono.util.SyncUtils

@SuppressLint("DiscouragedApi")
@HookItem(path = "聊天与消息/群聊加密消息", description = "发送的消息将加密抄送，仅针对群聊\n* 长按文本消息可手动解密(需开启手动解密)\n* 手动解密功能依赖 娱乐功能/修改文本消息\n* 开启需重启\n* 禁止用于非法用途，违者后果自负\n* 需开启 '开发者选项/注入 CodecWarpper'")
class MessageEncryptor : BaseClickableFunctionHookItem(), OnMenuBuilder {
    companion object {
        var decryptMsg = false
        var msgSeq = ""
        var peerUid = ""
        var senderUin = ""

        fun hexToBytes(hex: String): ByteArray {
            require(hex.length % 2 == 0) { "Invalid hex string length." }
            return ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }
    }

    override fun entry(classLoader: ClassLoader) {
        QQHookCodec.hijackers.add(object: IHijacker {
            override fun onHandle(
                param: XC_MethodHook.MethodHookParam,
                uin: String,
                cmd: String,
                seq: Int,
                buffer: ByteArray,
                bufferIndex: Int
            ): Boolean {
                this@MessageEncryptor.onHandle(param, uin, cmd, seq, buffer, bufferIndex)
                return false
            }
            override val command: String = "MessageSvc.PbSendMsg"
        })
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun onHandle(param: XC_MethodHook.MethodHookParam, uin: String, cmd: String, seq: Int, buffer: ByteArray, bufferIndex: Int) {
        if (!ConfigManager.isEnable(this.javaClass)) return
        if (buffer.size <= 4) return
        val unknownFields = UnknownFieldSet.parseFrom(buffer.copyOfRange(4, buffer.size))
        if (!unknownFields.hasField(1)) return
        val routingHead  = unknownFields.getUnknownObject(1)
        if (routingHead.hasField(1)) return // 私聊消息不加密
        val msgBody = unknownFields.getUnknownObject(3)

        val builder = UnknownFieldSet.newBuilder(unknownFields)
        builder.clearField(3) // 清除原消息体

        val newMsgBody = generateEncryptedMsgBody(msgBody)
        builder.addField(3, UnknownFieldSet.Field.newBuilder().also {
            it.addLengthDelimited(newMsgBody.toByteString())
        }.build())

        val data = builder.build().toByteArray()

        if (bufferIndex == 15 && param.args[13] != null) {
            // 因为包体改变，重新签名
            val qqSecurityHead = UnknownFieldSet.parseFrom(param.args[13] as ByteArray)
            val qqSecurityHeadBuilder = UnknownFieldSet.newBuilder(qqSecurityHead)
            qqSecurityHeadBuilder.clearField(24)
            val sign = FEKit.getInstance().getSign(cmd, data, seq, uin)
            Logger.d("sign ->" + sign.toInnerValuesString())
            qqSecurityHeadBuilder.addField(24, UnknownFieldSet.Field.newBuilder().also {
                it.addLengthDelimited(
                    ByteString.copyFrom(
                        ProtoBuf.encodeToByteArray(QQSsoSecureInfo(
                    secSig = sign.sign,
                    extra = sign.extra,
                    deviceToken = sign.token
                ))))
            }.build())
            param.args[13] = qqSecurityHeadBuilder.build().toByteArray()
        }

        if (bufferIndex == 15 && param.args[14] != null) {
            val qqSecurityHead = UnknownFieldSet.parseFrom(param.args[14] as ByteArray)
            val qqSecurityHeadBuilder = UnknownFieldSet.newBuilder(qqSecurityHead)
            qqSecurityHeadBuilder.clearField(24)
            val sign = FEKit.getInstance().getSign(cmd, data, seq, uin)
            qqSecurityHeadBuilder.addField(24, UnknownFieldSet.Field.newBuilder().also {
                it.addLengthDelimited(
                    ByteString.copyFrom(
                        ProtoBuf.encodeToByteArray(QQSsoSecureInfo(
                    secSig = sign.sign,
                    extra = sign.extra,
                    deviceToken = sign.token
                ))))
            }.build())
            param.args[14] = qqSecurityHeadBuilder.build().toByteArray()
        }

        Toasts.info(hostInfo.application, "此消息正在加密抄送..")

        param.args[bufferIndex] = BytePacketBuilder().also {
            it.writeInt(data.size + 4)
            it.writeFully(data)
        }.build().readBytes()
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun generateEncryptedMsgBody(msgBody: UnknownFieldSet): UnknownFieldSet {
        val encryptKey = ConfigManager.getStringConfig(this.javaClass, "ono")
        if (encryptKey.isBlank()) {
            // 未设置加密密钥
            return msgBody
        }

        val elements = UnknownFieldSet.Field.newBuilder()
        msgBody.getUnknownObject(1).let { richText ->
            richText.getUnknownObjects(2).forEach { element ->
                if (element.hasField(37) || element.hasField(9)) {
                    elements.addLengthDelimited(element.toByteString()) // 通用字段，不自己合成
                }
            }
        }

        val newMsgBody = UnknownFieldSet.newBuilder()
        val richText = UnknownFieldSet.newBuilder()

        elements.addLengthDelimited(UnknownFieldSet.newBuilder().also { builder ->
            builder.addField(1, UnknownFieldSet.Field.newBuilder().also { it ->
                it.addLengthDelimited(UnknownFieldSet.newBuilder().also { textElement ->
                    textElement.addField(1, UnknownFieldSet.Field.newBuilder().also { content ->
                        var display = ConfigManager.getStringConfig(this.javaClass, "", "display")
                        if (display.isEmpty()) {
                            display = DailySaying.getSaying()
                        }
                        content.addLengthDelimited(ByteString.copyFromUtf8(display))
                    }.build())

                    textElement.addField(12, UnknownFieldSet.Field.newBuilder().also { content ->
                        content.addLengthDelimited(
                            ByteString.copyFrom(
                                ProtoBuf.encodeToByteArray(TextMsgExtPbResvAttr(
                            wording = BytePacketBuilder().also {
                                it.writeInt(0x114514)
                                it.writeInt(encryptKey.hashCode())
                                it.writeFully(aesEncrypt(msgBody.toByteArray(), md5(encryptKey)))
                            }.build().readBytes()
                        ))))
                    }.build())
                }.build().toByteString())
            }.build())
        }.build().toByteString()) // add text

        richText.addField(2, elements.build())

        newMsgBody.addField(1, UnknownFieldSet.Field.newBuilder().also {
            it.addLengthDelimited(richText.build().toByteString())
        }.build())

        return newMsgBody.build()
    }

    override val targetTypes = arrayOf(
        "com.tencent.mobileqq.aio.msglist.holder.component.text.AIOTextContentComponent",
    )

    override fun onGetMenu(aioMsgItem: Any, targetType: String, param: XC_MethodHook.MethodHookParam) {
        if (!ConfigManager.dGetBoolean(Constants.PrekCfgXXX + "manualDecrypt")) {
            return
        }

        val item: Any = CustomMenu.createItemIconNt(
            aioMsgItem,
            "解密",
            R.drawable.ic_telegram,
            R.id.item_message_encryptor
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
//                                if (msgRecord.msgType != 2 || msgRecord.subMsgType != 1) {
//                                    Toasts.error(ContextUtils.getCurrentActivity(), "仅支持文本消息")
//                                    return@runOnUiThread
//                                }

                                when (msgRecord.chatType) {
                                    C2C -> {
                                        Toasts.error(ContextUtils.getCurrentActivity(), "仅支持群聊消息")
                                        break
                                    }
                                    GROUP -> {
                                        decryptMsg = true
                                        msgSeq = msgRecord.msgSeq.toString()
                                        peerUid = msgRecord.peerUid
                                        senderUin = msgRecord.senderUin.toString()
                                        QQMessageFetcher.pullGroupMsg(msgRecord)
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

    override fun alwaysRun(): Boolean {
        return true
    }

    override fun targetProcess(): Int {
        return SyncUtils.PROC_MSF
    }
}

