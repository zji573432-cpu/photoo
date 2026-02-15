package com.voicelike.app

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Standalone Image Analyzer using ML Kit.
 * Handles on-device image labeling with simple localization mapping.
 */
object ImageAnalyzer {
    
    // Loaded from assets
    private var labelMap: Map<String, Map<String, String>>? = null

    private fun ensureMapLoaded(context: Context) {
        if (labelMap != null) return
        
        try {
            android.util.Log.d("ImageAnalyzer", "Loading imagenet_labels_localized.json...")
            val json = context.assets.open("imagenet_labels_localized.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<Map<String, Map<String, String>>>() {}.type
            labelMap = Gson().fromJson(json, type)
            android.util.Log.d("ImageAnalyzer", "Loaded label map with ${labelMap?.size} entries")
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("ImageAnalyzer", "Error loading label map", e)
            labelMap = emptyMap() // Fallback
        }
    }

    suspend fun analyze(context: Context, uri: Uri, langCode: String = "en"): List<String> {
        return withContext(Dispatchers.IO) {
            ensureMapLoaded(context)
            try {
                android.util.Log.d("ImageAnalyzer", "Analyzing with langCode: $langCode")
                val image = InputImage.fromFilePath(context, uri)
                val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                
                val labels = labeler.process(image).await()
                
                // Filter and map labels
                labels.filter { it.confidence > 0.75f } // Higher threshold to avoid low confidence results
                      .take(3) // Top 3 only
                      .map { label ->
                          val text = label.text
                          val lowerText = text.lowercase(Locale.ROOT)
                          
                          // Try exact match, then lowercase match
                          // Default to English text if no mapping found
                          if (langCode == "en") {
                              text
                          } else {
                              // Try to find key in map (case insensitive)
                              val translations = labelMap?.get(lowerText)
                              val mapped = translations?.get(langCode)
                              
                              android.util.Log.d("ImageAnalyzer", "Label: '$text' -> '$lowerText', Found in map: ${translations != null}, Translation for $langCode: $mapped")
                              
                              mapped ?: text // Fallback to original English text
                          }
                      }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
}
