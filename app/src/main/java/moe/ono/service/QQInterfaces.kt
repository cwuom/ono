package moe.ono.service

import cn.hutool.core.lang.reflect.MethodHandleUtil
import com.github.kyuubiran.ezxhelper.utils.isPublic
import com.github.kyuubiran.ezxhelper.utils.paramCount
import com.tencent.common.app.AppInterface
import com.tencent.qphone.base.remote.FromServiceMsg
import com.tencent.qphone.base.remote.ToServiceMsg
import moe.ono.hooks.base.util.Toasts
import moe.ono.reflex.getFields
import moe.ono.reflex.getMethods
import moe.ono.service.inject.ServletPool
import moe.ono.service.inject.ServletPool.getServlet
import moe.ono.service.inject.ServletPool.iServlet
import moe.ono.service.inject.servlets.IServlet
import moe.ono.util.FunProtoData
import moe.ono.util.FunProtoData.getUnpPackage
import moe.ono.util.Initiator.load
import moe.ono.util.Logger
import mqq.app.MobileQQ
import org.json.JSONObject
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

abstract class QQInterfaces {
    companion object {
        lateinit var mRealHandlerReq: Method
        lateinit var mHandlerResponse: Method
        lateinit var mqqService: Any

        val seqReceiveMap = ConcurrentHashMap<Int, FromServiceMsg>()
        private val seqFactory = AtomicInteger(0)

        private fun generateSeq(): Int {
            return seqFactory.addAndGet(1)
        }

        private fun getReceiveAndRemove(seq: Int): FromServiceMsg? {
            if (seqReceiveMap.containsKey(seq)) {
                val fromServiceMsg = seqReceiveMap[seq]
                seqReceiveMap.remove(seq)
                return fromServiceMsg
            }
            return null
        }

        var app = (if (PlatformUtils.isMqqPackage())
            MobileQQ.getMobileQQ().waitAppRuntime()
        else
            MobileQQ.getMobileQQ().waitAppRuntime(null)) as AppInterface

        private fun sendToServiceMsg(to: ToServiceMsg) {
            app.sendToService(to)
        }

        private fun createToServiceMsg(cmd: String): ToServiceMsg {
            return ToServiceMsg("mobileqq.service", app.currentAccountUin, cmd)
        }

        fun sendBuffer(
            cmd: String,
            isProto: Boolean,
            data: ByteArray,
        ) {
            val toServiceMsg = createToServiceMsg(cmd)
            toServiceMsg.putWupBuffer(data)
            toServiceMsg.addAttribute("req_pb_protocol_flag", isProto)
            sendToServiceMsg(toServiceMsg)
        }


        // FIXME: 部分情况下发包失败 ，但无法精准复现
        fun sendOidbSvcTrpcTcp(
            cmd: String,
            flag: Int,
            serviceType: Int,
            data: ByteArray,
        ): Int {
            val toServiceMsg = OidbUtil.makeOIDBPkg(
                cmd, flag, serviceType, data).apply {
                appSeq = generateSeq()
            }
            try {
                sendReq(toServiceMsg)
            } catch (e: Exception) {
                Logger.e(e)
                Toasts.show(app.getApp(), Toasts.TYPE_INFO, "未初始化，请稍后重试")
            }

            return toServiceMsg.appSeq
        }

        private fun sendReq(toServiceMsg: ToServiceMsg) {
            // qq <= 8.9.8
            toServiceMsg.extraData.putBoolean("req_pb_protocol_flag", true)
            // qq >= 8.9.10
            toServiceMsg.attributes["req_pb_protocol_flag"] = true
            if (mRealHandlerReq.parameterTypes.size == 2) {
                MethodHandleUtil.invokeSpecial<Unit>(mqqService, mRealHandlerReq, toServiceMsg, IServlet::class.java)
            } else {
                MethodHandleUtil.invokeSpecial<Unit>(mqqService, mRealHandlerReq, toServiceMsg, null, IServlet::class.java)
            }
        }

        private fun decodeResponse(toServiceMsg: ToServiceMsg, fromServiceMsg: FromServiceMsg) {
            MethodHandleUtil.invokeSpecial<Unit>(mqqService, mHandlerResponse, fromServiceMsg.isSuccess, toServiceMsg, fromServiceMsg, null)
        }

        fun receive(seq: Int): JSONObject? {
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 5_000) {
                Thread.sleep(120)
                val fromServiceMsg = getReceiveAndRemove(seq) ?: continue
                val toServiceMsg = fromServiceMsg.attributes[FromServiceMsg::class.java.simpleName] as ToServiceMsg
                decodeResponse(toServiceMsg, fromServiceMsg)
                val data = FunProtoData()
                data.fromBytes(
                    getUnpPackage(
                        fromServiceMsg.wupBuffer
                    )
                )

                return data.toJSON()
            }
            return null
        }

        fun update(){
            app = (if (PlatformUtils.isMqqPackage())
                MobileQQ.getMobileQQ().waitAppRuntime()
            else
                MobileQQ.getMobileQQ().waitAppRuntime(null)) as AppInterface

            val cBaseService = load("com.tencent.mobileqq.service.MobileQQServiceBase")!!
            cBaseService.getMethods(false).forEach {
                if (it.returnType == Void.TYPE && it.isPublic) {
                    val paramTypes = it.parameterTypes
                    if (paramTypes.size >= 2
                        && paramTypes.first() == ToServiceMsg::class.java
                        && paramTypes.last() == Class::class.java) {
                        mRealHandlerReq = it
                        Logger.d(it.toString())
                    }
                }
            }
            cBaseService.getMethods(false).forEach {
                if (it.returnType == Void.TYPE && it.isPublic && it.paramCount == 4) {
                    val paramTypes = it.parameterTypes
                    if (paramTypes[0] == Boolean::class.java
                        && paramTypes[1] == ToServiceMsg::class.java
                        && paramTypes[2] == FromServiceMsg::class.java) {
                        mHandlerResponse = it
                        Logger.d(it.toString())
                    }
                }
            }
            app.getFields(false).forEach {
                if (cBaseService.isAssignableFrom(it.type)) {
                    Logger.d(it.name)
                    it.isAccessible = true
                    mqqService = it.get(app)!!
                }
            }

            if (!this::mRealHandlerReq.isInitialized) {
                throw RuntimeException("初始化失败 -> mRealHandlerReq")
            }
            if (!this::mHandlerResponse.isInitialized) {
                throw RuntimeException("初始化失败 -> mHandlerResponse")
            }
            if (!this::mqqService.isInitialized) { // 9.2.70 修复
                app.getMethods(false).forEach {
                    if (it.returnType == cBaseService && it.isPublic
                        && it.name == "getMobileQQService" && it.paramCount == 0) {
                        Logger.d(it.toString())
                        it.isAccessible = true
                        mqqService = it.invoke(app)!!
                    }
                }
            }

        }
    }


}