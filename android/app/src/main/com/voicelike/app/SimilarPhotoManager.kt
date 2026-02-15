package com.voicelike.app

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Handles detection of similar photos using a perceptual hash (dHash) algorithm.
 * Delegates analysis to [MediaAnalysisManager] for unified processing.
 */
object SimilarPhotoManager {

    // Threshold for similarity (Hamming distance). Lower = more strict.
    private const val SIMILARITY_THRESHOLD = 7
    
    // Time window for comparison: 2 minutes
    private const val TIME_WINDOW_SEC = 120 

    // State
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress = _scanProgress.asStateFlow()

    private val _foundGroups = MutableStateFlow<List<List<MediaItem>>>(emptyList())
    val foundGroups = _foundGroups.asStateFlow()
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Incremental update control
    private var lastUpdateTimestamp = 0L
    private val UPDATE_INTERVAL_MS = 2000L // Update every 2 seconds
    private var isComputingGroups = false
    
    // Track ignored IDs (processed or deleted) during the session
    private val currentIgnoredIds = java.util.Collections.synchronizedSet(HashSet<Long>())

    fun init(context: Context) {
        MediaAnalysisManager.init(context)
    }
    
    fun addIgnoredIds(ids: Set<Long>) {
        currentIgnoredIds.addAll(ids)
    }

    /**
     * Starts a background scan via MediaAnalysisManager.
     */
    fun startScan(context: Context, allMedia: List<MediaItem>, ignoredIds: Set<Long> = emptySet()) {
        if (_isScanning.value) return
        
        // Reset and init ignored IDs
        currentIgnoredIds.clear()
        currentIgnoredIds.addAll(ignoredIds)
        
        // 1. Trigger unified scan
        MediaAnalysisManager.startScan(context, allMedia)
        
        // 2. Observe status
        scope.launch {
            MediaAnalysisManager.isScanning.collect { scanning ->
                _isScanning.value = scanning
                if (!scanning) {
                    // Scan finished, final update
                    updateGroups(allMedia, currentIgnoredIds.toSet(), force = true)
                    _scanProgress.value = 1.0f
                }
            }
        }
        
        // 3. Observe progress
        scope.launch {
            MediaAnalysisManager.progress.collect { p ->
                _scanProgress.value = p
                // Incremental update
                if (p > 0 && p < 1.0f) {
                    updateGroups(allMedia, currentIgnoredIds.toSet())
                }
            }
        }
    }

    private fun updateGroups(allMedia: List<MediaItem>, ignoredIds: Set<Long>, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastUpdateTimestamp < UPDATE_INTERVAL_MS || isComputingGroups)) {
            return
        }

        isComputingGroups = true
        scope.launch(Dispatchers.Default) {
            try {
                val groups = findSimilarGroupsGlobal(allMedia, ignoredIds)
                if (groups.isNotEmpty() || force) {
                    _foundGroups.value = groups
                }
                lastUpdateTimestamp = System.currentTimeMillis()
            } finally {
                isComputingGroups = false
            }
        }
    }

    private fun findSimilarGroupsGlobal(sortedPhotos: List<MediaItem>, ignoredIds: Set<Long>): List<List<MediaItem>> {
        val groups = mutableListOf<List<MediaItem>>()
        val visited = mutableSetOf<Long>()
        visited.addAll(ignoredIds)
        
        // Ensure sorted
        val photos = sortedPhotos.filter { it.type == "photo" }.sortedByDescending { it.dateAdded }
        
        for (i in photos.indices) {
            val current = photos[i]
            if (visited.contains(current.id)) continue
            
            val currentHash = MediaAnalysisManager.getAnalysis(current.id)?.dHash ?: continue
            if (currentHash == 0L) continue
            
            for (j in i + 1 until photos.size) {
                val next = photos[j]
                if (visited.contains(next.id)) continue
                
                if (abs(current.dateAdded - next.dateAdded) > TIME_WINDOW_SEC) {
                    break 
                }
                
                val nextHash = MediaAnalysisManager.getAnalysis(next.id)?.dHash ?: continue
                if (hammingDistance(currentHash, nextHash) <= SIMILARITY_THRESHOLD) {
                    
                    val isLiveVsStatic = current.isLivePhoto != next.isLivePhoto
                    val isSameMoment = abs(current.dateAdded - next.dateAdded) <= 2
                    
                    if (isLiveVsStatic && isSameMoment) {
                        continue
                    }

                    groups.add(listOf(current, next))
                    visited.add(current.id)
                    visited.add(next.id)
                    break
                }
            }
        }
        return groups
    }

    private fun hammingDistance(hash1: Long, hash2: Long): Int {
        return java.lang.Long.bitCount(hash1 xor hash2)
    }

    suspend fun findSimilarForItems(
        context: Context, 
        targets: List<MediaItem>, 
        allSorted: List<MediaItem>
    ): Map<Long, List<MediaItem>> = withContext(Dispatchers.Default) {
        val results = mutableMapOf<Long, List<MediaItem>>()
        val photosSorted = allSorted.filter { it.type == "photo" }
        
        for (target in targets) {
            if (target.type != "photo") continue
            
            val targetHash = MediaAnalysisManager.getAnalysis(target.id)?.dHash ?: continue
            if (targetHash == 0L) continue

            val minTime = target.dateAdded - TIME_WINDOW_SEC
            val maxTime = target.dateAdded + TIME_WINDOW_SEC
            val candidates = photosSorted.filter { it.dateAdded in minTime..maxTime }
            
            val similarGroup = mutableListOf<MediaItem>()
            for (candidate in candidates) {
                if (candidate.id == target.id) continue
                
                val candidateHash = MediaAnalysisManager.getAnalysis(candidate.id)?.dHash ?: continue
                if (hammingDistance(targetHash, candidateHash) <= SIMILARITY_THRESHOLD) {
                    similarGroup.add(candidate)
                }
            }
            
            if (similarGroup.isNotEmpty()) {
                results[target.id] = listOf(target) + similarGroup
            }
        }
        results
    }
    
    fun removeProcessedItems(processedIds: Set<Long>) {
        if (processedIds.isEmpty()) return
        val currentGroups = _foundGroups.value
        val newGroups = currentGroups.filter { group ->
            group.none { processedIds.contains(it.id) }
        }
        if (newGroups.size != currentGroups.size) {
            _foundGroups.value = newGroups
        }
    }
    
    suspend fun findSimilarPhotos(context: Context, items: List<MediaItem>): List<List<MediaItem>> {
        return emptyList() 
    }
}
