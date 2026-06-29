package com.reset20.novelconverter

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

object GbkConverter {

    private val gbk: Charset = Charset.forName("GBK")

    data class ConvertResult(
        val data: ByteArray,
        val totalChars: Int,
        val droppedChars: Int,
        val sourceEncoding: String
    )

    fun convert(
        input: ByteArray,
        sourceCharset: Charset,
        compactEmptyLines: Boolean = true
    ): ConvertResult {
        val text = String(input, sourceCharset)
        val processed = if (compactEmptyLines) {
            text.replace(Regex("\n{3,}"), "\n\n")
        } else {
            text
        }

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

        val outBytes = filtered.toString().toByteArray(gbk)

        return ConvertResult(
            data = outBytes,
            totalChars = processed.length,
            droppedChars = dropped,
            sourceEncoding = sourceCharset.name()
        )
    }

    fun previewDrop(text: String): List<Char> {
        val dropped = mutableListOf<Char>()
        val encoder = gbk.newEncoder()
        for (ch in text) {
            if (ch.code >= 0x80 && !encoder.canEncode(ch)) {
                dropped.add(ch)
            }
        }
        return dropped
    }
}
