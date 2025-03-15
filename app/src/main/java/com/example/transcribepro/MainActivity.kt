package com.example.transcribepro

import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Button
import android.widget.TextView
import android.widget.EditText
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.provider.OpenableColumns
import androidx.lifecycle.lifecycleScope
import com.example.transcribepro.transcription.TranscriptionService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var audioButton: Button
    private lateinit var logTextView: TextView
    private lateinit var storageButton: Button
    private lateinit var apiKeyInput: EditText
    private lateinit var audioLauncher: ActivityResultLauncher<String>
    private var selectedAudioUri: Uri? = null
    private lateinit var selectedLanguage: String
    private lateinit var spinner: Spinner
    private lateinit var adapter: ArrayAdapter<Any>
    private lateinit var transcriptionService: TranscriptionService
    private var languages = arrayOf("English", "Russian", "Chinese", "French", "German")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI components
        audioButton = findViewById(R.id.selectAudioButton)
        logTextView = findViewById(R.id.logTextView)
        storageButton = findViewById(R.id.storageButton)
        spinner = findViewById(R.id.language_spinner)
        apiKeyInput = findViewById(R.id.api_key_input)

        // Initialize service
        transcriptionService = TranscriptionService(this)

        // Setup language spinner
        adapter = ArrayAdapter(this, R.layout.spinner_list, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        // Register file picker
        audioLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                selectedAudioUri = it
                audioButton.text = "Choose Language"
                showSpinner()
            }
        }

        // Main button logic
        audioButton.setOnClickListener {
            when (audioButton.text) {
                "Select Audio" -> {
                    audioLauncher.launch("audio/*")
                }
                "Choose Language" -> {
                    audioButton.text = "Transcribe"
                    selectedLanguage = spinner.selectedItem.toString()
                    hideSpinner()
                }
                "Transcribe" -> {
                    startTranscription()
                }
                "Save" -> {
                    val transcription = logTextView.text.toString()
                    saveTranscription(transcription)
                    logTextView.text = "Transcription saved successfully."
                    resetUI()
                }
            }
        }

        // Storage button
        storageButton.setOnClickListener {
            val intent = Intent(this, TranscriptionStorageActivity::class.java)
            startActivity(intent)
        }
    }

    private fun startTranscription() {
        val apiKey = apiKeyInput.text.toString()
        if (apiKey.isBlank()) {
            logTextView.text = "Please enter OpenAI API key"
            logTextView.visibility = View.VISIBLE
            return
        }

        if (selectedAudioUri == null) {
            logTextView.text = "No audio file selected"
            logTextView.visibility = View.VISIBLE
            return
        }

        audioButton.isEnabled = false
        logTextView.visibility = View.VISIBLE
        logTextView.text = "Transcribing... Please wait."

        lifecycleScope.launch {
            try {
                val fileName = getFileName(selectedAudioUri)
                val transcription = selectedAudioUri?.let {
                    transcriptionService.transcribeAudio(it, selectedLanguage, apiKey)
                } ?: "Error: No audio file selected"

                logTextView.text = """
                    File name: $fileName
                    Language: $selectedLanguage
                    
                    $transcription
                """.trimIndent()

                audioButton.text = "Save"
                audioButton.isEnabled = true
            } catch (e: Exception) {
                logTextView.text = "Error: ${e.localizedMessage}"
                resetUI()
                audioButton.isEnabled = true
            }
        }
    }

    private fun resetUI() {
        audioButton.text = "Select Audio"
        selectedAudioUri = null
        hideSpinner()
        apiKeyInput.setText("")
    }

    private fun showSpinner() {
        spinner.visibility = View.VISIBLE
    }

    private fun hideSpinner() {
        spinner.visibility = View.GONE
    }

    private fun getFileName(uri: Uri?): String {
        uri ?: return "Unknown"
        var fileName = "Unknown"
        val contentResolver: ContentResolver = contentResolver
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    fileName = it.getString(index)
                }
            }
        }
        return fileName
    }

    private fun saveTranscription(transcription: String) {
        val fileName = "transcription_${System.currentTimeMillis()}.txt"
        openFileOutput(fileName, MODE_PRIVATE).use {
            it.write(transcription.toByteArray())
        }
    }
}