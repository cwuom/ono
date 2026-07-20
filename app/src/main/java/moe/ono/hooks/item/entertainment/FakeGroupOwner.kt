package moe.ono.hooks.item.entertainment

import android.content.Intent
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.UnknownFieldSet
import com.tencent.qphone.base.remote.ToServiceMsg
import de.robv.android.xposed.XC_MethodHook
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks.base.api.QQMsfReqHandler
import moe.ono.loader.hookapi.IMsfHandler
import moe.ono.util.Logger
import java.nio.ByteBuffer

@HookItem(path = "娱乐功能/假群主", description = "* 随便发点消息，概率变成群主头衔\n* 管理员无效")
class FakeGroupOwner : BaseSwitchFunctionHookItem() {
    override fun entry(classLoader: ClassLoader) {
        QQMsfReqHandler.handler.add(object : IMsfHandler {
            override fun onHandle(
                param: XC_MethodHook.MethodHookParam,
                intent: Intent,
                toServiceMsg: ToServiceMsg
            ) {
                if (!this@FakeGroupOwner.isEnabled) return

                try {
                    val original = toServiceMsg.wupBuffer ?: return
                    val patched = FakeGroupOwnerProto.patchWupBuffer(original) ?: return
                    toServiceMsg.putWupBuffer(patched)
                } catch (t: Throwable) {
                    Logger.e("[FakeGroupOwner] Failed to patch MessageSvc.PbSendMsg", t)
                }
            }

            override val cmd: String
                get() = "MessageSvc.PbSendMsg"
        })
    }
}

internal object FakeGroupOwnerProto {
    private const val LENGTH_HEADER_SIZE = 4
    private const val OWNER_TITLE_VALUE = 300L

    fun patchWupBuffer(buffer: ByteArray): ByteArray? {
        val hasLengthHeader = hasLengthHeader(buffer)
        val payload = if (hasLengthHeader) {
            buffer.copyOfRange(LENGTH_HEADER_SIZE, buffer.size)
        } else {
            buffer
        }
        val patchedPayload = patchPayload(payload) ?: return null

        return if (hasLengthHeader) addLengthHeader(patchedPayload) else patchedPayload
    }

    fun patchPayload(payload: ByteArray): ByteArray? {
        val request = parse(payload) ?: return null
        if (!isGroupMessage(request)) return null

        return when (
            val result = patchMessageField(request, 3, transform = ::patchMessageBody)
        ) {
            is PatchResult.Modified -> result.value.toByteArray()
            PatchResult.Malformed,
            PatchResult.Unchanged -> null
        }
    }

    private fun isGroupMessage(request: UnknownFieldSet): Boolean {
        return request.getField(1).lengthDelimitedList.any { routingBytes ->
            val routingHead = parse(routingBytes) ?: return@any false
            routingHead.getField(2).lengthDelimitedList.any { groupBytes ->
                parse(groupBytes)?.getField(1)?.varintList?.isNotEmpty() == true
            }
        }
    }

    private fun patchMessageBody(messageBody: UnknownFieldSet): PatchResult {
        return patchMessageField(messageBody, 1, transform = ::patchRichText)
    }

    private fun patchRichText(richText: UnknownFieldSet): PatchResult {
        val elementsField = richText.getField(2)
        if (elementsField.lengthDelimitedList.isEmpty()) return PatchResult.Unchanged

        var foundOwnerElement = false
        val elements = ArrayList<ByteString>(elementsField.lengthDelimitedList.size + 1)

        for (elementBytes in elementsField.lengthDelimitedList) {
            val element = parse(elementBytes) ?: return PatchResult.Malformed
            if (!element.hasField(37)) {
                elements.add(elementBytes)
                continue
            }

            foundOwnerElement = true
            when (val result = patchOwnerElement(element)) {
                is PatchResult.Modified -> elements.add(result.value.toByteString())
                PatchResult.Malformed,
                PatchResult.Unchanged -> return PatchResult.Malformed
            }
        }

        if (!foundOwnerElement) {
            elements.add(createOwnerElement().toByteString())
        }

        return PatchResult.Modified(replaceLengthDelimited(richText, 2, elements))
    }

    private fun patchOwnerElement(element: UnknownFieldSet): PatchResult {
        return patchMessageField(
            element,
            37,
            requireValues = true,
            transform = ::patchOwnerInfo
        )
    }

    private fun patchOwnerInfo(ownerInfo: UnknownFieldSet): PatchResult {
        if (!ownerInfo.hasField(19)) {
            return PatchResult.Modified(
                replaceLengthDelimited(
                    ownerInfo,
                    19,
                    listOf(setOwnerTitle(UnknownFieldSet.getDefaultInstance()).toByteString())
                )
            )
        }

        return patchMessageField(ownerInfo, 19, requireValues = true) { titleInfo ->
            PatchResult.Modified(setOwnerTitle(titleInfo))
        }
    }

    private fun setOwnerTitle(titleInfo: UnknownFieldSet): UnknownFieldSet {
        val oldField = titleInfo.getField(4)
        val newField = UnknownFieldSet.Field.newBuilder().apply {
            addVarint(OWNER_TITLE_VALUE)
            oldField.fixed32List.forEach(::addFixed32)
            oldField.fixed64List.forEach(::addFixed64)
            oldField.lengthDelimitedList.forEach(::addLengthDelimited)
            oldField.groupList.forEach(::addGroup)
        }.build()

        return UnknownFieldSet.newBuilder(titleInfo)
            .clearField(4)
            .addField(4, newField)
            .build()
    }

    private fun createOwnerElement(): UnknownFieldSet {
        val ownerInfo = UnknownFieldSet.newBuilder()
            .addField(
                19,
                lengthDelimitedField(setOwnerTitle(UnknownFieldSet.getDefaultInstance()))
            )
            .build()

        return UnknownFieldSet.newBuilder()
            .addField(37, lengthDelimitedField(ownerInfo))
            .build()
    }

    private fun patchMessageField(
        parent: UnknownFieldSet,
        fieldNumber: Int,
        requireValues: Boolean = false,
        transform: (UnknownFieldSet) -> PatchResult
    ): PatchResult {
        val field = parent.getField(fieldNumber)
        if (field.lengthDelimitedList.isEmpty()) {
            return if (requireValues) PatchResult.Malformed else PatchResult.Unchanged
        }

        var modified = false
        val values = ArrayList<ByteString>(field.lengthDelimitedList.size)
        for (bytes in field.lengthDelimitedList) {
            val child = parse(bytes) ?: return PatchResult.Malformed
            when (val result = transform(child)) {
                is PatchResult.Modified -> {
                    values.add(result.value.toByteString())
                    modified = true
                }
                PatchResult.Malformed -> return PatchResult.Malformed
                PatchResult.Unchanged -> values.add(bytes)
            }
        }

        return if (modified) {
            PatchResult.Modified(replaceLengthDelimited(parent, fieldNumber, values))
        } else {
            PatchResult.Unchanged
        }
    }

    private fun replaceLengthDelimited(
        parent: UnknownFieldSet,
        fieldNumber: Int,
        values: List<ByteString>
    ): UnknownFieldSet {
        val oldField = parent.getField(fieldNumber)
        val newField = UnknownFieldSet.Field.newBuilder().apply {
            oldField.varintList.forEach(::addVarint)
            oldField.fixed32List.forEach(::addFixed32)
            oldField.fixed64List.forEach(::addFixed64)
            values.forEach(::addLengthDelimited)
            oldField.groupList.forEach(::addGroup)
        }.build()

        return UnknownFieldSet.newBuilder(parent)
            .clearField(fieldNumber)
            .addField(fieldNumber, newField)
            .build()
    }

    private fun lengthDelimitedField(value: UnknownFieldSet): UnknownFieldSet.Field {
        return UnknownFieldSet.Field.newBuilder()
            .addLengthDelimited(value.toByteString())
            .build()
    }

    private fun hasLengthHeader(buffer: ByteArray): Boolean {
        if (buffer.size < LENGTH_HEADER_SIZE) return false
        return ByteBuffer.wrap(buffer, 0, LENGTH_HEADER_SIZE).int == buffer.size
    }

    private fun addLengthHeader(payload: ByteArray): ByteArray {
        val size = payload.size + LENGTH_HEADER_SIZE
        return ByteBuffer.allocate(size).putInt(size).put(payload).array()
    }

    private fun parse(bytes: ByteArray): UnknownFieldSet? {
        return try {
            UnknownFieldSet.parseFrom(bytes)
        } catch (_: InvalidProtocolBufferException) {
            null
        }
    }

    private fun parse(bytes: ByteString): UnknownFieldSet? {
        return try {
            UnknownFieldSet.parseFrom(bytes)
        } catch (_: InvalidProtocolBufferException) {
            null
        }
    }

    private sealed interface PatchResult {
        data object Unchanged : PatchResult
        data object Malformed : PatchResult
        data class Modified(val value: UnknownFieldSet) : PatchResult
    }
}
