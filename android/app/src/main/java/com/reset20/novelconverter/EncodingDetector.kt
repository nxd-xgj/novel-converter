package com.reset20.novelconverter

import org.mozilla.universalchardet.UniversalDetector
import java.nio.charset.Charset

object EncodingDetector {

    data class DetectResult(
        val charset: Charset,
        val encodingName: String,
        val confidence: Float
    )

    private val charsetAliases = mapOf(
        "gb2312" to "GBK",
        "gb18030" to "GBK",
        "big5" to "BIG5",
        "big5-hkscs" to "BIG5",
        "euc-kr" to "EUC-KR",
        "shift_jis" to "Shift_JIS",
        "euc-jp" to "EUC-JP",
        "windows-1252" to "windows-1252",
        "iso-8859-1" to "ISO-8859-1",
        "utf-8" to "UTF-8",
        "utf-16" to "UTF-16",
        "utf-16le" to "UTF-16LE",
        "utf-16be" to "UTF-16BE"
    )

    fun detect(data: ByteArray, offset: Int = 0, length: Int = data.size): DetectResult {
        // BOM 检测
        val bomCharset = checkBom(data, offset, length)
        if (bomCharset != null) {
            return DetectResult(bomCharset, displayName(bomCharset), 1.0f)
        }

        // juniversalchardet
        val detector = UniversalDetector(null)
        detector.handleData(data, offset, length)
        detector.dataEnd()
        val detected = detector.detectedCharset

        if (detected != null) {
            val alias = detected.lowercase()
            val charsetName = charsetAliases[alias] ?: detected.uppercase()
            return try {
                val charset = Charset.forName(charsetName)
                DetectResult(charset, displayName(charset),
                    if (alias == "utf-8") 0.98f
                    else if (alias == "gb2312" || alias == "gb18030") 0.95f
                    else 0.90f)
            } catch (e: Exception) {
                heuristicDetect(data, offset, length)
            }
        }

        return heuristicDetect(data, offset, length)
    }

    private fun checkBom(data: ByteArray, offset: Int, length: Int): Charset? {
        if (length < 2) return null
        val b0 = data[offset].toInt() and 0xFF
        val b1 = data[offset + 1].toInt() and 0xFF
        if (b0 == 0xFE && b1 == 0xFF) return Charsets.UTF_16BE
        if (b0 == 0xFF && b1 == 0xFE) {
            if (length >= 4) {
                val b2 = data[offset + 2].toInt() and 0xFF
                val b3 = data[offset + 3].toInt() and 0xFF
                if (b2 == 0x00 && b3 == 0x00) return charsetOrNull("UTF-32LE")
            }
            return Charsets.UTF_16LE
        }
        if (length >= 3) {
            val b2 = data[offset + 2].toInt() and 0xFF
            if (b0 == 0xEF && b1 == 0xBB && b2 == 0xBF) return Charsets.UTF_8
        }
        return null
    }

    private fun heuristicDetect(data: ByteArray, offset: Int, length: Int): DetectResult {
        val end = (offset + length).coerceAtMost(data.size)
        var gbkScore = 0
        var utf8Score = 0
        var i = offset

        while (i < end) {
            val b = data[i].toInt() and 0xFF
            when {
                b < 0x80 -> i++
                b in 0xC0..0xDF -> {
                    if (i + 1 < end) {
                        val b2 = data[i + 1].toInt() and 0xFF
                        if (b2 in 0x80..0xBF) utf8Score++ else gbkScore++
                    }
                    i += 2
                }
                b in 0xE0..0xEF -> {
                    if (i + 2 < end) {
                        val b2 = data[i + 1].toInt() and 0xFF
                        val b3 = data[i + 2].toInt() and 0xFF
                        if (b2 in 0x80..0xBF && b3 in 0x80..0xBF) utf8Score += 2 else gbkScore += 2
                    }
                    i += 3
                }
                b in 0xF0..0xF7 -> {
                    if (i + 3 < end) utf8Score += 3
                    i += 4
                }
                b in 0x81..0xFE -> {
                    if (i + 1 < end) {
                        val b2 = data[i + 1].toInt() and 0xFF
                        if (b2 in 0x40..0xFE) gbkScore += 2
                    }
                    i += 2
                }
                else -> i++
            }
        }

        val charset = if (utf8Score > gbkScore) Charsets.UTF_8 else Charset.forName("GBK")
        val conf = if (utf8Score > gbkScore)
            (utf8Score.toFloat() / (utf8Score + gbkScore + 1)).coerceIn(0.6f, 0.9f)
        else
            (gbkScore.toFloat() / (utf8Score + gbkScore + 1)).coerceIn(0.6f, 0.9f)

        return DetectResult(charset, displayName(charset), conf)
    }

    private fun charsetOrNull(name: String) = try { Charset.forName(name) } catch (_: Exception) { null }

    private fun displayName(charset: Charset): String = when (charset.name().lowercase()) {
        "utf-8", "utf8" -> "UTF-8"
        "gbk", "gb2312", "gb18030" -> "GBK"
        "big5", "big5-hkscs" -> "BIG5"
        "utf-16", "utf-16le", "utf-16be" -> "UTF-16"
        else -> charset.name().uppercase()
    }
}
