package moe.ono.loader.hookapi

import com.tencent.qphone.base.remote.FromServiceMsg
import com.tencent.qphone.base.remote.ToServiceMsg
import org.json.JSONObject

interface IRespHandler {
    fun onHandle (
        data: JSONObject?,
        service: ToServiceMsg,
        fromServiceMsg: FromServiceMsg
    )

    val cmd: String
}