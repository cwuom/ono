package moe.ono.hooks.item.entertainment

import android.content.Intent
import com.tencent.qphone.base.remote.ToServiceMsg
import de.robv.android.xposed.XC_MethodHook
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks.base.api.QQMsfReqHandler
import moe.ono.hooks.protocol.sendPacket
import moe.ono.loader.hookapi.IMsfHandler
import moe.ono.util.FunProtoData
import org.json.JSONArray
import org.json.JSONObject

@HookItem(path = "娱乐功能/假群主", description = "仅供娱乐：随便发点消息，概率变成群主头衔\n* 管理员无效")
class FakeGroupOwner: BaseSwitchFunctionHookItem() {
    override fun entry(classLoader: ClassLoader) {
        QQMsfReqHandler.handler.add(object : IMsfHandler {
            override fun onHandle(
                param: XC_MethodHook.MethodHookParam,
                intent: Intent,
                toServiceMsg: ToServiceMsg
            ) {
                try {
                    if (!this@FakeGroupOwner.isEnabled) return

                    val pbData = FunProtoData.getUnpPackage(toServiceMsg.wupBuffer)
                    val json = FunProtoData().apply { fromBytes(pbData) }.toJSON()

                    json.optJSONObject("1")?.optJSONObject("2")?.opt("1") ?: return

                    val obj3_1 = json.optJSONObject("3")?.optJSONObject("1") ?: return
                    val obj2 = obj3_1.opt("2") ?: return

                    var isModified = false

                    fun createTargetNode() = JSONObject().apply {
                        put("37", JSONObject().apply {
                            put("19", JSONObject().apply { put("4", 300) })
                        })
                    }

                    fun updateExistingNode(targetNode: JSONObject) {
                        val inner37 = targetNode.getJSONObject("37")
                        val inner19 = inner37.optJSONObject("19") ?: JSONObject().also { inner37.put("19", it) }
                        inner19.put("4", 300)
                    }

                    if (obj2 is JSONObject) {
                        if (obj2.has("37")) {
                            updateExistingNode(obj2)
                            isModified = true
                        } else {
                            obj3_1.put("2", JSONArray().apply {
                                put(obj2)
                                put(createTargetNode())
                            })
                            isModified = true
                        }
                    } else if (obj2 is JSONArray) {
                        var hasNode37 = false
                        for (i in 0 until obj2.length()) {
                            val item = obj2.optJSONObject(i)
                            if (item != null && item.has("37")) {
                                updateExistingNode(item)
                                hasNode37 = true
                            }
                        }
                        if (!hasNode37) {
                            obj2.put(createTargetNode())
                        }
                        isModified = true
                    }

                    if (isModified) {
                        finish(param, json)
                    }
                } catch (_: Throwable) { }
            }

            private fun finish(param: XC_MethodHook.MethodHookParam, json: JSONObject) {
                param.result = null
                sendPacket("MessageSvc.PbSendMsg", json.toString())
            }

            override val cmd: String
                get() = "MessageSvc.PbSendMsg"
        })
    }
}