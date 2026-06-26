package com.reset20.novelconverter

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset

class MainActivity : AppCompatActivity() {

    private lateinit var binding: com.reset20.novelconverter.databinding.ActivityMainBinding
    private lateinit var adapter: FileAdapter

    companion object {
        private const val TAG = "NovelConv"
    }

    // 文件选择器
    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> handleSelectedFiles(uris) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = com.reset20.novelconverter.databinding.ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "onCreate: activity created")

        setupUI()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }

    // ═══════════════════════════════════════════════
    // UI 初始化
    // ═══════════════════════════════════════════════
    private fun setupUI() {
        Log.d(TAG, "setupUI: initializing")
        adapter = FileAdapter { position -> adapter.removeAt(position); updateUI() }
        binding.fileList.layoutManager = LinearLayoutManager(this)
        binding.fileList.adapter = adapter

        // 文件选择：只选 TXT
        binding.dropZone.setOnClickListener {
            Log.d(TAG, "dropZone clicked → launching file picker")
            filePicker.launch(arrayOf("text/plain"))
        }

        // 压缩空行
        binding.optCompact.setOnCheckedChangeListener { _, _ -> }

        // 分卷
        binding.optSplit.setOnCheckedChangeListener { _, checked ->
            binding.optSplitMB.isEnabled = checked
        }

        // 开始转换
        binding.btnConvert.setOnClickListener {
            Log.d(TAG, "btnConvert clicked | items=${adapter.items.size} encodings=${adapter.items.map{it.encoding}}")
            if (adapter.items.isEmpty()) {
                Toast.makeText(this, "请先选择 TXT 文件", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val nullEncoding = adapter.items.filter { it.encoding == null }
            if (nullEncoding.isNotEmpty()) {
                Log.w(TAG, "btnConvert skipped: ${nullEncoding.size} items still have null encoding")
                Toast.makeText(this, "正在检测编码，请稍候…", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Log.d(TAG, "btnConvert: all encoding ready, starting conversion")
            startConversion()
        }

        // 清空
        binding.btnClear.setOnClickListener {
            Log.d(TAG, "btnClear clicked")
            adapter.clear()
            updateUI()
        }
    }

    // ═══════════════════════════════════════════════
    // 文件处理
    // ═══════════════════════════════════════════════
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            uri?.let { handleSelectedFiles(listOf(it)) }
        } else if (intent?.action == Intent.ACTION_SEND_MULTIPLE) {
            val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            if (!uris.isNullOrEmpty()) handleSelectedFiles(uris)
        }
    }

    private fun handleSelectedFiles(uris: List<Uri>) {
        Log.d(TAG, "handleSelectedFiles: ${uris.size} URIs received")
        val newItems = mutableListOf<FileAdapter.FileItem>()
        var skipped = 0
        for (uri in uris) {
            val item = resolveFileInfo(uri)
            if (item != null && item.name.lowercase().endsWith(".txt")) {
                newItems.add(item)
                Log.d(TAG, "  accepted: ${item.name} (${item.size} bytes)")
            } else {
                skipped++
                Log.w(TAG, "  skipped: ${item?.name ?: uri} (non-txt or null)")
            }
        }
        if (skipped > 0) {
            Toast.makeText(this, "已跳过 $skipped 个非 TXT 文件", Toast.LENGTH_SHORT).show()
        }
        if (newItems.isEmpty()) {
            Log.w(TAG, "handleSelectedFiles: no valid txt files")
            return
        }

        adapter.addAll(newItems)
        Log.d(TAG, "adapter now has ${adapter.items.size} items, launching encoding detection")
        updateUI()

        // 自动检测编码
        lifecycleScope.launch { detectEncodings() }
    }

    private fun resolveFileInfo(uri: Uri): FileAdapter.FileItem? {
        var name = "unknown.txt"
        var size = 0L

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }

        return FileAdapter.FileItem(uri = uri, name = name, size = size)
    }

    // ═══════════════════════════════════════════════
    // 编码检测
    // ═══════════════════════════════════════════════
    private suspend fun detectEncodings() = withContext(Dispatchers.IO) {
        Log.d(TAG, "detectEncodings: starting for ${adapter.items.size} items")
        adapter.items.forEachIndexed { index, item ->
            if (item.encoding != null) {
                Log.d(TAG, "  [$index] ${item.name}: already detected as ${item.encoding}, skip")
                return@forEachIndexed
            }

            Log.d(TAG, "  [$index] ${item.name}: detecting encoding...")
            withContext(Dispatchers.Main) {
                item.status = "detecting"
                adapter.notifyItemChanged(index)
            }

            try {
                val bytes = readBytes(item.uri)
                Log.d(TAG, "  [$index] read ${bytes.size} bytes")
                val result = EncodingDetector.detect(bytes)
                Log.d(TAG, "  [$index] detected: ${result.encodingName} (conf=${result.confidence})")

                withContext(Dispatchers.Main) {
                    item.encoding = result.encodingName
                    item.status = "done"
                    adapter.notifyItemChanged(index)
                }
            } catch (e: Exception) {
                Log.e(TAG, "  [$index] detection failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    item.encoding = "?"
                    item.status = "error"
                    adapter.notifyItemChanged(index)
                }
            }
        }

        withContext(Dispatchers.Main) { updateUI() }
    }

    private fun readBytes(uri: Uri): ByteArray {
        return contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
    }

    // ═══════════════════════════════════════════════
    // 转换核心
    // ═══════════════════════════════════════════════
    private fun startConversion() {
        Log.d(TAG, "startConversion: entered, items=${adapter.items.size}")
        if (adapter.items.isEmpty()) {
            Log.w(TAG, "startConversion: no items, returning")
            return
        }

        binding.btnConvert.isEnabled = false
        binding.btnConvert.text = "转换中…"
        binding.progress.isVisible = true
        binding.result.isVisible = false
        Log.d(TAG, "startConversion: UI set, launching coroutine")

        val compact = binding.optCompact.isChecked
        val splitMB = if (binding.optSplit.isChecked) {
            binding.optSplitMB.text.toString().toIntOrNull() ?: 0
        } else 0
        Log.d(TAG, "startConversion: opts compact=$compact splitMB=$splitMB")

        lifecycleScope.launch {
            Log.d(TAG, "startConversion: coroutine started")
            val convertedFiles = mutableMapOf<String, ByteArray>()
            var totalDropped = 0
            var totalChars = 0

            for ((index, item) in adapter.items.withIndex()) {
                withContext(Dispatchers.Main) {
                    binding.progress.progress = (index * 100 / adapter.items.size)
                    binding.progress.isVisible = true
                }

                try {
                    val bytes = withContext(Dispatchers.IO) { readBytes(item.uri) }
                    val srcCharset = item.encoding?.let { nameToCharset(it) } ?: Charsets.UTF_8
                    Log.d(TAG, "  converting [$index] ${item.name} | ${bytes.size} bytes | charset=${srcCharset.name()}")

                    val result = withContext(Dispatchers.IO) {
                        GbkConverter.convert(bytes, srcCharset, compact)
                    }
                    Log.d(TAG, "  done [$index] → ${result.data.size} bytes, dropped ${result.droppedChars}/${result.totalChars}")

                    totalDropped += result.droppedChars
                    totalChars += result.totalChars

                    // 输出文件名：原名去扩展 + .gbk.txt
                    val outName = item.name.substringBeforeLast(".") + ".txt"
                    convertedFiles[outName] = result.data

                } catch (e: Exception) {
                    Log.e(TAG, "  FAILED [$index] ${item.name}: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "处理失败: ${item.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            Log.d(TAG, "all files converted. packing ZIP... (${convertedFiles.size} files)")
            // 打包 ZIP 并分享
            withContext(Dispatchers.IO) {
                val zips = ZipHelper.createZip(convertedFiles, "gbk_converted", splitMB)
                Log.d(TAG, "ZIP created: ${zips.size} parts, total ${zips.sumOf { it.data.size }} bytes")

                withContext(Dispatchers.Main) {
                    binding.progress.progress = 100
                    binding.result.isVisible = true
                    binding.stats.isVisible = true

                    val sizeStr = formatSize(zips.sumOf { it.data.size.toLong() })
                    binding.result.text = "✓ 转换完成 —— ${zips.size} 个 ZIP，共 $sizeStr"
                    binding.statsText.text = "共 ${adapter.items.size} 个文件 · $totalChars 字符 · 丢弃 $totalDropped 个"

                    // 分享 ZIP
                    Log.d(TAG, "sharing ${zips.size} ZIP files")
                    shareZipFiles(zips)
                }
            }

            binding.btnConvert.isEnabled = true
            binding.btnConvert.text = "开始转换"
            Log.d(TAG, "startConversion: complete")
        }
    }

    private fun nameToCharset(name: String): Charset = when (name.lowercase()) {
        "utf-8" -> Charsets.UTF_8
        "gbk" -> Charset.forName("GBK")
        "big5" -> Charset.forName("BIG5")
        "utf-16", "utf-16le" -> Charsets.UTF_16LE
        "utf-16be" -> Charsets.UTF_16BE
        else -> Charsets.UTF_8
    }

    // ═══════════════════════════════════════════════
    // ZIP 分享
    // ═══════════════════════════════════════════════
    private fun shareZipFiles(zips: List<ZipHelper.ZipOutput>) {
        val cacheDir = File(cacheDir, "output")
        cacheDir.mkdirs()

        val files = zips.map { zip ->
            val file = File(cacheDir, zip.fileName)
            file.writeBytes(zip.data)
            Log.d(TAG, "  wrote ${zip.fileName} (${zip.data.size} bytes)")
            file
        }

        val uris = files.map { file ->
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        }

        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/zip"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        Log.d(TAG, "shareZipFiles: launching share intent with ${uris.size} ZIPs")
        startActivity(Intent.createChooser(shareIntent, "分享转换结果"))
    }

    // ═══════════════════════════════════════════════
    // UI 更新
    // ═══════════════════════════════════════════════
    private fun updateUI() {
        val hasFiles = adapter.items.isNotEmpty()
        val detectingCount = adapter.items.count { it.encoding == null }
        val allDetected = hasFiles && detectingCount == 0

        binding.actionBar.isVisible = hasFiles
        binding.fileList.isVisible = hasFiles
        binding.dropZone.isVisible = !hasFiles

        when {
            !hasFiles -> {
                binding.btnConvert.isEnabled = false
                binding.btnConvert.text = "开始转换"
            }
            detectingCount > 0 -> {
                binding.btnConvert.isEnabled = false
                binding.btnConvert.text = "检测编码中… ($detectingCount)"
            }
            else -> {
                binding.btnConvert.isEnabled = true
                binding.btnConvert.text = "开始转换"
            }
        }
        Log.d(TAG, "updateUI: hasFiles=$hasFiles detecting=$detectingCount btnEnabled=${binding.btnConvert.isEnabled} btnText=${binding.btnConvert.text}")
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
