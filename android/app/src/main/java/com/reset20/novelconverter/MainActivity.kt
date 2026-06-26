package com.reset20.novelconverter

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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

    // 文件选择器
    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> handleSelectedFiles(uris) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = com.reset20.novelconverter.databinding.ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        adapter = FileAdapter { position -> adapter.removeAt(position); updateUI() }
        binding.fileList.layoutManager = LinearLayoutManager(this)
        binding.fileList.adapter = adapter

        // 文件选择：只选 TXT
        binding.dropZone.setOnClickListener { filePicker.launch(arrayOf("text/plain")) }

        // 压缩空行
        binding.optCompact.setOnCheckedChangeListener { _, _ -> }

        // 分卷
        binding.optSplit.setOnCheckedChangeListener { _, checked ->
            binding.optSplitMB.isEnabled = checked
        }

        // 开始转换
        binding.btnConvert.setOnClickListener {
            if (adapter.items.isEmpty()) {
                Toast.makeText(this, "请先选择 TXT 文件", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (adapter.items.any { it.encoding == null }) {
                Toast.makeText(this, "正在检测编码，请稍候…", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startConversion()
        }

        // 清空
        binding.btnClear.setOnClickListener {
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
        val newItems = mutableListOf<FileAdapter.FileItem>()
        var skipped = 0
        for (uri in uris) {
            val item = resolveFileInfo(uri)
            if (item != null && item.name.lowercase().endsWith(".txt")) {
                newItems.add(item)
            } else {
                skipped++
            }
        }
        if (skipped > 0) {
            Toast.makeText(this, "已跳过 $skipped 个非 TXT 文件", Toast.LENGTH_SHORT).show()
        }
        if (newItems.isEmpty()) return

        adapter.addAll(newItems)
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
        adapter.items.forEachIndexed { index, item ->
            if (item.encoding != null) return@forEachIndexed

            withContext(Dispatchers.Main) {
                item.status = "detecting"
                adapter.notifyItemChanged(index)
            }

            try {
                val bytes = readBytes(item.uri)
                val result = EncodingDetector.detect(bytes)

                withContext(Dispatchers.Main) {
                    item.encoding = result.encodingName
                    item.status = "done"
                    adapter.notifyItemChanged(index)
                }
            } catch (e: Exception) {
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
        if (adapter.items.isEmpty()) return

        binding.btnConvert.isEnabled = false
        binding.progress.isVisible = true
        binding.result.isVisible = false

        val compact = binding.optCompact.isChecked
        val splitMB = if (binding.optSplit.isChecked) {
            binding.optSplitMB.text.toString().toIntOrNull() ?: 0
        } else 0

        lifecycleScope.launch {
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

                    val result = withContext(Dispatchers.IO) {
                        GbkConverter.convert(bytes, srcCharset, compact)
                    }

                    totalDropped += result.droppedChars
                    totalChars += result.totalChars

                    // 输出文件名：原名去扩展 + .gbk.txt
                    val outName = item.name.substringBeforeLast(".") + ".txt"
                    convertedFiles[outName] = result.data

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "处理失败: ${item.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // 打包 ZIP 并分享
            withContext(Dispatchers.IO) {
                val zips = ZipHelper.createZip(convertedFiles, "gbk_converted", splitMB)

                withContext(Dispatchers.Main) {
                    binding.progress.progress = 100
                    binding.result.isVisible = true
                    binding.stats.isVisible = true

                    val sizeStr = formatSize(zips.sumOf { it.data.size.toLong() })
                    binding.result.text = "✓ 转换完成 —— ${zips.size} 个 ZIP，共 $sizeStr"
                    binding.statsText.text = "共 ${adapter.items.size} 个文件 · $totalChars 字符 · 丢弃 $totalDropped 个"

                    // 分享 ZIP
                    shareZipFiles(zips)
                }
            }

            binding.btnConvert.isEnabled = true
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
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
