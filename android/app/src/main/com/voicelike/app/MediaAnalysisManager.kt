package com.voicelike.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import coil.decode.VideoFrameDecoder
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/**
 * Unified Media Analysis Manager.
 * Performs single-pass analysis for Similar Photos, Low Quality, and Smart Organize.
 * This centralized service reduces resource usage by loading bitmaps once and running all checks.
 */
object MediaAnalysisManager {
    private const val CLOSED_EYE_PROB_THRESHOLD = 0.15f
    private const val MIN_FACE_SIZE_FOR_CLOSED_EYE = 0.04f

    data class AnalysisResult(
        val id: Long,
        val dHash: Long = 0,
        val blurVariance: Double = 0.0,
        val exposureAvg: Int = 128,
        val solidColorStdDev: Double = 100.0,
        val faceCount: Int = 0,
        val maxFaceSize: Float = 0f,
        val hasClosedEyes: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class PrefetchState(
        val sizes: Map<Long, Long> = emptyMap(),
        val videoThumbs: Set<Long> = emptySet()
    )

    private const val CACHE_FILE = "media_analysis_cache.json"
    private const val PREFETCH_CACHE_FILE = "media_prefetch_cache.json"
    private val gson = Gson()
    
    // In-memory cache
    private val analysisCache = ConcurrentHashMap<Long, AnalysisResult>()
    private val sizeCache = ConcurrentHashMap<Long, Long>()
    private val videoThumbCache = ConcurrentHashMap<Long, Boolean>()
    private var isCacheLoaded = false
    private var isPrefetchLoaded = false
    
    // State
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()
    
    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()
    
    private val _scanRatePerMinute = MutableStateFlow(0f)
    val scanRatePerMinute = _scanRatePerMinute.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var scanJob: Job? = null
    private var scanStartTimeMs: Long = 0L
    private var lastRateUpdateMs: Long = 0L
    private var smoothedRatePerMinute: Float = 0f

    fun init(context: Context) {
        if (!isCacheLoaded) {
            scope.launch(Dispatchers.IO) {
                loadCache(context)
            }
        }
        if (!isPrefetchLoaded) {
            scope.launch(Dispatchers.IO) {
                loadPrefetchCache(context)
            }
        }
    }

    private fun loadCache(context: Context) {
        try {
            val file = File(context.filesDir, CACHE_FILE)
            if (file.exists()) {
                val json = file.readText()
                val type = object : TypeToken<Map<Long, AnalysisResult>>() {}.type
                val loaded: Map<Long, AnalysisResult> = gson.fromJson(json, type)
                analysisCache.putAll(loaded)
            }
            isCacheLoaded = true
        } catch (e: Exception) {
            Log.e("MediaAnalysis", "Error loading cache", e)
        }
    }

    private fun saveCache(context: Context) {
        try {
            val file = File(context.filesDir, CACHE_FILE)
            val json = gson.toJson(analysisCache)
            file.writeText(json)
        } catch (e: Exception) {
            Log.e("MediaAnalysis", "Error saving cache", e)
        }
    }

    private fun loadPrefetchCache(context: Context) {
        try {
            val file = File(context.filesDir, PREFETCH_CACHE_FILE)
            if (file.exists()) {
                val json = file.readText()
                val type = object : TypeToken<PrefetchState>() {}.type
                val loaded: PrefetchState = gson.fromJson(json, type)
                sizeCache.putAll(loaded.sizes)
                loaded.videoThumbs.forEach { videoThumbCache[it] = true }
            }
            isPrefetchLoaded = true
        } catch (e: Exception) {
            Log.e("MediaAnalysis", "Error loading prefetch cache", e)
        }
    }

    private fun savePrefetchCache(context: Context) {
        try {
            val file = File(context.filesDir, PREFETCH_CACHE_FILE)
            val state = PrefetchState(
                sizes = sizeCache.toMap(),
                videoThumbs = videoThumbCache.keys.toSet()
            )
            val json = gson.toJson(state)
            file.writeText(json)
        } catch (e: Exception) {
            Log.e("MediaAnalysis", "Error saving prefetch cache", e)
        }
    }

    fun getAnalysis(id: Long): AnalysisResult? {
        return analysisCache[id]
    }

    fun getEffectiveSize(item: MediaItem): Long {
        val cached = sizeCache[item.id]
        if (cached == null || cached <= 0L) return item.size
        return maxOf(item.size, cached)
    }

    suspend fun warmVideoThumbs(context: Context, items: List<MediaItem>, maxCount: Int = 40) {
        withContext(Dispatchers.IO) {
            items.asSequence()
                .filter { it.type == "video" }
                .take(maxCount)
                .forEach { prefetchVideoThumb(context, it) }
        }
    }

    fun getLowQualityCount(): Int {
        return analysisCache.values.count { 
            // Criteria for Low Quality:
            // Blur > 500 (approx), or Exposure < 50 or > 200, or Closed Eyes
            // Need to match LowQualityView logic
            // Assuming standard thresholds:
            val isBlurry = it.blurVariance < 100.0 
            val isDark = it.exposureAvg < 40
            val isBright = it.exposureAvg > 215
            val isSolid = it.solidColorStdDev < 10.0
            
            isBlurry || isDark || isBright || it.hasClosedEyes || isSolid
        }
    }

    /**
     * Starts a background scan for all media items.
     * Skips items that are already analyzed.
     */
    fun startScan(context: Context, items: List<MediaItem>) {
        if (_isScanning.value) return
        
        scanJob = scope.launch {
            // Set lowest thread priority for minimal impact
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            
            _isScanning.value = true
            scanStartTimeMs = System.currentTimeMillis()
            lastRateUpdateMs = scanStartTimeMs
            smoothedRatePerMinute = 0f
            _scanRatePerMinute.value = 0f
            
            if (!isCacheLoaded) loadCache(context)
            if (!isPrefetchLoaded) loadPrefetchCache(context)
            
            val photos = items.filter { it.type == "photo" }
            val total = photos.size
            if (total == 0) {
                _isScanning.value = false
                _progress.value = 1f
                return@launch
            }
            
            // Calculate initial progress based on cache
            val initialAnalyzed = photos.count { analysisCache.containsKey(it.id) }
            _progress.value = initialAnalyzed.toFloat() / total
            
            val toAnalyzeIds = photos.filter { !analysisCache.containsKey(it.id) }.map { it.id }.toSet()
            val needsPrefetch = items.any { item ->
                !sizeCache.containsKey(item.id) || (item.type == "video" && !videoThumbCache.containsKey(item.id))
            }
            if (toAnalyzeIds.isEmpty() && !needsPrefetch) {
                _isScanning.value = false
                _progress.value = 1f
                return@launch
            }

            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()
            val detector = FaceDetection.getClient(options)

            try {
                var analyzedCount = 0
                val prefetchJob = if (needsPrefetch) {
                    scope.launch(Dispatchers.IO) {
                        var prefetchedCount = 0
                        for (item in items) {
                            if (!isActive) break
                            val prefetched = ensurePrefetchForItem(context, item)
                            if (prefetched) {
                                prefetchedCount++
                                if (prefetchedCount % 40 == 0) {
                                    savePrefetchCache(context)
                                }
                            }
                        }
                    }
                } else {
                    null
                }

                val photoItems = photos.filter { toAnalyzeIds.contains(it.id) }
                if (photoItems.isNotEmpty()) {
                    val coreCount = Runtime.getRuntime().availableProcessors()
                    val parallelism = (coreCount / 2).coerceIn(1, 3)
                    val dispatcher = Dispatchers.Default.limitedParallelism(parallelism)
                    val batchSize = (parallelism * 4).coerceAtLeast(4)
                    for (chunk in photoItems.chunked(batchSize)) {
                        if (!isActive) break
                        val batchStart = System.currentTimeMillis()
                        val results = chunk.map { item ->
                            async(dispatcher) { item to analyzeItem(context, item, detector) }
                        }.awaitAll()
                        for ((item, result) in results) {
                            if (result != null) {
                                analysisCache[item.id] = result
                            }
                            analyzedCount++
                            _progress.value = (initialAnalyzed + analyzedCount).toFloat() / total
                            val now = System.currentTimeMillis()
                            if (now - lastRateUpdateMs >= 1000L) {
                                val elapsedMinutes = maxOf(0.25f, (now - scanStartTimeMs) / 60000f)
                                val rawRate = analyzedCount / elapsedMinutes
                                smoothedRatePerMinute = if (smoothedRatePerMinute == 0f) {
                                    rawRate
                                } else {
                                    smoothedRatePerMinute * 0.7f + rawRate * 0.3f
                                }
                                _scanRatePerMinute.value = smoothedRatePerMinute
                                lastRateUpdateMs = now
                            }
                            if (analyzedCount % 20 == 0) {
                                saveCache(context)
                            }
                        }
                        val batchElapsed = System.currentTimeMillis() - batchStart
                        val targetPerItemMs = if (parallelism > 1) 28L else 40L
                        val targetBatchMs = targetPerItemMs * chunk.size
                        val extraDelay = (targetBatchMs - batchElapsed).coerceAtLeast(0L).coerceAtMost(120L)
                        if (extraDelay > 0L) {
                            delay(extraDelay)
                        }
                    }
                }
                prefetchJob?.join()
                saveCache(context)
                savePrefetchCache(context)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                detector.close()
                _isScanning.value = false
                _progress.value = 1f
                _scanRatePerMinute.value = 0f
            }
        }
    }
    
    fun stopScan() {
        scanJob?.cancel()
        _isScanning.value = false
    }

    private suspend fun ensurePrefetchForItem(context: Context, item: MediaItem): Boolean {
        var updated = false
        if (!sizeCache.containsKey(item.id)) {
            val computed = computeMediaSizeBytes(context, item)
            sizeCache[item.id] = if (computed > 0) computed else item.size
            updated = true
        }
        if (item.type == "video" && !videoThumbCache.containsKey(item.id)) {
            val ok = prefetchVideoThumb(context, item)
            if (ok) {
                videoThumbCache[item.id] = true
                updated = true
            }
        }
        return updated
    }

    private suspend fun computeMediaSizeBytes(context: Context, item: MediaItem): Long {
        return withContext(Dispatchers.IO) {
            if (item.type != "photo") return@withContext item.size
            if (!item.isLivePhoto) return@withContext item.size
            try {
                context.contentResolver.query(
                    item.uri,
                    arrayOf(android.provider.MediaStore.Images.Media.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val idx = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.SIZE)
                    if (cursor.moveToFirst()) cursor.getLong(idx) else item.size
                } ?: item.size
            } catch (_: Exception) {
                item.size
            }
        }
    }

    private suspend fun prefetchVideoThumb(context: Context, item: MediaItem): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(item.uri)
                    .decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
                    .size(256)
                    .memoryCacheKey("video_thumb_${item.id}_list")
                    .diskCacheKey("video_thumb_${item.id}_list")
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request)
                result is SuccessResult
            } catch (_: Exception) {
                false
            }
        }
    }

    private suspend fun analyzeItem(context: Context, item: MediaItem, detector: FaceDetector): AnalysisResult? {
        return withContext(Dispatchers.IO) {
            var bitmap: Bitmap? = null
            var scaled100: Bitmap? = null
            var scaled9x8: Bitmap? = null
            
            try {
                // 1. Load Subsampled Bitmap (approx 500-1000px)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 4 // Start with subsample 4
                    inJustDecodeBounds = false
                }
                
                context.contentResolver.openInputStream(item.uri)?.use { stream ->
                    bitmap = BitmapFactory.decodeStream(stream, null, options)
                }

                if (bitmap == null) return@withContext null

                // 2. Compute dHash (Requires 9x8)
                scaled9x8 = Bitmap.createScaledBitmap(bitmap!!, 9, 8, true)
                val dHash = dHash(scaled9x8!!)
                
                // 3. Compute Stats (Requires approx 100x100 for speed)
                scaled100 = Bitmap.createScaledBitmap(bitmap!!, 100, 100, true)
                val solidStdDev = getSolidColorStdDev(scaled100!!)
                val exposure = getAverageLuminance(scaled100!!)
                val blurVar = getBlurVariance(scaled100!!)
                
                // 4. Face Detection (Use the 'bitmap' which is subsampled but large enough)
                val (faces, maxFaceSize, closedEyes) = detectFaces(bitmap!!, detector)
                
                AnalysisResult(
                    id = item.id,
                    dHash = dHash,
                    blurVariance = blurVar,
                    exposureAvg = exposure,
                    solidColorStdDev = solidStdDev,
                    faceCount = faces,
                    maxFaceSize = maxFaceSize,
                    hasClosedEyes = closedEyes
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                scaled9x8?.recycle()
                scaled100?.recycle()
                bitmap?.recycle()
            }
        }
    }

    // --- Helpers ---

    private fun dHash(bitmap: Bitmap): Long {
        var hash = 0L
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = bitmap.getPixel(x, y)
                val right = bitmap.getPixel(x + 1, y)
                val leftGray = (android.graphics.Color.red(left) + android.graphics.Color.green(left) + android.graphics.Color.blue(left)) / 3
                val rightGray = (android.graphics.Color.red(right) + android.graphics.Color.green(right) + android.graphics.Color.blue(right)) / 3
                
                if (leftGray > rightGray) {
                    hash = hash or (1L shl (y * 8 + x))
                }
            }
        }
        return hash
    }
    
    private fun getAverageLuminance(bitmap: Bitmap): Int {
        var totalLum = 0L
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            totalLum += (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }
        return (totalLum / pixels.size).toInt()
    }
    
    private fun getSolidColorStdDev(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var sum = 0L
        var sqSum = 0L
        val count = pixels.size

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            sum += lum
            sqSum += (lum * lum).toLong()
        }

        val mean = sum.toDouble() / count
        val variance = (sqSum.toDouble() / count) - (mean * mean)
        return sqrt(variance)
    }

    private fun getBlurVariance(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = IntArray(width * height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }
        
        var variance = 0.0
        var mean = 0.0
        var count = 0
        val laplacian = IntArray((width - 2) * (height - 2))
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val val0 = gray[idx]
                val val1 = gray[idx - 1]
                val val2 = gray[idx + 1]
                val val3 = gray[idx - width]
                val val4 = gray[idx + width]
                
                val lap = val1 + val2 + val3 + val4 - 4 * val0
                laplacian[count++] = lap
                mean += lap
            }
        }
        
        mean /= count
        for (l in laplacian) {
            variance += (l - mean) * (l - mean)
        }
        return variance / count
    }

    private suspend fun detectFaces(bitmap: Bitmap, detector: FaceDetector): Triple<Int, Float, Boolean> {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val faces = detector.process(image).await()
            
            if (faces.isEmpty()) return Triple(0, 0f, false)
            
            var hasClosedEyes = false
            var maxFaceSize = 0f
            val imageArea = (bitmap.width * bitmap.height).toFloat()
            
            for (face in faces) {
                val faceArea = (face.boundingBox.width() * face.boundingBox.height()).toFloat()
                val size = if (imageArea > 0) faceArea / imageArea else 0f
                if (size > maxFaceSize) maxFaceSize = size

                val leftOpen = face.leftEyeOpenProbability
                val rightOpen = face.rightEyeOpenProbability
                if (leftOpen != null && rightOpen != null) {
                    if (size >= MIN_FACE_SIZE_FOR_CLOSED_EYE && leftOpen < CLOSED_EYE_PROB_THRESHOLD && rightOpen < CLOSED_EYE_PROB_THRESHOLD) {
                        hasClosedEyes = true
                    }
                }
            }
            
            Triple(faces.size, maxFaceSize, hasClosedEyes)
        } catch (e: Exception) {
            Log.e("MediaAnalysis", "Face detection failed", e)
            Triple(0, 0f, false)
        }
    }
}
