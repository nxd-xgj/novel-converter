package com.reset20.novelconverter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/**
 * 文件列表适配器
 */
class FileAdapter(
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

    data class FileItem(
        val uri: android.net.Uri,
        val name: String,
        val size: Long,
        var encoding: String? = null,   // 检测出的编码名
        var status: String = "pending"  // pending / detecting / done / error
    )

    val items = mutableListOf<FileItem>()

    fun addAll(newItems: List<FileItem>) {
        items.addAll(newItems)
        notifyItemRangeInserted(items.size - newItems.size, newItems.size)
    }

    fun clear() {
        val count = items.size
        items.clear()
        notifyItemRangeRemoved(0, count)
    }

    fun removeAt(position: Int) {
        if (position in items.indices) {
            items.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(
        private val view: android.view.View
    ) : RecyclerView.ViewHolder(view) {
        private val nameView: android.widget.TextView = view.findViewById(R.id.fileName)
        private val encodingView: android.widget.TextView = view.findViewById(R.id.fileEncoding)
        private val sizeView: android.widget.TextView = view.findViewById(R.id.fileSize)

        fun bind(item: FileItem, position: Int) {
            nameView.text = item.name
            sizeView.text = formatSize(item.size)

            if (item.encoding != null) {
                encodingView.visibility = android.view.View.VISIBLE
                encodingView.text = item.encoding
            } else {
                encodingView.visibility = android.view.View.GONE
            }

            view.setOnLongClickListener {
                onRemove(position)
                true
            }
        }

        private fun formatSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
    }
}
