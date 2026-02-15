package com.voicelike.app

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object LowQualityManager {
    // State
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress

    private val _foundItems = MutableStateFlow<List<Pair<MediaItem, String>>>(emptyList())
    val foundItems: StateFlow<List<Pair<MediaItem, String>>> = _foundItems
    
    // Internal tracking for incremental updates
    private val _newlyScannedIds = MutableStateFlow<Set<Long>>(emptySet())
    val newlyScannedIds: StateFlow<Set<Long>> = _newlyScannedIds

    private var scanJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun startScan(
        context: Context, 
        items: List<MediaItem>, 
        ignoredIds: Set<Long>,
        savedResults: Map<Long, String> = emptyMap(),
        alreadyScannedIds: Set<Long> = emptySet()
    ) {
        if (_isScanning.value) return // Prevent duplicate starts
        
        scanJob?.cancel()

        // Initialize unified analysis
        MediaAnalysisManager.init(context)
        MediaAnalysisManager.startScan(context, items)

        scanJob = scope.launch {
            _isScanning.value = true
            _scanProgress.value = 0f

            // 1. Restore saved results
            val restored = mutableListOf<Pair<MediaItem, String>>()
            val restoredIds = mutableSetOf<Long>()
            
            if (savedResults.isNotEmpty()) {
                val savedItems = items.filter { savedResults.containsKey(it.id) && !ignoredIds.contains(it.id) }
                savedItems.forEach { item ->
                    val reason = savedResults[item.id]
                    if (reason != null) {
                        restored.add(item to reason)
                        restoredIds.add(item.id)
                    }
                }
                if (restored.isNotEmpty()) {
                    _foundItems.value = restored
                }
            }
            
            // 2. Identify Candidates
            val candidates = items.filter { 
                it.type == "photo" && 
                !ignoredIds.contains(it.id) && 
                !restoredIds.contains(it.id) &&
                !alreadyScannedIds.contains(it.id)
            }
            
            val total = candidates.size
            if (total == 0) {
                _scanProgress.value = 1f
                _isScanning.value = false
                return@launch
            }

            val newlyScanned = mutableSetOf<Long>()
            val found = restored.toMutableList()

            while (isActive) {
                var processedCount = 0
                
                for (item in candidates) {
                    if (newlyScanned.contains(item.id)) {
                        processedCount++
                        continue
                    }

                    // A. Static Checks (Fast)
                    val staticReason = checkStaticQuality(item)
                    if (staticReason != null) {
                        found.add(item to staticReason)
                        newlyScanned.add(item.id)
                        _foundItems.value = found.toList()
                        _newlyScannedIds.value = newlyScanned.toSet()
                        processedCount++
                        continue
                    }

                    // B. Analysis Checks (Wait for MediaAnalysisManager)
                    val analysis = MediaAnalysisManager.getAnalysis(item.id)
                    if (analysis != null) {
                        val reason = checkAnalysisQuality(analysis, item)
                        if (reason != null) {
                            found.add(item to reason)
                            _foundItems.value = found.toList()
                        }
                        newlyScanned.add(item.id)
                        _newlyScannedIds.value = newlyScanned.toSet()
                        processedCount++
                    }
                }
                
                _scanProgress.value = processedCount.toFloat() / total.toFloat()

                if (processedCount == total) break
                
                // If Unified Manager finished but we still have missing items (e.g. analysis failed), break to avoid infinite loop
                if (!MediaAnalysisManager.isScanning.value && processedCount < total) {
                    // Give it a moment, then break
                    delay(500)
                    // Check if any new analysis appeared?
                    // If not, just stop.
                     break
                }
                
                delay(500) // Poll interval
            }
            
            _isScanning.value = false
            _scanProgress.value = 1f
        }
    }

    fun stopScan() {
        if (_isScanning.value) {
            scanJob?.cancel()
            _isScanning.value = false
        }
    }

    fun removeResult(id: Long) {
        val current = _foundItems.value
        _foundItems.value = current.filter { it.first.id != id }
    }

    fun restoreResult(item: MediaItem, reason: String) {
        val current = _foundItems.value
        _foundItems.value = listOf(item to reason) + current
    }

    private fun checkStaticQuality(item: MediaItem): String? {
        // Small File (< 50KB)
        if (item.size < 50 * 1024) return "smallFile"

        // Low Resolution (< 0.5 MP)
        if (item.width > 0 && item.height > 0) {
            val pixels = item.width * item.height
            if (pixels < 500_000) return "lowResolution"
        }
        return null
    }

    private fun checkAnalysisQuality(analysis: MediaAnalysisManager.AnalysisResult, item: MediaItem): String? {
        // 1. Solid Color
        if (analysis.solidColorStdDev < 5.0) return "solidColor"

        // 2. Screenshot Detection (Skip exposure/blur checks)
        val isScreenshot = item.displayName.contains("Screenshot", ignoreCase = true) ||
                           item.displayName.contains("截屏", ignoreCase = true) ||
                           item.bucketName.contains("Screenshot", ignoreCase = true) ||
                           item.bucketName.contains("截屏", ignoreCase = true)

        if (!isScreenshot) {
            // 3. Exposure
            if (analysis.exposureAvg < 40) return "underexposed"
            if (analysis.exposureAvg > 220) return "overexposed"
            
            // 4. Blur
            // Using variance of Laplacian. Threshold 100.
            if (analysis.blurVariance < 100) return "blur"
        }

        // 5. Closed Eyes
        if (analysis.hasClosedEyes) return "closedEyes"

        return null
    }
}
