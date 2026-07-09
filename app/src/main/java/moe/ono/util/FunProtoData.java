package moe.ono.util;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;


public class FunProtoData {
    private static final int MAX_ACCIDENTAL_TEXT_BYTES = 64;
    private final HashMap<Integer, List<Object>> values = new HashMap<>();

    private static final class Fixed32Value {
        private final int value;

        private Fixed32Value(int value) {
            this.value = value;
        }
    }

    private static final class Fixed64Value {
        private final long value;

        private Fixed64Value(long value) {
            this.value = value;
        }
    }

    private static final class ProtoProbe {
        private final boolean valid;
        private final int fieldCount;
        private final boolean hasLengthDelimited;

        private ProtoProbe(boolean valid, int fieldCount, boolean hasLengthDelimited) {
            this.valid = valid;
            this.fieldCount = fieldCount;
            this.hasLengthDelimited = hasLengthDelimited;
        }
    }

    public static byte[] getUnpPackage(byte[] b) {
        if (b == null) {
            return null;
        }
        if (b.length < 4) {
            return b;
        }
        if ((b[0] & 0xFF) == 0) {
            return Arrays.copyOfRange(b, 4, b.length);
        } else {
            return b;
        }
    }

    public void fromJSON(JSONObject json){
        try {
            Iterator<String> key_it = json.keys();
            while (key_it.hasNext()){
                String key = key_it.next();
                int k = Integer.parseInt(key);
                Object value = json.get(key);
                if (value instanceof JSONObject){
                    FunProtoData newProto = new FunProtoData();
                    newProto.fromJSON((JSONObject) value);
                    putValue(k, newProto);
                }else if (value instanceof JSONArray arr){
                    for (int i = 0;i < arr.length(); i++){
                        Object arr_obj = arr.get(i);
                        if (arr_obj instanceof JSONObject){
                            FunProtoData newProto = new FunProtoData();
                            newProto.fromJSON((JSONObject) arr_obj);
                            putValue(k, newProto);
                        }else {
                            putValue(k, arr_obj);
                        }
                    }
                }else {
                    putValue(k, value);
                }
            }
        }catch (Exception ignored){ }
    }
    private void putValue(int key, Object value){
        List<Object> list = values.computeIfAbsent(key, k -> new ArrayList<>());
        list.add(value);
    }
    public void fromBytes(byte[] b) throws IOException {
        CodedInputStream in = CodedInputStream.newInstance(b);
        while (!in.isAtEnd()) {
            int tag = in.readTag();
            if (tag == 0) {
                break;
            }
            int fieldNumber = tag >>> 3;
            int wireType = tag & 7;
            if (wireType == 4 || wireType == 3 || wireType > 5) throw new IOException("Unexpected wireType: " + wireType);
            switch (wireType) {
                case 0:
                    putValue(fieldNumber, in.readInt64());
                    break;
                case 1:
                    putValue(fieldNumber, new Fixed64Value(in.readFixed64()));
                    break;
                case 2: {
                    byte[] subBytes = in.readByteArray();
                    putValue(fieldNumber, decodeLengthDelimited(subBytes));
                    break;
                }
                case 5:
                    putValue(fieldNumber, new Fixed32Value(in.readFixed32()));
                    break;
                default:
                    putValue(fieldNumber, "Unknown wireType: " + wireType);
                    break;
            }
        }
    }

    private Object decodeLengthDelimited(byte[] subBytes) {
        if (subBytes == null || subBytes.length == 0) {
            return "";
        }

        ProtoProbe protoProbe = probeProtoMessage(subBytes);

        // Prefer short user-visible text when protobuf parsing is impossible
        // or only succeeds as a trivial primitive field by chance.
        if (looksLikeAccidentalTextProto(subBytes, protoProbe)) {
            return new String(subBytes, StandardCharsets.UTF_8);
        }

        if (protoProbe.valid) {
            try {
                FunProtoData subData = new FunProtoData();
                subData.fromBytes(subBytes);
                return subData;
            } catch (Exception ignored) {
                // Fall through to the text/hex fallback.
            }
        }

        if (isStrictUtf8(subBytes)) {
            return new String(subBytes, StandardCharsets.UTF_8);
        }

        return "hex->" + bytesToHex(subBytes);
    }

    private static boolean looksLikeAccidentalTextProto(byte[] bytes, ProtoProbe protoProbe) {
        if (!looksLikeReadableUtf8(bytes)) {
            return false;
        }
        if (!protoProbe.valid) {
            return true;
        }
        return bytes.length <= MAX_ACCIDENTAL_TEXT_BYTES
                && protoProbe.fieldCount == 1
                && !protoProbe.hasLengthDelimited;
    }

    private static boolean looksLikeReadableUtf8(byte[] bytes) {
        if (!isStrictUtf8(bytes)) {
            return false;
        }

        String s = new String(bytes, StandardCharsets.UTF_8);
        if (s.isEmpty()) {
            return true;
        }

        int printable = 0;
        int suspiciousControl = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
                suspiciousControl++;
            } else {
                printable++;
            }
        }
        return suspiciousControl == 0 && printable > 0;
    }

    private static boolean isStrictUtf8(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static ProtoProbe probeProtoMessage(byte[] bytes) {
        try {
            CodedInputStream testIn = CodedInputStream.newInstance(bytes);
            int fieldCount = 0;
            boolean hasLengthDelimited = false;
            while (!testIn.isAtEnd()) {
                int tag = testIn.readTag();
                if (tag == 0) {
                    break;
                }

                int fieldNumber = tag >>> 3;
                int wireType = tag & 7;
                if (fieldNumber <= 0 || wireType == 3 || wireType == 4 || wireType > 5) {
                    return new ProtoProbe(false, 0, false);
                }

                fieldCount++;
                switch (wireType) {
                    case 0:
                        testIn.readInt64();
                        break;
                    case 1:
                        testIn.readFixed64();
                        break;
                    case 2:
                        hasLengthDelimited = true;
                        int size = testIn.readRawVarint32();
                        if (size < 0) {
                            return new ProtoProbe(false, 0, false);
                        }
                        testIn.skipRawBytes(size);
                        break;
                    case 5:
                        testIn.readFixed32();
                        break;
                    default:
                        return new ProtoProbe(false, 0, false);
                }
            }
            return new ProtoProbe(fieldCount > 0 && testIn.isAtEnd(), fieldCount, hasLengthDelimited);
        } catch (Exception e) {
            return new ProtoProbe(false, 0, false);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    public JSONObject toJSON()throws Exception{
        JSONObject obj = new JSONObject();
        for (Integer k_index : values.keySet()){
            List<?> list = values.get(k_index);
            assert list != null;
            if (list.size() > 1){
                JSONArray arr = new JSONArray();
                for (Object o : list){
                    arr.put(valueToText(o));
                }
                obj.put(String.valueOf(k_index), arr);
            }else {
                for (Object o : list){
                    obj.put(String.valueOf(k_index), valueToText(o));
                }
            }
        }
        return obj;
    }
    private Object valueToText(Object value) throws Exception {
        if (value instanceof FunProtoData data){
            return data.toJSON();
        }else if (value instanceof Fixed64Value fixed64Value){
            return fixed64Value.value;
        }else if (value instanceof Fixed32Value fixed32Value){
            return fixed32Value.value;
        }else {
            return value;
        }
    }
    public byte[] toBytes(){
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        CodedOutputStream out = CodedOutputStream.newInstance(bos);
        try {
            for (Integer k_index : values.keySet()){
                List<?> list = values.get(k_index);
                assert list != null;
                for (Object o : list){
                    if (o instanceof Long){
                        long l = (long) o;
                        out.writeInt64(k_index , l);
                    }else if (o instanceof Fixed64Value fixed64Value){
                        out.writeFixed64(k_index, fixed64Value.value);
                    }else if (o instanceof String s){
                        out.writeByteArray(k_index , s.getBytes(StandardCharsets.UTF_8));
                    }else if (o instanceof FunProtoData data){
                        byte[] subBytes = data.toBytes();
                        out.writeByteArray(k_index , subBytes);
                    }else if (o instanceof Fixed32Value fixed32Value){
                        out.writeFixed32(k_index, fixed32Value.value);
                    }else if (o instanceof Integer){
                        int i = (int) o;
                        out.writeInt32(k_index, i);
                    }else {
                        Logger.w("FunProtoData.toBytes "+ "Unknown type: " + o.getClass().getName());
                    }
                }
            }
            out.flush();
            return bos.toByteArray();
        }catch (Exception e){
            Logger.e("FunProtoData - toBytes", e);
            return new byte[0];
        }
    }
}
