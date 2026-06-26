package com.reset20.novelconverter

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * ZIP 打包工具
 * - 将转换后的文件打包为 ZIP
 * - 支持按大小分卷（拆分为多个 ZIP）
 */
object ZipHelper {

    data class ZipOutput(
        val fileName: String,  // 不含路径，如 "novels.zip"
        val data: ByteArray,   // ZIP 字节
        val fileCount: Int     // 内含文件数
    )

    /**
     * 创建 ZIP
     * @param files Map<文件名, GBK字节内容>
     * @param zipName ZIP 文件名（不含 .zip）
     * @param splitMB 分卷大小（MB），0 或负数表示不分卷
     */
    fun createZip(
        files: Map<String, ByteArray>,
        zipName: String = "converted",
        splitMB: Int = 0
    ): List<ZipOutput> {
        if (files.isEmpty()) return emptyList()

        val maxBytes = if (splitMB > 0) splitMB * 1024L * 1024L else Long.MAX_VALUE
        val results = mutableListOf<ZipOutput>()

        var partIndex = 0
        var currentBos = ByteArrayOutputStream()
        var currentZos = ZipOutputStream(currentBos)
        var currentSize = 0L
        var fileCount = 0

        for ((name, data) in files) {
            // 预估 ZIP entry 大小
            val entrySize = estimateEntrySize(name, data)

            // 如果加入此文件会超限，先保存当前卷
            if (currentSize + entrySize > maxBytes && fileCount > 0) {
                currentZos.close()
                val partName = if (splitMB > 0) "${zipName}_part${partIndex + 1}" else zipName
                results.add(ZipOutput("$partName.zip", currentBos.toByteArray(), fileCount))
                partIndex++

                currentBos = ByteArrayOutputStream()
                currentZos = ZipOutputStream(currentBos)
                currentSize = 0
                fileCount = 0
            }

            // 写入文件
            val entry = ZipEntry(name)
            currentZos.putNextEntry(entry)
            currentZos.write(data)
            currentZos.closeEntry()

            currentSize += entrySize
            fileCount++
        }

        // 最后一个卷
        currentZos.close()
        val partName = if (splitMB > 0 && partIndex > 0) "${zipName}_part${partIndex + 1}" else zipName
        results.add(ZipOutput("$partName.zip", currentBos.toByteArray(), fileCount))

        return results
    }

    private fun estimateEntrySize(name: String, data: ByteArray): Long {
        // 粗略估计：文件名 + 数据 + ZIP 元数据（~100 bytes）
        return name.length + data.size + 100L
    }
}
