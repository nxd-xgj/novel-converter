package com.reset20.novelconverter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
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
    private val logBuffer = StringBuilder()

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> handleFiles(uris) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = com.reset20.novelconverter.databinding.ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = FileAdapter { i -> adapter.removeAt(i); refreshUI() }
        binding.fileList.layoutManager = LinearLayoutManager(this)
        binding.fileList.adapter = adapter

        binding.dropZone.setOnClickListener { filePicker.launch(arrayOf("text/plain")) }
        binding.btnConvert.setOnClickListener { convert() }
        binding.btnClear.setOnClickListener { adapter.clear(); refreshUI() }

        binding.btnToggleDebug.setOnClickListener {
            val show = binding.debugPanel.isVisible.not()
            binding.debugPanel.isVisible = show
            binding.btnToggleDebug.text = if (show) "∧ 日志" else "∨ 日志"
        }
        binding.btnCopyLog.setOnClickListener {
            val cb = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cb.setPrimaryClip(ClipData.newPlainText("log", logBuffer.toString()))
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
        }

        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent); intent?.let { handleIncomingIntent(it) }
    }

    private fun log(msg: String) {
        logBuffer.append(msg).append("\n")
        if (::binding.isInitialized) {
            binding.debugLog.text = logBuffer.toString()
        }
    }

    // ── 文件处理 ──

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let {
                handleFiles(listOf(it))
            }
        } else if (intent?.action == Intent.ACTION_SEND_MULTIPLE) {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                ?.takeIf { it.isNotEmpty() }?.let { handleFiles(it) }
        }
    }

    private fun handleFiles(uris: List<Uri>) {
        val items = mutableListOf<FileAdapter.FileItem>()
        var nope = 0
        for (uri in uris) {
            val f = resolveFile(uri)
            if (f != null && f.name.lowercase().endsWith(".txt")) items.add(f)
            else nope++
        }
        if (nope > 0) Toast.makeText(this, "跳过 $nope 个非TXT", Toast.LENGTH_SHORT).show()
        if (items.isEmpty()) return

        adapter.addAll(items)
        refreshUI()

        lifecycleScope.launch(Dispatchers.IO) { detectAll() }
    }

    private fun resolveFile(uri: Uri): FileAdapter.FileItem? {
        var name = "unknown.txt"
        var size = 0L
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0) name = c.getString(ni) ?: name
                if (si >= 0) size = c.getLong(si)
            }
        }
        return FileAdapter.FileItem(uri = uri, name = name, size = size)
    }

    private fun readAll(uri: Uri) =
        contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)

    // ── 编码检测 ──

    private suspend fun detectAll() {
        adapter.items.forEachIndexed { i, item ->
            if (item.encoding != null) return@forEachIndexed
            try {
                val data = readAll(item.uri)
                val r = EncodingDetector.detect(data)
                withContext(Dispatchers.Main) { item.encoding = r.encodingName; adapter.notifyItemChanged(i) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { item.encoding = "?"; adapter.notifyItemChanged(i) }
            }
        }
        withContext(Dispatchers.Main) { refreshUI() }
    }

    // ── 转换 ──

    private fun convert() {
        if (adapter.items.isEmpty()) return
        binding.btnConvert.isEnabled = false
        binding.progress.isVisible = true
        binding.result.isVisible = false
        val compact = binding.optCompact.isChecked

        lifecycleScope.launch {
            val outs = mutableMapOf<String, ByteArray>()
            for ((i, item) in adapter.items.withIndex()) {
                withContext(Dispatchers.Main) {
                    binding.progress.progress = (i * 100 / adapter.items.size)
                }
                try {
                    val raw = withContext(Dispatchers.IO) { readAll(item.uri) }
                    val cs = item.encoding?.let { nameToCharset(it) } ?: Charsets.UTF_8
                    val r = withContext(Dispatchers.IO) { GbkConverter.convert(raw, cs, compact) }
                    val name = item.name.substringBeforeLast(".") + ".txt"
                    outs[name] = r.data
                } catch (e: Exception) {
                    log("失败: ${item.name} — ${e.message}")
                }
            }

            if (outs.isEmpty()) {
                withContext(Dispatchers.Main) {
                    binding.result.isVisible = true
                    binding.result.text = "没有可输出的文件"
                    binding.btnConvert.isEnabled = true
                }
                return@launch
            }

            val zips = withContext(Dispatchers.IO) { ZipHelper.createZip(outs, "gbk") }
            withContext(Dispatchers.Main) {
                binding.progress.progress = 100
                binding.result.isVisible = true
                binding.result.text = "完成 — ${zips.size} 个ZIP"
                binding.btnConvert.isEnabled = true
                shareZip(zips)
            }
        }
    }

    private fun nameToCharset(n: String): Charset = when (n.lowercase()) {
        "utf-8" -> Charsets.UTF_8; "gbk" -> Charset.forName("GBK")
        "big5" -> Charset.forName("BIG5"); else -> Charsets.UTF_8
    }

    // ── 分享 ──

    private fun shareZip(zips: List<ZipHelper.ZipOutput>) {
        val dir = File(cacheDir, "out").also { it.mkdirs() }
        val files = zips.map { z -> File(dir, z.fileName).also { it.writeBytes(z.data) } }
        val uris = files.map { FileProvider.getUriForFile(this, "$packageName.fileprovider", it) }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, java.util.ArrayList<android.os.Parcelable>(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "分享"))
    }

    // ── UI ──

    private fun refreshUI() {
        val n = adapter.items.size
        val ok = n > 0 && adapter.items.all { it.encoding != null }
        binding.dropZone.isVisible = n == 0
        binding.fileList.isVisible = n > 0
        binding.actionBar.isVisible = n > 0
        binding.btnConvert.isEnabled = ok
        binding.fileCount.text = "$n 个文件"
        binding.fileCount.isVisible = n > 0
        binding.btnConvert.text = if (ok) "开始" else "检测中…"
    }
}
