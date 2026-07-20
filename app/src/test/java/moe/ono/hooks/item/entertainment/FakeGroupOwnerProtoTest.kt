package moe.ono.hooks.item.entertainment

import com.google.protobuf.ByteString
import com.google.protobuf.UnknownFieldSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer

class FakeGroupOwnerProtoTest {
    private val opaque = ByteString.copyFrom(byteArrayOf(0, -1, 8, -128, 127))
    private val protobufShapedOpaqueElement = ByteString.copyFrom(
        byteArrayOf(0xd8.toByte(), 0x05, 0x81.toByte(), 0x00)
    )

    @Test
    fun appendsOwnerElementWithoutChangingExistingPayloads() {
        assertNotEquals(
            protobufShapedOpaqueElement,
            parse(protobufShapedOpaqueElement).toByteString()
        )
        val originalElements = listOf("ui。", "95", "9号还是10号?", "🙂")
            .map { textElement(it).toByteString() } + listOf(protobufShapedOpaqueElement)
        val before = packet(isGroup = true, elements = originalElements)

        val after = parse(requireNotNull(FakeGroupOwnerProto.patchPayload(before.toByteArray())))
        val oldElements = elementBytes(before)
        val newElements = elementBytes(after)

        assertTargetPathSiblingsPreserved(before, after)
        assertEquals(oldElements.size + 1, newElements.size)
        assertEquals(oldElements, newElements.take(oldElements.size))
        val appended = parse(newElements.last())
        assertCanonicalOwnerElement(appended)
    }

    @Test
    fun updatesEveryExistingOwnerValueAndPreservesMixedWireTypes() {
        val before = packet(
            isGroup = true,
            elements = listOf(
                textElement("95").toByteString(),
                ownerElement(listOf(listOf(95, 96), listOf(97, 98))).toByteString(),
                textElement("ui。").toByteString()
            )
        )
        val after = parse(requireNotNull(FakeGroupOwnerProto.patchPayload(before.toByteArray())))
        val oldElements = elementBytes(before)
        val newElements = elementBytes(after)

        assertEquals(oldElements.size, newElements.size)
        assertEquals(oldElements[0], newElements[0])
        assertEquals(oldElements[2], newElements[2])

        val oldOwner = parse(oldElements[1])
        val newOwner = parse(newElements[1])
        assertExcept(oldOwner, newOwner, 37)
        assertNonLengthDelimitedPreserved(oldOwner.getField(37), newOwner.getField(37))

        val oldOwnerInfos = children(oldOwner, 37)
        val newOwnerInfos = children(newOwner, 37)
        assertEquals(oldOwnerInfos.size, newOwnerInfos.size)
        oldOwnerInfos.zip(newOwnerInfos).forEach { (oldInfo, newInfo) ->
            assertExcept(oldInfo, newInfo, 19)
            assertNonLengthDelimitedPreserved(oldInfo.getField(19), newInfo.getField(19))

            val oldTitles = children(oldInfo, 19)
            val newTitles = children(newInfo, 19)
            assertEquals(oldTitles.size, newTitles.size)
            oldTitles.zip(newTitles).forEach { (oldTitle, newTitle) ->
                assertExcept(oldTitle, newTitle, 4)
                assertEquals(listOf(300L), newTitle.getField(4).varintList)
                assertNonVarintPreserved(oldTitle.getField(4), newTitle.getField(4))
            }
        }
    }

    @Test
    fun addsMissingField19WithoutAppendingAnotherElement() {
        val ownerInfo = message(20 to bytes(opaque))
        val element = message(
            37 to mixedLengthDelimited(listOf(ownerInfo.toByteString())),
            80 to bytes(opaque)
        )
        val before = packet(isGroup = true, elements = listOf(element.toByteString()))

        val after = parse(requireNotNull(FakeGroupOwnerProto.patchPayload(before.toByteArray())))
        val oldElement = parse(elementBytes(before).single())
        val newElement = parse(elementBytes(after).single())

        assertExcept(oldElement, newElement, 37)
        assertNonLengthDelimitedPreserved(oldElement.getField(37), newElement.getField(37))
        val oldOwnerInfo = child(oldElement, 37)
        val newOwnerInfo = child(newElement, 37)
        assertExcept(oldOwnerInfo, newOwnerInfo, 19)
        assertOwnerValue(newElement)
    }

    @Test
    fun patchesEveryRepeatedMessageBodyAndRichText() {
        val before = packetWithBodies(
            isGroup = true,
            bodies = listOf(
                messageBody(
                    listOf(
                        richText(listOf(textElement("ui。").toByteString())),
                        richText(listOf(textElement("95").toByteString()))
                    )
                ),
                messageBody(
                    listOf(
                        richText(listOf(textElement("9号还是10号?").toByteString()))
                    )
                )
            )
        )

        val after = parse(requireNotNull(FakeGroupOwnerProto.patchPayload(before.toByteArray())))
        assertExcept(before, after, 3)
        assertNonLengthDelimitedPreserved(before.getField(3), after.getField(3))
        val oldBodies = children(before, 3)
        val newBodies = children(after, 3)

        assertEquals(oldBodies.size, newBodies.size)
        oldBodies.zip(newBodies).forEach { (oldBody, newBody) ->
            assertExcept(oldBody, newBody, 1)
            assertNonLengthDelimitedPreserved(oldBody.getField(1), newBody.getField(1))
            val oldRichTexts = children(oldBody, 1)
            val newRichTexts = children(newBody, 1)
            assertEquals(oldRichTexts.size, newRichTexts.size)

            oldRichTexts.zip(newRichTexts).forEach { (oldRichText, newRichText) ->
                assertExcept(oldRichText, newRichText, 2)
                assertNonLengthDelimitedPreserved(
                    oldRichText.getField(2),
                    newRichText.getField(2)
                )
                val oldElements = oldRichText.getField(2).lengthDelimitedList
                val newElements = newRichText.getField(2).lengthDelimitedList
                assertEquals(oldElements.size + 1, newElements.size)
                assertEquals(oldElements, newElements.take(oldElements.size))
                assertCanonicalOwnerElement(parse(newElements.last()))
            }
        }
    }

    @Test
    fun rejectsMalformedOwnerPathsWithoutPartiallyChangingPacket() {
        val invalidMessage = ByteString.copyFrom(byteArrayOf(0))
        val malformed37 = message(37 to bytes(invalidMessage))
        val malformed19 = message(
            37 to objectField(message(19 to bytes(invalidMessage)))
        )
        val wrongWire37 = message(37 to varints(1))
        val wrongWire19 = message(
            37 to objectField(message(19 to varints(1)))
        )
        val validOwnerInfo = message(19 to objectField(ownerTitle(95)))
        val repeatedMalformed37 = message(
            37 to mixedLengthDelimited(listOf(validOwnerInfo.toByteString(), invalidMessage))
        )
        val repeatedMalformed19 = message(
            37 to objectField(
                message(
                    19 to mixedLengthDelimited(
                        listOf(ownerTitle(95).toByteString(), invalidMessage)
                    )
                )
            )
        )

        listOf(
            malformed37,
            malformed19,
            wrongWire37,
            wrongWire19,
            repeatedMalformed37,
            repeatedMalformed19
        ).forEach { element ->
            val payload = packet(true, listOf(element.toByteString())).toByteArray()
            assertNull(FakeGroupOwnerProto.patchPayload(payload))
        }

        val malformedElement = packet(true, listOf(invalidMessage)).toByteArray()
        assertNull(FakeGroupOwnerProto.patchPayload(malformedElement))
    }

    @Test
    fun stripsFourByteLengthHeaderForResigning() {
        val payload = packet(
            isGroup = true,
            elements = listOf(textElement("ui。").toByteString())
        ).toByteArray()
        val framed = addLengthHeader(payload)

        val patched = requireNotNull(FakeGroupOwnerProto.patchWupBuffer(framed))

        val after = parse(patched)
        assertCanonicalOwnerElement(parse(elementBytes(after).last()))
    }

    @Test
    fun acceptsUnframedPayloadAndIgnoresPrivateMessages() {
        val groupPayload = packet(
            isGroup = true,
            elements = listOf(textElement("🙂").toByteString())
        ).toByteArray()
        val privatePayload = packet(
            isGroup = false,
            elements = listOf(textElement("ui。").toByteString())
        ).toByteArray()

        val patched = requireNotNull(FakeGroupOwnerProto.patchWupBuffer(groupPayload))

        assertCanonicalOwnerElement(parse(elementBytes(parse(patched)).last()))
        assertNull(FakeGroupOwnerProto.patchWupBuffer(addLengthHeader(privatePayload)))
    }

    private fun packet(isGroup: Boolean, elements: List<ByteString>): UnknownFieldSet {
        return packetWithBodies(isGroup, listOf(messageBody(listOf(richText(elements)))))
    }

    private fun packetWithBodies(
        isGroup: Boolean,
        bodies: List<UnknownFieldSet>
    ): UnknownFieldSet {
        val routingHead = if (isGroup) {
            message(2 to objectField(message(1 to varints(123456))))
        } else {
            message(1 to objectField(message(2 to bytes(ByteString.copyFromUtf8("peer")))))
        }

        return message(
            1 to objectField(routingHead),
            3 to mixedLengthDelimited(bodies.map(UnknownFieldSet::toByteString)),
            72 to bytes(opaque),
            73 to UnknownFieldSet.Field.newBuilder().addFixed32(0x12345678).build()
        )
    }

    private fun messageBody(richTexts: List<UnknownFieldSet>): UnknownFieldSet {
        return message(
            1 to mixedLengthDelimited(richTexts.map(UnknownFieldSet::toByteString)),
            71 to bytes(ByteString.copyFromUtf8("ui。"))
        )
    }

    private fun richText(elements: List<ByteString>): UnknownFieldSet {
        return message(
            2 to mixedLengthDelimited(elements),
            70 to bytes(opaque)
        )
    }

    private fun textElement(text: String): UnknownFieldSet {
        return message(
            1 to objectField(message(1 to bytes(ByteString.copyFromUtf8(text)))),
            90 to bytes(opaque)
        )
    }

    private fun ownerElement(ownerValues: List<List<Long>>): UnknownFieldSet {
        val ownerInfos = ownerValues.map { values ->
            message(
                19 to mixedLengthDelimited(values.map { ownerTitle(it).toByteString() }),
                20 to bytes(opaque)
            )
        }
        return message(
            37 to mixedLengthDelimited(ownerInfos.map(UnknownFieldSet::toByteString)),
            80 to bytes(opaque)
        )
    }

    private fun ownerTitle(value: Long): UnknownFieldSet {
        return message(
            4 to mixedVarintField(value, value + 1),
            9 to bytes(ByteString.copyFromUtf8("ui。")),
            10 to bytes(opaque)
        )
    }

    private fun elementBytes(root: UnknownFieldSet): List<ByteString> {
        return child(child(root, 3), 1).getField(2).lengthDelimitedList
    }

    private fun assertTargetPathSiblingsPreserved(
        before: UnknownFieldSet,
        after: UnknownFieldSet
    ) {
        assertExcept(before, after, 3)
        assertNonLengthDelimitedPreserved(before.getField(3), after.getField(3))

        val beforeBody = child(before, 3)
        val afterBody = child(after, 3)
        assertExcept(beforeBody, afterBody, 1)
        assertNonLengthDelimitedPreserved(beforeBody.getField(1), afterBody.getField(1))

        val beforeRichText = child(beforeBody, 1)
        val afterRichText = child(afterBody, 1)
        assertExcept(beforeRichText, afterRichText, 2)
        assertNonLengthDelimitedPreserved(
            beforeRichText.getField(2),
            afterRichText.getField(2)
        )
    }

    private fun assertCanonicalOwnerElement(value: UnknownFieldSet) {
        assertEquals(setOf(37), value.asMap().keys)
        val ownerInfo = child(value, 37)
        assertEquals(setOf(19), ownerInfo.asMap().keys)
        val title = child(ownerInfo, 19)
        assertEquals(setOf(4), title.asMap().keys)
        assertEquals(varints(300), title.getField(4))
    }

    private fun assertOwnerValue(value: UnknownFieldSet) {
        val ownerInfos = children(value, 37)
        assertEquals(1, ownerInfos.size)
        val titles = children(ownerInfos.single(), 19)
        assertEquals(1, titles.size)
        assertEquals(listOf(300L), titles.single().getField(4).varintList)
    }

    private fun assertExcept(
        before: UnknownFieldSet,
        after: UnknownFieldSet,
        fieldNumber: Int
    ) {
        assertEquals(
            before.asMap().filterKeys { it != fieldNumber },
            after.asMap().filterKeys { it != fieldNumber }
        )
    }

    private fun assertNonLengthDelimitedPreserved(
        before: UnknownFieldSet.Field,
        after: UnknownFieldSet.Field
    ) {
        assertEquals(before.varintList, after.varintList)
        assertEquals(before.fixed32List, after.fixed32List)
        assertEquals(before.fixed64List, after.fixed64List)
        assertEquals(before.groupList, after.groupList)
    }

    private fun assertNonVarintPreserved(
        before: UnknownFieldSet.Field,
        after: UnknownFieldSet.Field
    ) {
        assertEquals(before.fixed32List, after.fixed32List)
        assertEquals(before.fixed64List, after.fixed64List)
        assertEquals(before.lengthDelimitedList, after.lengthDelimitedList)
        assertEquals(before.groupList, after.groupList)
    }

    private fun child(parent: UnknownFieldSet, number: Int): UnknownFieldSet {
        return children(parent, number).single()
    }

    private fun children(parent: UnknownFieldSet, number: Int): List<UnknownFieldSet> {
        return parent.getField(number).lengthDelimitedList.map(::parse)
    }

    private fun parse(bytes: ByteArray): UnknownFieldSet = UnknownFieldSet.parseFrom(bytes)

    private fun parse(bytes: ByteString): UnknownFieldSet = UnknownFieldSet.parseFrom(bytes)

    private fun message(vararg fields: Pair<Int, UnknownFieldSet.Field>): UnknownFieldSet {
        return UnknownFieldSet.newBuilder().apply {
            fields.forEach { (number, field) -> addField(number, field) }
        }.build()
    }

    private fun objectField(value: UnknownFieldSet): UnknownFieldSet.Field {
        return UnknownFieldSet.Field.newBuilder()
            .addLengthDelimited(value.toByteString())
            .build()
    }

    private fun bytes(value: ByteString): UnknownFieldSet.Field {
        return UnknownFieldSet.Field.newBuilder().addLengthDelimited(value).build()
    }

    private fun varints(vararg values: Long): UnknownFieldSet.Field {
        return UnknownFieldSet.Field.newBuilder().apply {
            values.forEach(::addVarint)
        }.build()
    }

    private fun mixedLengthDelimited(values: List<ByteString>): UnknownFieldSet.Field {
        return UnknownFieldSet.Field.newBuilder().apply {
            addVarint(7)
            addFixed32(0x10203040)
            addFixed64(0x0102030405060708L)
            values.forEach(::addLengthDelimited)
            addGroup(message(99 to varints(9)))
        }.build()
    }

    private fun mixedVarintField(vararg values: Long): UnknownFieldSet.Field {
        return UnknownFieldSet.Field.newBuilder().apply {
            values.forEach(::addVarint)
            addFixed32(0x10203040)
            addFixed64(0x0102030405060708L)
            addLengthDelimited(opaque)
            addGroup(message(99 to varints(9)))
        }.build()
    }

    private fun addLengthHeader(payload: ByteArray): ByteArray {
        val size = payload.size + 4
        return ByteBuffer.allocate(size).putInt(size).put(payload).array()
    }

}
