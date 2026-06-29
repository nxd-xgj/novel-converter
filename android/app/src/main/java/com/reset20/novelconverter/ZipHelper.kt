package com.reset20.novelconverter

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipHelper {

    data class ZipOutput(
        val fileName: String,
        val data: ByteArray,
        val fileCount: Int
    )

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
            val entrySize = name.length + data.size + 100L

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

            val entry = ZipEntry(name)
            currentZos.putNextEntry(entry)
            currentZos.write(data)
            currentZos.closeEntry()

            currentSize += entrySize
            fileCount++
        }

        currentZos.close()
        val partName = if (splitMB > 0 && partIndex > 0) "${zipName}_part${partIndex + 1}" else zipName
        results.add(ZipOutput("$partName.zip", currentBos.toByteArray(), fileCount))

        return results
    }
}
