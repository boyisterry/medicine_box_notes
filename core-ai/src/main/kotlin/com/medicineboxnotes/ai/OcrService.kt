package com.medicineboxnotes.ai

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrResult(val text: String, val blocks: List<OcrBlock>)
data class OcrBlock(val text: String, val confidence: Float?)

interface OcrService { suspend fun recognize(uri: Uri): OcrResult }

class MlKitOcrService(private val context: Context) : OcrService {
    override suspend fun recognize(uri: Uri): OcrResult {
        val image = InputImage.fromFilePath(context, uri)
        val chinese = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = try {
            var chineseFailure: Throwable? = null
            val chineseResult = runCatching { process(chinese, image) }.onFailure { chineseFailure = it }.getOrNull()
            if (!chineseResult?.text.isNullOrBlank()) chineseResult!!
            else runCatching { process(latin, image) }.getOrElse { throw chineseFailure ?: it }
        } finally {
            chinese.close()
            latin.close()
        }
        return OcrResult(result.text, result.textBlocks.map { block ->
            OcrBlock(block.text, block.lines.mapNotNull { it.confidence }.takeIf { it.isNotEmpty() }?.average()?.toFloat())
        })
    }

    private suspend fun process(recognizer: TextRecognizer, image: InputImage): Text = suspendCancellableCoroutine { continuation ->
        recognizer.process(image)
            .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
            .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    }
}
