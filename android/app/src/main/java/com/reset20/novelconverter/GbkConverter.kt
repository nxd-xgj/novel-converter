package com.reset20.novelconverter

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * GBK 纯净化转换器
 * - 从任意编码读取文本
 * - 丢弃 GBK 不支持的字符
 * - 输出纯 GBK 字节
 */
object GbkConverter {

    private val gbk: Charset = Charset.forName("GBK")
    private val gbkEncoder = gbk.newEncoder()

    data class ConvertResult(
        val data: ByteArray,       // GBK 字节
        val totalChars: Int,       // 原文总字符数
        val droppedChars: Int,     // 丢弃的字符数
        val sourceEncoding: String // 源编码名
    )

    /**
     * 转换字节数组为纯 GBK
     * @param input 原始字节
     * @param sourceCharset 源编码
     * @param compactEmptyLines 是否压缩连续空行（3+ → 2）
     */
    fun convert(
        input: ByteArray,
        sourceCharset: Charset,
        compactEmptyLines: Boolean = true
    ): ConvertResult {
        // 1. 解码为字符串
        val text = String(input, sourceCharset)

        // 2. 压缩空行（可选）
        val processed = if (compactEmptyLines) {
            text.replace(Regex("\n{3,}"), "\n\n")
        } else {
            text
        }

        // 3. 过滤掉 GBK 不支持的字符
        val encoder = gbk.newEncoder()
        var dropped = 0
        val filtered = StringBuilder(processed.length)

        for (ch in processed) {
            if (ch.code < 0x80 || encoder.canEncode(ch)) {
                filtered.append(ch)
            } else {
                dropped++
            }
        }

        // 4. 编码为 GBK 字节
        val outBytes = filtered.toString().toByteArray(gbk)

        return ConvertResult(
            data = outBytes,
            totalChars = processed.length,
            droppedChars = dropped,
            sourceEncoding = sourceCharset.name()
        )
    }

    /**
     * 仅检测哪些字符会被丢弃（预览用）
     */
    fun previewDrop(text: String): List<Char> {
        val dropped = mutableListOf<Char>()
        for (ch in text) {
            if (ch.code >= 0x80 && !gbkEncoder.canEncode(ch)) {
                dropped.add(ch)
            }
        }
        return dropped
    }
}
