package moe.ono.util

import android.net.Uri
import android.util.Base64
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap

object MqqApiUrlTool {

    data class ParamValue(
        val raw: String,
        val urlDecoded: String,
        val base64Decoded: String?,
        val finalDecoded: String
    )

    data class ParsedUrl(
        val scheme: String?,
        val authority: String?,
        val path: String?,
        val params: LinkedHashMap<String, String>
    ) {
        fun getParam(key: String): String? = params[key]

        fun getDecodedParam(key: String): ParamValue? {
            val raw = params[key] ?: return null
            return decodeValue(raw)
        }

        /**
         * 直接替换原始值，不做 Base64 编码
         */
        fun replaceRaw(key: String, newValue: String): ParsedUrl {
            params[key] = newValue
            return this
        }

        /**
         * 替换为普通字符串，并自动 Base64 编码写回
         */
        fun replaceAsBase64(key: String, plainText: String): ParsedUrl {
            params[key] = encodeBase64NoWrap(plainText)
            return this
        }

        /**
         * 删除参数
         */
        fun remove(key: String): ParsedUrl {
            params.remove(key)
            return this
        }

        /**
         * 转回完整 URL
         */
        fun build(): String {
            val sb = StringBuilder()
            if (!scheme.isNullOrEmpty()) {
                sb.append(scheme).append("://")
            }
            if (!authority.isNullOrEmpty()) {
                sb.append(authority)
            }
            if (!path.isNullOrEmpty()) {
                sb.append(if (path.startsWith("/")) path else "/$path")
            }

            if (params.isNotEmpty()) {
                sb.append("?")
                sb.append(
                    params.entries.joinToString("&") { (k, v) ->
                        "${encodeQueryComponent(k)}=${encodeQueryComponent(v)}"
                    }
                )
            }
            return sb.toString()
        }

        /**
         * 打印调试信息
         */
        fun dump(): String {
            val sb = StringBuilder()
            sb.appendLine("scheme = $scheme")
            sb.appendLine("authority = $authority")
            sb.appendLine("path = $path")
            sb.appendLine("params:")
            for ((k, v) in params) {
                val decoded = decodeValue(v)
                sb.appendLine("  [$k]")
                sb.appendLine("    raw           = ${decoded.raw}")
                sb.appendLine("    urlDecoded    = ${decoded.urlDecoded}")
                sb.appendLine("    base64Decoded = ${decoded.base64Decoded}")
                sb.appendLine("    finalDecoded  = ${decoded.finalDecoded}")
            }
            return sb.toString()
        }
    }

    /**
     * 解析整个 mqqapi URL
     */
    fun parse(url: String): ParsedUrl {
        val uri = Uri.parse(url)
        val params = LinkedHashMap<String, String>()

        val query = uri.encodedQuery.orEmpty()
        if (query.isNotEmpty()) {
            query.split("&").forEach { pair ->
                if (pair.isEmpty()) return@forEach
                val index = pair.indexOf('=')
                if (index >= 0) {
                    val key = decodeQueryComponent(pair.substring(0, index))
                    val value = decodeQueryComponent(pair.substring(index + 1))
                    params[key] = value
                } else {
                    val key = decodeQueryComponent(pair)
                    params[key] = ""
                }
            }
        }

        return ParsedUrl(
            scheme = uri.scheme,
            authority = uri.authority,
            path = uri.path,
            params = params
        )
    }

    /**
     * 解码单个参数值
     * 顺序：
     * 1. URLDecode
     * 2. 尝试 Base64 解码
     */
    fun decodeValue(raw: String): ParamValue {
        val urlDecoded = safeUrlDecode(raw)
        val base64Decoded = tryDecodeBase64ToString(urlDecoded)
        val finalDecoded = base64Decoded ?: urlDecoded
        return ParamValue(
            raw = raw,
            urlDecoded = urlDecoded,
            base64Decoded = base64Decoded,
            finalDecoded = finalDecoded
        )
    }

    /**
     * 普通字符串转 Base64（无换行）
     */
    fun encodeBase64NoWrap(text: String): String {
        return Base64.encodeToString(
            text.toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP
        )
    }

    /**
     * Base64 字符串解码为 UTF-8 文本
     */
    fun decodeBase64ToString(base64: String): String {
        val normalized = normalizeBase64(base64)
        return String(
            Base64.decode(normalized, Base64.DEFAULT),
            StandardCharsets.UTF_8
        )
    }

    private fun tryDecodeBase64ToString(text: String): String? {
        return try {
            val normalized = normalizeBase64(text)
            val decoded = Base64.decode(normalized, Base64.DEFAULT)
            val result = String(decoded, StandardCharsets.UTF_8)

            // 做个简单判断，避免把普通字符串误判成 Base64
            if (result.isNotEmpty() && result.any { it.code in 9..13 || it.code in 32..126 || it.code > 127 }) {
                result
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun normalizeBase64(input: String): String {
        var s = input.trim()
            .replace('-', '+')
            .replace('_', '/')

        val mod = s.length % 4
        if (mod != 0) {
            s += "=".repeat(4 - mod)
        }
        return s
    }

    private fun safeUrlDecode(text: String): String {
        return try {
            URLDecoder.decode(text, "UTF-8")
        } catch (_: Throwable) {
            text
        }
    }

    private fun decodeQueryComponent(text: String): String {
        return try {
            URLDecoder.decode(text, "UTF-8")
        } catch (_: Throwable) {
            text
        }
    }

    private fun encodeQueryComponent(text: String): String {
        return try {
            URLEncoder.encode(text, "UTF-8")
        } catch (_: Throwable) {
            text
        }
    }
}