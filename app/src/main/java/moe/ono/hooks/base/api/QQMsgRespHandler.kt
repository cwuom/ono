package moe.ono.hooks.base.api

import com.tencent.qphone.base.remote.FromServiceMsg
import com.tencent.qphone.base.remote.ToServiceMsg
import com.tencent.qqnt.kernel.nativeinterface.PushExtraInfo
import de.robv.android.xposed.XposedHelpers.callMethod
import kotlinx.io.core.ByteReadPacket
import kotlinx.io.core.readBytes
import moe.ono.R
import moe.ono.bridge.ntapi.RelationNTUinAndUidApi.getUinFromUid
import moe.ono.common.CheckUtils
import moe.ono.config.CacheConfig
import moe.ono.config.CacheConfig.getIQQntWrapperSessionInstance
import moe.ono.config.CacheConfig.setRKeyGroup
import moe.ono.config.CacheConfig.setRKeyPrivate
import moe.ono.config.ConfigManager
import moe.ono.config.ONOConf
import moe.ono.constants.Constants
import moe.ono.creator.JsonViewerDialog
import moe.ono.creator.PacketHelperDialog
import moe.ono.creator.QQMessageFetcherResultDialog
import moe.ono.hooks._base.ApiHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks._core.factory.HookItemFactory.getItem
import moe.ono.hooks.base.util.Toasts
import moe.ono.hooks.item.chat.MessageEncryptor
import moe.ono.hooks.item.developer.QQPacketHelperC2CDisplayFixer
import moe.ono.hooks.item.entertainment.ModifyTextMessage
import moe.ono.hooks.protocol.buildMessage
import moe.ono.reflex.XField
import moe.ono.reflex.XMethod
import moe.ono.service.QQInterfaces
import moe.ono.service.inject.ServletPool.injectServlet
import moe.ono.ui.CommonContextWrapper
import moe.ono.util.AesUtils
import moe.ono.util.ContextUtils
import moe.ono.util.FunProtoData
import moe.ono.util.FunProtoData.getUnpPackage
import moe.ono.util.Logger
import moe.ono.util.QAppUtils
import moe.ono.util.SyncUtils
import moe.ono.util.Utils
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.Deflater

@HookItem(path = "API/QQMsgRespHandler")
class QQMsgRespHandler : ApiHookItem() {
    private fun update() {
        var pbSendCount = ONOConf.getInt("QQMsgRespHandler", "pbSendCount", 1000000)
        hookBefore(XMethod.clz("mqq.app.msghandle.MsgRespHandler").name("dispatchRespMsg").ignoreParam().get()) { param ->
            val serviceMsg: ToServiceMsg = XField.obj(param.args[1]).name("toServiceMsg").get()
            val fromServiceMsg: FromServiceMsg =
                XField.obj(param.args[1]).name("fromServiceMsg").get()

            val data = FunProtoData()
            data.fromBytes(
                getUnpPackage(
                    fromServiceMsg.wupBuffer
                )
            )

            val obj: JSONObject = data.toJSON()
            when (fromServiceMsg.serviceCmd) {
                "OidbSvcTrpcTcp.0x9067_202" -> {
                    Logger.d("on OidbSvcTrpcTcp.0x9067_202")
                    QQInterfaces.update()

                    val rkeyGroup =
                        obj.getJSONObject("4")
                            .getJSONObject("4")
                            .getJSONArray("1")
                            .getJSONObject(0).getString("1")

                    val rkeyPrivate =
                        obj.getJSONObject("4")
                            .getJSONObject("4")
                            .getJSONArray("1")
                            .getJSONObject(1).getString("1")

                    setRKeyGroup(rkeyGroup)
                    setRKeyPrivate(rkeyPrivate)
                }

                "trpc.qq_new_tech.status_svc.StatusService.UnRegister" -> {
                    Logger.d("on trpc.qq_new_tech.status_svc.StatusService.UnRegister")
                    QQInterfaces.update()
                    injectServlet()
                }

                "MessageSvc.PbSendMsg" -> {
                    Logger.d("on MessageSvc.PbSendMsg")
                    if (!getItem(QQPacketHelperC2CDisplayFixer::class.java).isEnabled) {
                        return@hookBefore
                    }
                    val index = CacheConfig.getPbSendMsgPacketIndex()
                    Logger.d("pb.index", index.toString())
                    if (index >= 0) {
                        val msgtime =
                            obj.getLong("3")
                        val seq =
                            obj.getLong("14")
                        val pbObj = CacheConfig.getPbSendMsgPacket(index)
                        val toUin = getUinFromUid(pbObj.peerid)
                        val toPeerid = pbObj.peerid
                        if (!CheckUtils.isInteger(toPeerid)) {
                            val uin = QAppUtils.getCurrentUin()
                            val uid = QAppUtils.UserUinToPeerID(uin)
                            val syncPacket1 = "{\n" +
                                    "  \"1\": {\n" +
                                    "    \"1\": {\n" +
                                    "      \"1\": $uin,\n" +
                                    "      \"2\": \"$uid\",\n" +
                                    "      \"5\": $uin,\n" +
                                    "      \"6\": \"$uid\"\n" +
                                    "    },\n" +
                                    "    \"2\": {\n" +
                                    "      \"1\": 528,\n" +
                                    "      \"2\": 8,\n" +
                                    "      \"3\": 8,\n" +
                                    "      \"4\": 0,\n" +
                                    "      \"5\": 0,\n" +
                                    "      \"6\": $msgtime,\n" +
                                    "      \"12\": 0" +
                                    "    },\n" +
                                    "    \"3\": {\n" +
                                    "      \"1\": {\n" +
                                    "        \n" +
                                    "      },\n" +
                                    "      \"2\": {\n" +
                                    "        \"1\": {\n" +
                                    "          \"1\": \"$toPeerid\",\n" +
                                    "          \"2\": $msgtime,\n" +
                                    "          \"20\": 0,\n" +
                                    "          \"21\": 0,\n" +
                                    "          \"9\": 0,\n" +
                                    "          \"11\": 0\n" +
                                    "        }\n" +
                                    "      }\n" +
                                    "    }\n" +
                                    "  }\n" +
                                    "}"
                            Logger.d("syncPacket1", syncPacket1)
                            callMethod(getIQQntWrapperSessionInstance(), "onMsfPush", "trpc.msg.olpush.OlPushService.MsgPush", buildMessage(syncPacket1), PushExtraInfo())

                            var syncPacket2 = "{\n" +
                                    "  \"1\": {\n" +
                                    "    \"1\": {\n" +
                                    "      \"1\": $uin,\n" +
                                    "      \"2\": \"$uid\",\n" +
                                    "      \"3\": 1001,\n" +
                                    "      \"5\": $toUin,\n" +
                                    "      \"6\": \"$toPeerid\"\n" +
                                    "    },\n" +
                                    "    \"2\": {\n" +
                                    "      \"1\": 166,\n" +
                                    "      \"3\": 11,\n" +
                                    "      \"4\": 0,\n" +
                                    "      \"5\": ${pbSendCount},\n" +
                                    "      \"6\": $msgtime,\n" +
                                    "      \"7\": 1,\n" +
                                    "      \"11\": $seq,\n" +
                                    "      \"28\": ${pbSendCount},\n" +
                                    "      \"12\": 0,\n" +
                                    "      \"14\": 0\n" +
                                    "    },\n" +
                                    "    \"3\": {\n" +
                                    "      \"1\": {\n" +
                                    "        \"1\": {\n" +
                                    "          \"1\": 0,\n" +
                                    "          \"2\": $msgtime,\n" +
                                    "          \"3\": 1490340800,\n" +
                                    "          \"4\": 0,\n" +
                                    "          \"5\": 10,\n" +
                                    "          \"6\": 0,\n" +
                                    "          \"7\": 134,\n" +
                                    "          \"8\": 2,\n" +
                                    "          \"9\": \"宋体\"\n" +
                                    "        },\n" +
                                    "        \"2\": [\n" +
                                    "          {\n" +
                                    "            \"37\": {\n" +
                                    "              \"17\": 105342,\n" +
                                    "              \"1\": 10896,\n" +
                                    "              \"19\": {\n" +
                                    "                \"96\": 0,\n" +
                                    "                \"34\": 0,\n" +
                                    "                \"102\": {\n" +
                                    "                  \"1\": {\n" +
                                    "                    \"1\": 0,\n" +
                                    "                    \"2\": 0,\n" +
                                    "                    \"3\": 0,\n" +
                                    "                    \"4\": 0\n" +
                                    "                  }\n" +
                                    "                },\n" +
                                    "                \"73\": {\n" +
                                    "                  \"2\": 0,\n" +
                                    "                  \"6\": 6\n" +
                                    "                },\n" +
                                    "                \"25\": 0,\n" +
                                    "                \"90\": {\n" +
                                    "                  \"1\": $seq,\n" +
                                    "                  \"2\": 0\n" +
                                    "                },\n" +
                                    "                \"30\": 0,\n" +
                                    "                \"31\": 0,\n" +
                                    "                \"15\": 65536\n" +
                                    "              }\n" +
                                    "            }\n" +
                                    "          },\n" +
                                    "          {\n" +
                                    "            \"9\": {\n" +
                                    "              \"1\": 2021111,\n" +
                                    "              \"12\": 65536\n" +
                                    "            }\n" +
                                    "          }\n" +
                                    "        ]\n" +
                                    "      }\n" +
                                    "    }\n" +
                                    "  },\n" + " " +
                                    " \"3\": 1,\n" +
                                    "  \"4\": {\n" +
                                    "    \"1\": \"0.0.0.0\",\n" +
                                    "    \"2\": 20222,\n" +
                                    "    \"3\": {\n" +
                                    "      \"2\": 166,\n" +
                                    "      \"3\": 11600,\n" +
                                    "      \"4\": 0,\n" +
                                    "      \"7\": 1,\n" +
                                    "      \"8\": $uin\n" +
                                    "    }\n" +
                                    "  }\n" +
                                    "}"


                            val originalJson = JSONObject(syncPacket2)

                            val raw = pbObj.content.trimStart()
                            val appendContent: Any = if (raw.startsWith("[")) {
                                JSONArray(raw)
                            } else {
                                JSONObject(raw)
                            }

                            appendToContentArray(originalJson, appendContent)
                            syncPacket2 = originalJson.toString(4)

                            Logger.d("syncPacket2", syncPacket2)
                            callMethod(getIQQntWrapperSessionInstance(), "onMsfPush", "trpc.msg.olpush.OlPushService.MsgPush", buildMessage(syncPacket2), PushExtraInfo())
                            CacheConfig.removeLastPbSendMsgPacket()
                            pbSendCount++
                            ONOConf.setInt("QQMsgRespHandler", "pbSendCount", pbSendCount)
                        }

                    }
                }

                "trpc.qq_new_tech.status_svc.StatusService.UnRegister" -> {
                    Logger.d("on trpc.qq_new_tech.status_svc.StatusService.UnRegister")
                    QQInterfaces.update()
                    injectServlet()
                }

//                "OidbSvcTrpcTcp.0xfe1_2" -> {
//                    Logger.d("on OidbSvcTrpcTcp.0xfe1_2")
////                    Logger.i(obj.toString())
//                }
                "MessageSvc.PbGetGroupMsg" -> {
                    Logger.d("on MessageSvc.PbGetGroupMsg")

                    Logger.d("obj: " + obj.toString(4))

                    if (MessageEncryptor.decryptMsg) {
                        MessageEncryptor.decryptMsg = false
                        val key = "${MessageEncryptor.peerUid}:${MessageEncryptor.msgSeq}:${MessageEncryptor.senderUin}"
                        MessageEncryptor.peerUid = ""
                        MessageEncryptor.senderUin = ""
                        MessageEncryptor.msgSeq = ""

                        val encryptKey = ConfigManager.dGetString(
                            Constants.PrekCfgXXX + getItem(MessageEncryptor::class.java).path,
                            "ono"
                        )
                        if (encryptKey.isBlank()) {
                            return@hookBefore
                        }

                        val aesKey = AesUtils.md5(encryptKey)
                        val encryptMsg = obj.optJSONObject("6")?.optJSONObject("3")?.optJSONObject("1")
                            ?.optJSONArray("2")?.optJSONObject(2)?.optJSONObject("1")?.optJSONObject("12")
                            ?.optString("1")

                        if (encryptMsg == null) {
                            Toasts.error(ContextUtils.getCurrentActivity(), "非加密消息")
                            return@hookBefore
                        }

                        val hexStr = encryptMsg.replace("hex->", "")
                        val byteArray = MessageEncryptor.hexToBytes(hexStr)
                        val encryptBuffer = ByteReadPacket(byteArray)

                        if (0x114514 != encryptBuffer.readInt()) {
                            Toasts.error(ContextUtils.getCurrentActivity(), "非加密消息")
                            return@hookBefore
                        }

                        if (encryptKey.hashCode() != encryptBuffer.readInt()) {
                            Toasts.error(ContextUtils.getCurrentActivity(), "密钥不匹配")
                            return@hookBefore
                        }

                        val decryptMsg = AesUtils.aesDecrypt(encryptBuffer.readBytes(), aesKey)
                        val decryptData = FunProtoData()
                        decryptData.fromBytes(getUnpPackage(decryptMsg))

                        val decryptMsgData = decryptData.toJSON().optJSONObject("1")
                            ?.optJSONArray("2")
                            ?.optJSONObject(2)
                            ?.optJSONObject("1")
                            ?.optString("1")

                        if (decryptMsgData != null) {
                            ModifyTextMessage.modifyMap.put(key, decryptMsgData)
                            Toasts.success(ContextUtils.getCurrentActivity(), "解密成功重新进入本界面生效")
                            return@hookBefore
                        }

                        SyncUtils.runOnUiThread {
                            JsonViewerDialog.createView(
                                CommonContextWrapper.createAppCompatContext(ContextUtils.getCurrentActivity()),
                                decryptData.toJSON()
                            )

                            Toasts.success(ContextUtils.getCurrentActivity(), "不支持的消息类型, 已打开原 PB")
                        }

                        return@hookBefore
                    }

                    SyncUtils.runOnUiThread { QQMessageFetcherResultDialog.createView(
                        CommonContextWrapper.createAppCompatContext(ContextUtils.getCurrentActivity()), obj) }

                }
                "MessageSvc.PbGetOneDayRoamMsg" -> {
                    Logger.d("on MessageSvc.PbGetOneDayRoamMsg")

                    Logger.d("obj: " + obj.toString(4))
                    SyncUtils.runOnUiThread { QQMessageFetcherResultDialog.createView(
                        CommonContextWrapper.createAppCompatContext(ContextUtils.getCurrentActivity()), obj) }
                }

                "trpc.group.long_msg_interface.MsgService.SsoSendLongMsg" -> {
                    Logger.d("on trpc.group.long_msg_interface.MsgService.SsoSendLongMsg")
                    Logger.d("obj: " + obj.toString(4))

                    val resid = obj.getJSONObject("2").getString("3")

                    Logger.d("resid", resid)
                    try {
                        if (PacketHelperDialog.mRgSendBy.checkedRadioButtonId == R.id.rb_send_by_longmsg){
                            val content = "{\n" +
                                    "    \"37\": {\n" +
                                    "        \"6\": 1,\n" +
                                    "        \"7\": \"$resid\",\n" +
                                    "        \"17\": 0,\n" +
                                    "        \"19\": {\n" +
                                    "            \"15\": 0,\n" +
                                    "            \"31\": 0,\n" +
                                    "            \"41\": 0\n" +
                                    "        }\n" +
                                    "    }\n" +
                                    "}"

                            PacketHelperDialog.setContent(content, true)
                        } else if (PacketHelperDialog.mRgSendBy.checkedRadioButtonId == R.id.rb_send_by_forwarding) {
                            if (!PacketHelperDialog.mRbXmlForward.isChecked) {
                                val json = "{\n" +
                                        "  \"app\": \"com.tencent.multimsg\",\n" +
                                        "  \"config\": {\n" +
                                        "    \"autosize\": 1,\n" +
                                        "    \"forward\": 1,\n" +
                                        "    \"round\": 1,\n" +
                                        "    \"type\": \"normal\",\n" +
                                        "    \"width\": 300\n" +
                                        "  },\n" +
                                        "  \"desc\": \"${PacketHelperDialog.etHint.text}\",\n" +
                                        "  \"extra\": \"{\\\"filename\\\":\\\"${UUID.randomUUID()}\\\",\\\"tsum\\\":1}\\n\",\n" +
                                        "  \"meta\": {\n" +
                                        "    \"detail\": {\n" +
                                        "      \"news\": [\n" +
                                        "        {\n" +
                                        "          \"text\": \"${PacketHelperDialog.etDesc.text}\"\n" +
                                        "        }\n" +
                                        "      ],\n" +
                                        "      \"resid\": \"$resid\",\n" +
                                        "      \"source\": \"聊天记录\",\n" +
                                        "      \"summary\": \"PacketHelper@ouom_pub\",\n" +
                                        "      \"uniseq\": \"${UUID.randomUUID()}\"\n" +
                                        "    }\n" +
                                        "  },\n" +
                                        "  \"prompt\": \"${PacketHelperDialog.etHint.text}\",\n" +
                                        "  \"ver\": \"0.0.0.5\",\n" +
                                        "  \"view\": \"contact\"\n" +
                                        "}"

                                Logger.d(json)
                                val content = "{\n" +
                                        "    \"51\": {\n" +
                                        "        \"1\": \"hex->${Utils.bytesToHex(compressData(json))}\"\n" +
                                        "    }\n" +
                                        "}"

                                PacketHelperDialog.setContent(content, false)
                            } else {
                                val xml = """<?xml version="1.0" encoding="utf-8"?><msg brief="${PacketHelperDialog.etDesc.text}" m_fileName="${UUID.randomUUID()}" action="viewMultiMsg" tSum="1" flag="3" m_resid="$resid" serviceID="35" m_fileSize="0"><item layout="1"><title color="#000000" size="34">聊天记录</title><title color="#777777" size="26">${PacketHelperDialog.etDesc.text}</title><hr></hr><summary color="#808080" size="26">PacketHelper@ouom_pub</summary></item><source name="@ouom_pub"></source></msg>"""
                                Logger.d("xml", xml)

                                val json = """{
    "12": {
        "1": "hex->${Utils.bytesToHex(compressData(xml))}",
        "2": 60
    }
}""".trim()

                                Logger.d(json)

                                PacketHelperDialog.setContentForLongmsg(json)
                            }

                        }

                    } catch (e: Exception) {
                        Logger.e("QQMsgRespHandler", e)
                    }


                }
            }
        }

    }

    companion object {
        fun compressData(data: String): ByteArray {
            val inputBytes = data.toByteArray(Charsets.UTF_8)
            val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, false)
            deflater.setInput(inputBytes)
            deflater.finish()

            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                outputStream.write(buffer, 0, count)
            }
            deflater.end()
            val compressedBytes = outputStream.toByteArray()
            val result = ByteArray(compressedBytes.size + 1)
            result[0] = 0x01
            System.arraycopy(compressedBytes, 0, result, 1, compressedBytes.size)

            return result
        }
    }

    fun appendToContentArray(original: JSONObject, newContent: Any) {
        val contentArray = original
            .optJSONObject("1")
            ?.optJSONObject("3")
            ?.optJSONObject("1")
            ?.optJSONArray("2") ?: JSONArray().also {
            original.getJSONObject("1").getJSONObject("3").getJSONObject("1").put("2", it)
        }

        when (newContent) {
            is JSONObject -> contentArray.put(newContent)
            is JSONArray -> {
                for (i in 0 until newContent.length()) {
                    contentArray.put(newContent.getJSONObject(i))
                }
            }
            else -> throw IllegalArgumentException("Unsupported type for content")
        }
    }




    @Throws(Throwable::class)
    override fun entry(classLoader: ClassLoader) {
        update()
    }
}