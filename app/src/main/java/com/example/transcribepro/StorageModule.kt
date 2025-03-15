package com.example.transcribepro

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Button
import android.widget.ImageButton
import android.content.ClipboardManager
import android.content.ClipData
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Date
import java.text.SimpleDateFormat
import android.widget.LinearLayout

class TranscriptionStorageActivity : ComponentActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var backButton: Button
    private var transcriptionsList = mutableListOf<TranscriptionItem>()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_storage)

        // Initialize views
        recyclerView = findViewById(R.id.transcriptions_recycler_view)
        emptyView = findViewById(R.id.empty_view)
        backButton = findViewById(R.id.back_button)

        // Configure RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = TranscriptionAdapter(transcriptionsList)
        recyclerView.adapter = adapter

        // Load saved transcriptions
        loadTranscriptions()

        // Set up back button
        backButton.setOnClickListener {
            finish()
        }
    }

    private fun loadTranscriptions() {
        transcriptionsList.clear()
        val files = filesDir.listFiles { file -> file.name.startsWith("transcription_") }

        if (files == null || files.isEmpty()) {
            showEmptyView(true)
            return
        }

        files.sortByDescending { it.lastModified() }

        for (file in files) {
            val fileName = file.name
            val content = readFileContent(fileName)
            val timestamp = extractTimestampFromFilename(fileName)
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm")
            val formattedDate = dateFormat.format(Date(timestamp))

            transcriptionsList.add(TranscriptionItem(fileName, content, formattedDate))
        }

        (recyclerView.adapter as TranscriptionAdapter).notifyDataSetChanged()
        showEmptyView(transcriptionsList.isEmpty())
    }

    private fun showEmptyView(show: Boolean) {
        if (show) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun readFileContent(fileName: String): String {
        return try {
            val inputStream = openFileInput(fileName)
            val bufferedReader = BufferedReader(InputStreamReader(inputStream))
            val stringBuilder = StringBuilder()
            var line: String?
            while (bufferedReader.readLine().also { line = it } != null) {
                stringBuilder.append(line)
                stringBuilder.append("\n")
            }
            bufferedReader.close()
            stringBuilder.toString()
        } catch (e: Exception) {
            "Error reading file: ${e.message}"
        }
    }

    private fun extractTimestampFromFilename(fileName: String): Long {
        val regex = "transcription_(\\d+).txt".toRegex()
        val matchResult = regex.find(fileName)
        return matchResult?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }

    private fun deleteTranscription(position: Int) {
        val item = transcriptionsList[position]
        val file = File(filesDir, item.fileName)

        if (file.exists() && file.delete()) {
            transcriptionsList.removeAt(position)
            (recyclerView.adapter as TranscriptionAdapter).notifyItemRemoved(position)
            showEmptyView(transcriptionsList.isEmpty())
            Toast.makeText(this, "Transcription deleted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to delete transcription", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("Transcription", text)
        clipboardManager.setPrimaryClip(clipData)
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    // Data class for transcription items
    data class TranscriptionItem(
        val fileName: String,
        val content: String,
        val date: String
    )

    // Adapter for the RecyclerView
    inner class TranscriptionAdapter(private val items: List<TranscriptionItem>) :
        RecyclerView.Adapter<TranscriptionAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val dateText: TextView = view.findViewById(R.id.transcription_date)
            val contentPreview: TextView = view.findViewById(R.id.transcription_preview)
            val expandButton: ImageButton = view.findViewById(R.id.expand_button)
            val copyButton: ImageButton = view.findViewById(R.id.copy_button)
            val deleteButton: ImageButton = view.findViewById(R.id.delete_button)
            val expandedContent: TextView = view.findViewById(R.id.expanded_content)
            val itemContainer: LinearLayout = view.findViewById(R.id.item_container)

            var expanded = false
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.transcription_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]

            holder.dateText.text = item.date

            // Create preview by taking first 100 chars
            val preview = if (item.content.length > 100) {
                item.content.substring(0, 100) + "..."
            } else {
                item.content
            }
            holder.contentPreview.text = preview
            holder.expandedContent.text = item.content

            // Initialize expanded state
            holder.expandedContent.visibility = View.GONE
            holder.expanded = false

            // Set up expand/collapse functionality
            holder.expandButton.setOnClickListener {
                holder.expanded = !holder.expanded
                if (holder.expanded) {
                    holder.expandedContent.visibility = View.VISIBLE
                    holder.expandButton.setImageResource(android.R.drawable.arrow_up_float)
                } else {
                    holder.expandedContent.visibility = View.GONE
                    holder.expandButton.setImageResource(android.R.drawable.arrow_down_float)
                }
            }

            // Set up copy button
            holder.copyButton.setOnClickListener {
                copyToClipboard(item.content)
            }

            // Set up delete button
            holder.deleteButton.setOnClickListener {
                deleteTranscription(position)
            }

            // Set up click listener for the entire item
            holder.itemContainer.setOnClickListener {
                holder.expanded = !holder.expanded
                if (holder.expanded) {
                    holder.expandedContent.visibility = View.VISIBLE
                    holder.expandButton.setImageResource(android.R.drawable.arrow_up_float)
                } else {
                    holder.expandedContent.visibility = View.GONE
                    holder.expandButton.setImageResource(android.R.drawable.arrow_down_float)
                }
            }
        }

        override fun getItemCount() = items.size
    }
}