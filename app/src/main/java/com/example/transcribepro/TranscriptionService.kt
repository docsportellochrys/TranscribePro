package com.example.transcribepro.transcription

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class TranscriptionService(private val context: Context) {
    companion object {
        private const val OPENAI_API_ENDPOINT = "https://api.openai.com/v1/audio/transcriptions"
        private const val MODEL = "whisper-1"
        private const val TIMEOUT = 60L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT, TimeUnit.SECONDS)
        .build()

    init {
        initializePython()
    }

    private fun initializePython() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
    }

    suspend fun transcribeAudio(uri: Uri, language: String, apiKey: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // First try optimized Python-based transcription
                val pythonResult = tryPythonTranscription(uri, language, apiKey)
                if (pythonResult.isNotEmpty()) {
                    return@withContext pythonResult
                }

                // Fall back to direct API call
                directApiTranscription(uri, language, apiKey)
            } catch (e: Exception) {
                e.printStackTrace()
                "Error: ${e.localizedMessage}"
            }
        }
    }

    private suspend fun tryPythonTranscription(uri: Uri, language: String, apiKey: String): String {
        return try {
            val py = Python.getInstance()
            val transcriptionModule = py.getModule("whisper_transcriber")

            // Extract audio file path
            val audioFile = copyUriToTempFile(uri)

            // Call Python function
            val result = transcriptionModule.callAttr(
                "transcribe_audio",
                audioFile.absolutePath,
                language.lowercase(),
                apiKey
            )

            // Clean up temp file
            audioFile.delete()

            result.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            "" // Empty string signals to fall back to direct API call
        }
    }

    private suspend fun directApiTranscription(uri: Uri, language: String, apiKey: String): String {
        return suspendCoroutine { continuation ->
            try {
                val tempFile = copyUriToTempFile(uri)

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        "audio.mp3",
                        tempFile.asRequestBody("audio/mpeg".toMediaTypeOrNull())
                    )
                    .addFormDataPart("model", MODEL)
                    .addFormDataPart("language", language.lowercase())
                    .build()

                val request = Request.Builder()
                    .url(OPENAI_API_ENDPOINT)
                    .header("Authorization", "Bearer $apiKey")
                    .post(requestBody)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        tempFile.delete()
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        tempFile.delete()
                        val responseBody = response.body?.string() ?: ""

                        if (response.isSuccessful) {
                            val jsonObject = JSONObject(responseBody)
                            val text = jsonObject.getString("text")
                            continuation.resume(text)
                        } else {
                            continuation.resumeWithException(
                                IOException("API Error: $responseBody")
                            )
                        }
                    }
                })
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    private fun copyUriToTempFile(uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Failed to open input stream")

        val fileName = DocumentFile.fromSingleUri(context, uri)?.name ?: "audio_file"
        val tempFile = File.createTempFile("audio_", ".tmp", context.cacheDir)

        FileOutputStream(tempFile).use { output ->
            inputStream.use { input ->
                input.copyTo(output)
            }
        }

        return tempFile
    }
}