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
import moe.ono.ext.getUnknownObject
import moe.ono.ext.toInnerValuesString
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks.item.developer.QQHookCodec
import moe.ono.hooks.protocol.entries.QQSsoSecureInfo
import moe.ono.loader.hookapi.IHijacker
import moe.ono.util.FunProtoData
import moe.ono.util.Logger
import moe.ono.util.SyncUtils

@SuppressLint("DiscouragedApi")
@HookItem(path = "聊天与消息/哔哩哔哩自动转播放卡", description = "从哔哩哔哩分享的小程序卡片将会自动转换为点击就自动播放的卡片\n* 需开启 '开发者选项/注入 CodecWarpper'\n* 重启生效")
class BilibiliAutoPlayCard : BaseSwitchFunctionHookItem() {
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
                this@BilibiliAutoPlayCard.onHandle(param, uin, cmd, seq, buffer, bufferIndex)
                return false
            }
            override val command: String = "LightAppSvc.mini_app_share.AdaptShareInfo"
        })
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun onHandle(param: XC_MethodHook.MethodHookParam, uin: String, cmd: String, seq: Int, buffer: ByteArray, bufferIndex: Int) {
        val data = FunProtoData()
        data.fromBytes(FunProtoData.getUnpPackage(buffer))
        Logger.d(data.toJSON().toString())

        val json = data.toJSON().optJSONObject("4")
        val appid = json?.optString("2").toString()

        if (appid != "1109937557") return

        val path = json?.optString("11").toString()
        val bvid = path.substringAfter("bvid=").substringBefore("&share_source")

        if (bvid.isEmpty()) return

        Logger.d(bvid)

        val changePath =
            "pages/packages/fallback/webview/webview.html?url=https%3a%2f%2fwww.bilibili.com%2fblackboard%2fnewplayer.html%3fbvid%3d$bvid%26page%3d1%26autoplay%3d1"

        val mBuffer = replaceField4_11_inPacket(buffer, changePath)

        Logger.d(FunProtoData().also { it.fromBytes(FunProtoData.getUnpPackage(mBuffer)) }.toJSON().toString())

        if (bufferIndex == 15 && param.args[13] != null) {
            // 因为包体改变，重新签名
            val qqSecurityHead = UnknownFieldSet.parseFrom(param.args[13] as ByteArray)
            val qqSecurityHeadBuilder = UnknownFieldSet.newBuilder(qqSecurityHead)
            qqSecurityHeadBuilder.clearField(24)
            val sign = FEKit.getInstance().getSign("LightAppSvc.mini_app_privacy.GetPrivacyInfo", mBuffer, seq, uin)
//            FEKit.getInstance().cmdWhiteList.forEach { it ->
//                Logger.d(it)
//            }
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
            val sign = FEKit.getInstance().getSign("LightAppSvc.mini_app_privacy.GetPrivacyInfo", mBuffer, seq, uin)
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

        param.args[bufferIndex] = BytePacketBuilder().also {
            it.writeInt(mBuffer.size + 4)
            it.writeFully(mBuffer)
        }.build().readBytes()
    }

    private fun replaceField4_11_inPacket(packetWithLenPrefix: ByteArray, new11Value: String): ByteArray {
        if (packetWithLenPrefix.size <= 4) return packetWithLenPrefix

        val body = packetWithLenPrefix.copyOfRange(4, packetWithLenPrefix.size)
        val root = UnknownFieldSet.parseFrom(body)

        val rootBuilder = UnknownFieldSet.newBuilder(root)

        val innerBuilder = if (root.hasField(4)) {
            val existingInner = root.getUnknownObject(4)
            UnknownFieldSet.newBuilder(existingInner)
        } else {
            UnknownFieldSet.newBuilder()
        }

        innerBuilder.clearField(11)
        innerBuilder.addField(11,
            UnknownFieldSet.Field.newBuilder().also { f ->
                f.addLengthDelimited(ByteString.copyFromUtf8(new11Value))
            }.build()
        )

        rootBuilder.clearField(4)
        rootBuilder.addField(4,
            UnknownFieldSet.Field.newBuilder().also { f ->
                f.addLengthDelimited(innerBuilder.build().toByteString())
            }.build()
        )

        val newBody = rootBuilder.build().toByteArray()
        return newBody
    }

    override fun targetProcess(): Int {
        return SyncUtils.PROC_MSF
    }
}