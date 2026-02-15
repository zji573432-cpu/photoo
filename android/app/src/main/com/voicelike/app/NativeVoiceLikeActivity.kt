package com.voicelike.app

import android.view.Display
import android.view.WindowManager
import androidx.core.view.WindowCompat
import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ClipData
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.TextureView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.common.VideoSize
import androidx.media3.common.MediaItem as ExoMediaItem
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.decode.VideoFrameDecoder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import android.location.Geocoder
import java.util.Locale
import java.io.File
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults

// --- Data Models ---
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val type: String, // "video" or "photo"
    val dateAdded: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val displayName: String = "",
    val resolution: String = "",
    val camera: String = "--",
    val iso: String = "--",
    val shutter: String = "--",
    val focalLength: String = "--",
    val aperture: String = "--",
    val location: String = "Unknown Location",
    val isLivePhoto: Boolean = false,
    val livePhotoVideoUri: Uri? = null,
    val livePhotoVideoId: Long? = null,
    val path: String?, // Added path to support undo move
    val bucketName: String = "", // Added folder name
    val duration: Long = 0L // Duration in ms
)

data class AppStats(
    val processed: Int = 0,
    val trashed: Int = 0,
    val skipped: Int = 0,
    val savedSize: Long = 0L
)

data class CompressionResumeState(
    val queueIds: List<Long> = emptyList(),
    val doneIds: List<Long> = emptyList()
)

// --- Persistence Helper ---
class PreferencesManager(context: Context) {
    private val prefs = context.getSharedPreferences("photoo_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // In-memory cache to avoid expensive JSON parsing on every access
    private var _stats: AppStats? = null
    private var _processedIds: MutableSet<Long>? = null
    private var _pendingTrashIds: MutableSet<Long>? = null
    private var _likedIds: MutableSet<Long>? = null
    private var _comments: MutableMap<Long, List<String>>? = null // Comments cache
    private var _lowQualityResults: MutableMap<Long, String>? = null
    private var _language: AppLanguage? = null
    private var _muteVideos: Boolean? = null
    private var _hapticsEnabled: Boolean? = null
    private var _danmakuEnabled: Boolean? = null
    private var _videoCompressBitrateKey: String? = null
    private var _imageCompressThresholdMb: Int? = null
    private var _imageCompressQualityKey: String? = null
    private var _imageCompressResume: CompressionResumeState? = null
    private var _videoCompressResume: CompressionResumeState? = null
    private var _onboardingCompleted: Boolean? = null

    var stats: AppStats
        get() {
            if (_stats == null) {
                val json = prefs.getString("stats", null)
                _stats = try {
                    if (json != null) gson.fromJson(json, AppStats::class.java) else AppStats()
                } catch (e: Exception) {
                    AppStats()
                }
            }
            return _stats!!
        }
        set(value) {
            _stats = value
            prefs.edit().putString("stats", gson.toJson(value)).apply()
        }

    fun reloadStats() {
        _stats = null
    }

    var processedIds: MutableSet<Long>
        get() {
            if (_processedIds == null) {
                val json = prefs.getString("processed_ids", null)
                val type = object : TypeToken<HashSet<Long>>() {}.type
                _processedIds = try {
                    if (json != null) gson.fromJson(json, type) else HashSet()
                } catch (e: Exception) {
                    HashSet()
                }
            }
            return _processedIds!!
        }
        set(value) {
            _processedIds = value
            prefs.edit().putString("processed_ids", gson.toJson(value)).apply()
        }

    var pendingTrashIds: MutableSet<Long>
        get() {
            if (_pendingTrashIds == null) {
                val json = prefs.getString("pending_trash", null)
                val type = object : TypeToken<HashSet<Long>>() {}.type
                _pendingTrashIds = try {
                    if (json != null) gson.fromJson(json, type) else HashSet()
                } catch (e: Exception) {
                    HashSet()
                }
            }
            return _pendingTrashIds!!
        }
        set(value) {
            _pendingTrashIds = value
            prefs.edit().putString("pending_trash", gson.toJson(value)).apply()
        }

    var likedIds: MutableSet<Long>
        get() {
            if (_likedIds == null) {
                val json = prefs.getString("liked_ids", null)
                val type = object : TypeToken<HashSet<Long>>() {}.type
                _likedIds = try {
                    if (json != null) gson.fromJson(json, type) else HashSet()
                } catch (e: Exception) {
                    HashSet()
                }
            }
            return _likedIds!!
        }
        set(value) {
            _likedIds = value
            prefs.edit().putString("liked_ids", gson.toJson(value)).apply()
        }

    // New: Track moved items to prevent them from reappearing on Reset Filter
    private var _movedIds: MutableSet<Long>? = null
    var movedIds: MutableSet<Long>
        get() {
            if (_movedIds == null) {
                val json = prefs.getString("moved_ids", null)
                val type = object : TypeToken<HashSet<Long>>() {}.type
                _movedIds = try {
                    if (json != null) gson.fromJson(json, type) else HashSet()
                } catch (e: Exception) {
                    HashSet()
                }
            }
            return _movedIds!!
        }
        set(value) {
            _movedIds = value
            prefs.edit().putString("moved_ids", gson.toJson(value)).apply()
        }

    // New: Track user created folders
    private var _userCreatedFolders: MutableSet<String>? = null
    var userCreatedFolders: MutableSet<String>
        get() {
            if (_userCreatedFolders == null) {
                val json = prefs.getString("user_created_folders", null)
                val type = object : TypeToken<HashSet<String>>() {}.type
                _userCreatedFolders = try {
                    if (json != null) gson.fromJson(json, type) else HashSet()
                } catch (e: Exception) {
                    HashSet()
                }
            }
            return _userCreatedFolders!!
        }
        set(value) {
            _userCreatedFolders = value
            prefs.edit().putString("user_created_folders", gson.toJson(value)).apply()
        }

    var lowQualityResults: MutableMap<Long, String>
        get() {
            if (_lowQualityResults == null) {
                val json = prefs.getString("low_quality_results", null)
                val type = object : TypeToken<HashMap<Long, String>>() {}.type
                _lowQualityResults = try {
                    if (json != null) gson.fromJson(json, type) else HashMap()
                } catch (e: Exception) {
                    HashMap()
                }
            }
            return _lowQualityResults!!
        }
        set(value) {
            _lowQualityResults = value
            prefs.edit().putString("low_quality_results", gson.toJson(value)).apply()
        }

    // New: Track already scanned IDs to support incremental scanning
    private var _scannedLowQualityIds: MutableSet<Long>? = null
    var scannedLowQualityIds: MutableSet<Long>
        get() {
            if (_scannedLowQualityIds == null) {
                val json = prefs.getString("scanned_low_quality_ids", null)
                val type = object : TypeToken<HashSet<Long>>() {}.type
                _scannedLowQualityIds = try {
                    if (json != null) gson.fromJson(json, type) else HashSet()
                } catch (e: Exception) {
                    HashSet()
                }
            }
            return _scannedLowQualityIds!!
        }
        set(value) {
            _scannedLowQualityIds = value
            prefs.edit().putString("scanned_low_quality_ids", gson.toJson(value)).apply()
        }
        


    // New: Folder Usage Tracking
    private var _folderUsage: MutableMap<String, Int>? = null // Cache for folder usage
    var folderUsage: MutableMap<String, Int>
        get() {
            if (_folderUsage == null) {
                val json = prefs.getString("folder_usage", null)
                val type = object : TypeToken<HashMap<String, Int>>() {}.type
                _folderUsage = try {
                    if (json != null) gson.fromJson(json, type) else HashMap()
                } catch (e: Exception) {
                    HashMap()
                }
            }
            return _folderUsage!!
        }
        set(value) {
            _folderUsage = value
            prefs.edit().putString("folder_usage", gson.toJson(value)).apply()
        }

    // New: Folder Last Used Timestamp (for scientific sorting)
    private var _folderLastUsed: MutableMap<String, Long>? = null
    var folderLastUsed: MutableMap<String, Long>
        get() {
            if (_folderLastUsed == null) {
                val json = prefs.getString("folder_last_used", null)
                val type = object : TypeToken<HashMap<String, Long>>() {}.type
                _folderLastUsed = try {
                    if (json != null) gson.fromJson(json, type) else HashMap()
                } catch (e: Exception) {
                    HashMap()
                }
            }
            return _folderLastUsed!!
        }
        set(value) {
            _folderLastUsed = value
            prefs.edit().putString("folder_last_used", gson.toJson(value)).apply()
        }

    // New: Likes View Folder Selection Counts
    private var _likesFolderSelectionCounts: MutableMap<String, Int>? = null
    var likesFolderSelectionCounts: MutableMap<String, Int>
        get() {
            if (_likesFolderSelectionCounts == null) {
                val json = prefs.getString("likes_folder_selection_counts", null)
                val type = object : TypeToken<HashMap<String, Int>>() {}.type
                _likesFolderSelectionCounts = try {
                    if (json != null) gson.fromJson(json, type) else HashMap()
                } catch (e: Exception) {
                    HashMap()
                }
            }
            return _likesFolderSelectionCounts!!
        }
        set(value) {
            _likesFolderSelectionCounts = value
            prefs.edit().putString("likes_folder_selection_counts", gson.toJson(value)).apply()
        }

    // New: Smart Albums Persistence
    data class SmartAlbumDefinition(
        val id: String,
        val name: String,
        val rules: AlbumRules
    )
    
    data class DateRange(
        val start: Long,
        val end: Long
    )
    
    data class AlbumRules(
        val dateRange: DateRange? = null, // Start, End timestamp
        val location: String? = null,
        val keywords: List<String> = emptyList() // e.g. "Christmas", "Portrait"
    )

    private var _smartAlbums: MutableList<SmartAlbumDefinition>? = null
    var smartAlbums: MutableList<SmartAlbumDefinition>
        get() {
            if (_smartAlbums == null) {
                val json = prefs.getString("smart_albums", null)
                val type = object : TypeToken<ArrayList<SmartAlbumDefinition>>() {}.type
                _smartAlbums = try {
                    if (json != null) gson.fromJson(json, type) else ArrayList()
                } catch (e: Exception) {
                    ArrayList()
                }
            }
            return _smartAlbums!!
        }
        set(value) {
            _smartAlbums = value
            prefs.edit().putString("smart_albums", gson.toJson(value)).apply()
        }

    var danmakuEnabled: Boolean
        get() {
            if (_danmakuEnabled == null) {
                _danmakuEnabled = prefs.getBoolean("danmaku_enabled", false)
            }
            return _danmakuEnabled!!
        }
        set(value) {
            _danmakuEnabled = value
            prefs.edit().putBoolean("danmaku_enabled", value).apply()
        }

    var videoCompressBitrateKey: String
        get() {
            if (_videoCompressBitrateKey == null) {
                _videoCompressBitrateKey = prefs.getString("video_compress_bitrate_key", "balanced") ?: "balanced"
            }
            return _videoCompressBitrateKey!!
        }
        set(value) {
            _videoCompressBitrateKey = value
            prefs.edit().putString("video_compress_bitrate_key", value).apply()
        }

    var imageCompressThresholdMb: Int
        get() {
            if (_imageCompressThresholdMb == null) {
                _imageCompressThresholdMb = prefs.getInt("image_compress_threshold_mb", 3)
            }
            return _imageCompressThresholdMb!!
        }
        set(value) {
            _imageCompressThresholdMb = value
            prefs.edit().putInt("image_compress_threshold_mb", value).apply()
        }

    var imageCompressQualityKey: String
        get() {
            if (_imageCompressQualityKey == null) {
                _imageCompressQualityKey = prefs.getString("image_compress_quality_key", "balanced") ?: "balanced"
            }
            return _imageCompressQualityKey!!
        }
        set(value) {
            _imageCompressQualityKey = value
            prefs.edit().putString("image_compress_quality_key", value).apply()
        }

    var imageCompressResume: CompressionResumeState
        get() {
            if (_imageCompressResume == null) {
                val json = prefs.getString("image_compress_resume", null)
                val type = object : TypeToken<CompressionResumeState>() {}.type
                _imageCompressResume = try {
                    if (json != null) gson.fromJson(json, type) else CompressionResumeState()
                } catch (e: Exception) {
                    CompressionResumeState()
                }
            }
            return _imageCompressResume!!
        }
        set(value) {
            _imageCompressResume = value
            prefs.edit().putString("image_compress_resume", gson.toJson(value)).apply()
        }

    var videoCompressResume: CompressionResumeState
        get() {
            if (_videoCompressResume == null) {
                val json = prefs.getString("video_compress_resume", null)
                val type = object : TypeToken<CompressionResumeState>() {}.type
                _videoCompressResume = try {
                    if (json != null) gson.fromJson(json, type) else CompressionResumeState()
                } catch (e: Exception) {
                    CompressionResumeState()
                }
            }
            return _videoCompressResume!!
        }
        set(value) {
            _videoCompressResume = value
            prefs.edit().putString("video_compress_resume", gson.toJson(value)).apply()
        }

    var onboardingCompleted: Boolean
        get() {
            if (_onboardingCompleted == null) {
                _onboardingCompleted = prefs.getBoolean("onboarding_completed", false)
            }
            return _onboardingCompleted!!
        }
        set(value) {
            _onboardingCompleted = value
            prefs.edit().putBoolean("onboarding_completed", value).apply()
        }

    var language: AppLanguage
        get() {
            if (_language == null) {
                val code = prefs.getString("language_code", null)
                _language = if (code != null) {
                    // Handle migration from old codes (zh-CN -> zh-SC, zh-TW -> zh-TC)
                    val migratedCode = when (code) {
                        "zh-CN" -> "zh-SC"
                        "zh-TW" -> "zh-TC"
                        else -> code
                    }
                    AppLanguage.values().find { it.code == migratedCode } ?: AppLanguage.English
                } else {
                    // Auto-detect system language
                    val sysLang = Locale.getDefault().language
                    when (sysLang) {
                        "zh" -> {
                            val country = Locale.getDefault().country
                            if (country == "TW" || country == "HK" || country == "MO") AppLanguage.TraditionalChinese else AppLanguage.SimplifiedChinese
                        }
                        "ko" -> AppLanguage.Korean
                        "ja" -> AppLanguage.Japanese
                        "fr" -> AppLanguage.French
                        "de" -> AppLanguage.German
                        "it" -> AppLanguage.Italian
                        "pt" -> AppLanguage.Portuguese
                        "es" -> AppLanguage.Spanish
                        "ru" -> AppLanguage.Russian
                        "th" -> AppLanguage.Thai
                        "vi" -> AppLanguage.Vietnamese
                        "id" -> AppLanguage.Indonesian
                        "ar" -> AppLanguage.Arabic
                        else -> AppLanguage.English
                    }
                }
            }
            return _language!!
        }
        set(value) {
            _language = value
            prefs.edit().putString("language_code", value.code).apply()
        }

    var muteVideos: Boolean
        get() {
            if (_muteVideos == null) {
                _muteVideos = prefs.getBoolean("mute_videos", true)
            }
            return _muteVideos!!
        }
        set(value) {
            _muteVideos = value
            prefs.edit().putBoolean("mute_videos", value).apply()
        }

    var soundEffectsEnabled: Boolean
        get() = prefs.getBoolean("sound_effects_enabled", true)
        set(value) = prefs.edit().putBoolean("sound_effects_enabled", value).apply()

    var hapticsEnabled: Boolean
        get() {
            if (_hapticsEnabled == null) {
                _hapticsEnabled = prefs.getBoolean("haptics_enabled", true)
            }
            return _hapticsEnabled!!
        }
        set(value) {
            _hapticsEnabled = value
            prefs.edit().putBoolean("haptics_enabled", value).apply()
        }

    var avatarType: String
        get() = prefs.getString("avatar_type", "girl") ?: "girl"
        set(value) = prefs.edit().putString("avatar_type", value).apply()

    var batchSize: Int
        get() = prefs.getInt("batch_size", 16)
        set(value) = prefs.edit().putInt("batch_size", value).apply()

    var queueOrder: String
        get() = prefs.getString("queue_order", "random") ?: "random"
        set(value) = prefs.edit().putString("queue_order", value).apply()

    var filterFolder: String
        get() = prefs.getString("filter_folder", "All") ?: "All"
        set(value) = prefs.edit().putString("filter_folder", value).apply()

    var filterType: String
        get() = prefs.getString("filter_type", "All") ?: "All"
        set(value) = prefs.edit().putString("filter_type", value).apply()

    // New: Filter by Month (e.g., "2023-10") or "All"
    var filterMonth: String
        get() = prefs.getString("filter_month", "All") ?: "All"
        set(value) = prefs.edit().putString("filter_month", value).apply()

    // Comments Storage
    var comments: MutableMap<Long, List<String>>
        get() {
            if (_comments == null) {
                val json = prefs.getString("comments", null)
                val type = object : TypeToken<HashMap<Long, List<String>>>() {}.type
                _comments = try {
                    if (json != null) gson.fromJson(json, type) else HashMap()
                } catch (e: Exception) {
                    HashMap()
                }
            }
            return _comments!!
        }
        set(value) {
            _comments = value
            prefs.edit().putString("comments", gson.toJson(value)).apply()
        }



    // --- Async Save Helpers ---
    fun saveProcessedIdsToDisk(ids: Set<Long>) {
        prefs.edit().putString("processed_ids", gson.toJson(ids)).apply()
    }

    fun savePendingTrashIdsToDisk(ids: Set<Long>) {
        prefs.edit().putString("pending_trash", gson.toJson(ids)).apply()
    }

    fun saveLowQualityResultsToDisk(results: Map<Long, String>) {
        prefs.edit().putString("low_quality_results", gson.toJson(results)).apply()
    }

    fun saveScannedLowQualityIdsToDisk(ids: Set<Long>) {
        prefs.edit().putString("scanned_low_quality_ids", gson.toJson(ids)).apply()
    }
}

fun shareMedia(context: Context, item: MediaItem) {
    val displayName = getShareDisplayName(context, item)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = if (item.mimeType.isNotEmpty()) item.mimeType else if (item.type == "video") "video/*" else "image/*"
        putExtra(Intent.EXTRA_STREAM, item.uri)
        putExtra(Intent.EXTRA_TITLE, displayName)
        putExtra(Intent.EXTRA_SUBJECT, displayName)
        clipData = ClipData.newUri(context.contentResolver, displayName, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, "Share")
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(chooser)
    }
}

private fun getShareDisplayName(context: Context, item: MediaItem): String {
    val fromItem = item.displayName.trim()
    if (fromItem.isNotEmpty()) return fromItem
    val fromResolver = runCatching {
        context.contentResolver.query(
            item.uri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    }.getOrNull()?.trim().orEmpty()
    if (fromResolver.isNotEmpty()) return fromResolver
    return if (item.type == "video") "video_${item.id}.mp4" else "image_${item.id}.jpg"
}

private fun isHdrMimeType(mimeType: String?): Boolean {
    if (mimeType.isNullOrBlank()) return false
    val value = mimeType.lowercase()
    return value.contains("hdr") ||
        value.contains("hlg") ||
        value.contains("pq") ||
        value.contains("dolby") ||
        value.contains("dvh1") ||
        value.contains("dvhe")
}

private fun calculateEdgeScrollSpeed(
    cardCenterX: Float,
    screenWidthPx: Float,
    edgeThreshold: Float,
    minSpeed: Float,
    maxSpeed: Float
): Float {
    val rightOverflow = cardCenterX - (screenWidthPx - edgeThreshold)
    if (rightOverflow > 0f) {
        val factor = (rightOverflow / edgeThreshold).coerceIn(0f, 1f)
        return minSpeed + (maxSpeed - minSpeed) * factor
    }
    val leftOverflow = edgeThreshold - cardCenterX
    if (leftOverflow > 0f) {
        val factor = (leftOverflow / edgeThreshold).coerceIn(0f, 1f)
        return -(minSpeed + (maxSpeed - minSpeed) * factor)
    }
    return 0f
}

// --- Data Models ---
enum class SwipeDirection {
    Left,
    Right,
    Up,
    Down
}

data class UndoAnimationTarget(
    val itemId: Long,
    val direction: SwipeDirection,
    val token: Long
)

sealed class UndoAction {
    abstract val item: MediaItem
    abstract val direction: SwipeDirection
    
    data class Trash(
        override val item: MediaItem,
        override val direction: SwipeDirection = SwipeDirection.Left
    ) : UndoAction()
    data class Like(
        override val item: MediaItem,
        override val direction: SwipeDirection = SwipeDirection.Right
    ) : UndoAction()
    data class Keep(
        override val item: MediaItem,
        override val direction: SwipeDirection = SwipeDirection.Up
    ) : UndoAction()
    data class Move(
        override val item: MediaItem, 
        val sourceParentPath: String, 
        val targetFolder: String,
        val isNewFolder: Boolean = false,
        override val direction: SwipeDirection = SwipeDirection.Down
    ) : UndoAction()
}

// --- Main Activity ---
class NativeVoiceLikeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable High Refresh Rate
        enableHighRefreshRate()
        
        supportActionBar?.hide()
        setContent {
            VoiceLikeApp()
        }
    }

    private fun enableHighRefreshRate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // API 30+: Use preferredDisplayModeId
                this.display?.let { d ->
                    val modes = d.supportedModes
                    // Find max refresh rate
                    val maxMode = modes.maxByOrNull { it.refreshRate }
                    maxMode?.let { mode ->
                        val lp = window.attributes
                        lp.preferredDisplayModeId = mode.modeId
                        window.attributes = lp
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // API 23-29: Use preferredRefreshRate (deprecated but effective)
                val mode = windowManager.defaultDisplay.supportedModes.maxByOrNull { it.refreshRate }
                mode?.let {
                    val lp = window.attributes
                    lp.preferredRefreshRate = it.refreshRate
                    window.attributes = lp
                }
            }
        } catch (e: Exception) {
            // Fallback to system default
            Log.e("HighRefreshRate", "Failed to set high refresh rate: ${e.message}")
        }
    }
}

@Composable
fun VoiceLikeApp() {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    var onboardingCompleted by remember { mutableStateOf(prefs.onboardingCompleted) }
    var currentLanguage by remember { mutableStateOf(prefs.language) }
    var hapticsEnabled by remember { mutableStateOf(prefs.hapticsEnabled) }
    val strings = getStrings(currentLanguage)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { perms ->
            hasPermission = perms.values.all { it }
        }
    )

    val requestPermissions = {
        if (!hasPermission) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.ACCESS_MEDIA_LOCATION))
            } else {
                launcher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION))
            }
        }
    }

    // Check for MANAGE_EXTERNAL_STORAGE (Android 11+)
    val hasManageStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        android.os.Environment.isExternalStorageManager()
    } else {
        true // Not needed below R
    }
    
    // Auto-request removed to allow WelcomeView to handle it explicitly

    LaunchedEffect(hasPermission, onboardingCompleted) {
        if (onboardingCompleted && !hasPermission) {
            requestPermissions()
        }
    }

    val layoutDirection = if (currentLanguage == AppLanguage.Arabic) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(
        LocalAppLanguage provides currentLanguage,
        LocalAppStrings provides strings,
        LocalLayoutDirection provides layoutDirection,
        LocalHapticsEnabled provides hapticsEnabled
    ) {
        if (!onboardingCompleted) {
            WelcomeView(
                currentLanguage = currentLanguage,
                onLanguageSelected = { lang ->
                    currentLanguage = lang
                    prefs.language = lang
                },
                hasPermission = hasPermission,
                onRequestPermission = requestPermissions,
                hasManageStorage = hasManageStorage,
                onRequestManageStorage = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:${context.packageName}")
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            context.startActivity(intent)
                        }
                    }
                },
                onFinish = {
                    prefs.onboardingCompleted = true
                    onboardingCompleted = true
                }
            )
        } else if (hasPermission) {
            MainScreen()
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(strings.permissionsNeed)
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferencesManager(context) }
    val haptic = LocalHapticFeedback.current
    
    // Localization
    var currentLanguage by remember { mutableStateOf(prefs.language) }
    val strings = getStrings(currentLanguage)

    val layoutDirection = if (currentLanguage == AppLanguage.Arabic) LayoutDirection.Rtl else LayoutDirection.Ltr
    var hapticsEnabled by remember { mutableStateOf(prefs.hapticsEnabled) }

    CompositionLocalProvider(
        LocalAppLanguage provides currentLanguage,
        LocalAppStrings provides strings,
        LocalLayoutDirection provides layoutDirection,
        LocalHapticsEnabled provides hapticsEnabled
    ) {
        val hapticsAllowed = LocalHapticsEnabled.current
        // Core State
        var allMedia by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var displayMedia by remember { mutableStateOf<List<MediaItem>>(emptyList()) } // Filtered list
    var isFullAlbumDone by remember { mutableStateOf(false) } // Track if all items are processed
    var loading by remember { mutableStateOf(true) }
    var batchSize by remember { mutableIntStateOf(prefs.batchSize) }
    var queueOrder by remember { mutableStateOf(prefs.queueOrder) }
    var filterFolder by remember { mutableStateOf(prefs.filterFolder) }
    var filterType by remember { mutableStateOf(prefs.filterType) }
    var filterMonth by remember { mutableStateOf(prefs.filterMonth) } // New
    var muteVideos by remember { mutableStateOf(prefs.muteVideos) }
    var soundEffectsEnabled by remember { mutableStateOf(prefs.soundEffectsEnabled) }
    
    // Entrance Animation State
    var isInitialLoad by rememberSaveable { mutableStateOf(true) }
    var entryAnimationToken by remember { mutableLongStateOf(0L) }
    val entryRotationSeeds = listOf(-16f, 12f, -10f, 18f, -14f, 9f)
    val entryScreenConfig = LocalConfiguration.current
    val entryScreenWidthPx = with(LocalDensity.current) { entryScreenConfig.screenWidthDp.dp.toPx() }
    val entryScreenHeightPx = with(LocalDensity.current) { entryScreenConfig.screenHeightDp.dp.toPx() }
    val entryCardHeightPx = entryScreenWidthPx * 0.85f * (14f / 9f)
    val entryOffscreenY = entryScreenHeightPx + entryCardHeightPx + 120f
    val cardEntryOffsets = remember(entryAnimationToken, entryOffscreenY) { List(6) { Animatable(entryOffscreenY) } }
    val cardEntryRotations = remember(entryAnimationToken) {
        entryRotationSeeds.map { Animatable(it) }
    }
    val entryStaggerMs = 120L

    // Sound Pool (Moved up to be available for initial animation)
    val soundPool = remember { 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.media.SoundPool.Builder().setMaxStreams(5).build()
        } else {
            @Suppress("DEPRECATION")
            android.media.SoundPool(5, android.media.AudioManager.STREAM_MUSIC, 0)
        }
    }
    val swipeSoundId = remember { mutableIntStateOf(0) }
    val deleteSoundId = remember { mutableIntStateOf(0) }
    val magicSoundId = remember { mutableIntStateOf(0) }
    val startSoundId = remember { mutableIntStateOf(0) } // New start sound
    
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val cardsResId = context.resources.getIdentifier("cards", "raw", context.packageName)
                if (cardsResId != 0) swipeSoundId.intValue = soundPool.load(context, cardsResId, 1)
                
                val deleteResId = context.resources.getIdentifier("delete_sound", "raw", context.packageName)
                if (deleteResId != 0) deleteSoundId.intValue = soundPool.load(context, deleteResId, 1)

                val magicResId = context.resources.getIdentifier("magic", "raw", context.packageName)
                if (magicResId != 0) magicSoundId.intValue = soundPool.load(context, magicResId, 1)

                val startResId = context.resources.getIdentifier("start", "raw", context.packageName)
                if (startResId != 0) startSoundId.intValue = soundPool.load(context, startResId, 1)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val playSwipeSound = {
        if (soundEffectsEnabled && swipeSoundId.intValue != 0) {
            soundPool.play(swipeSoundId.intValue, 1f, 1f, 0, 0, 1f)
        }
    }
    
    val playDeleteSound = {
        if (soundEffectsEnabled) {
            if (deleteSoundId.intValue != 0) {
                soundPool.play(deleteSoundId.intValue, 0.2f, 0.2f, 0, 0, 1f)
            } else {
                playSwipeSound()
            }
        }
    }

    val playLikeSound = {
        if (soundEffectsEnabled) {
            if (magicSoundId.intValue != 0) {
                soundPool.play(magicSoundId.intValue, 1f, 1f, 0, 0, 1f)
            } else {
                playSwipeSound()
            }
        }
    }

    // Track pending sound play if ID is not yet loaded
    var pendingStartSoundPlay by remember { mutableStateOf(false) }

    val playStartSound = {
        if (soundEffectsEnabled) {
            if (startSoundId.intValue != 0) {
                soundPool.play(startSoundId.intValue, 1f, 1f, 0, 0, 1f)
                pendingStartSoundPlay = false
            } else {
                // Mark as pending if not loaded yet
                pendingStartSoundPlay = true
            }
        }
    }

    // Monitor sound load completion for pending plays
    LaunchedEffect(startSoundId.intValue) {
        if (startSoundId.intValue != 0 && pendingStartSoundPlay && soundEffectsEnabled) {
            soundPool.play(startSoundId.intValue, 1f, 1f, 0, 0, 1f)
            pendingStartSoundPlay = false
        }
    }

    LaunchedEffect(displayMedia, entryAnimationToken) {
        if (isInitialLoad && displayMedia.isNotEmpty()) {
            playStartSound()
            delay(280) // Slight delay to prevent white cards
            // Trigger animation
            cardEntryOffsets.forEachIndexed { index, anim ->
                launch {
                    delay(index * entryStaggerMs)
                    anim.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = 0.6f,
                            stiffness = 600f
                        )
                    )
                }
            }
            cardEntryRotations.forEachIndexed { index, anim ->
                launch {
                    delay(index * entryStaggerMs)
                    anim.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = 0.7f,
                            stiffness = 380f
                        )
                    )
                }
            }
            // Mark as loaded so it doesn't replay on recomposition or new batches
            // We delay slightly to ensure the animation logic has kicked off
            delay(1000) 
            isInitialLoad = false
        }
    }
    var avatarType by remember { mutableStateOf(prefs.avatarType) }
    
    // Stats State
    var stats by remember { mutableStateOf(prefs.stats) }
    var pendingTrashCount by remember { mutableIntStateOf(prefs.pendingTrashIds.size) }
    
    // UI State
    var showStatsOverlay by remember { mutableStateOf(false) }
    var showShootingStats by remember { mutableStateOf(false) }
    var showDeleteModal by remember { mutableStateOf(false) }
    var showDeepOrganize by remember { mutableStateOf(false) } // New state
    var showSimilarPhotos by remember { mutableStateOf(false) }
    var showLowQuality by remember { mutableStateOf(false) } // New state
    var showLikes by remember { mutableStateOf(false) } // New state for Likes view
    var showMonthly by remember { mutableStateOf(false) } // New state for Monthly view
    var showLanguage by remember { mutableStateOf(false) } // New state for Language view
    var showPrivacy by remember { mutableStateOf(false) } // New state for Privacy view
    var showSettings by remember { mutableStateOf(false) } // New state for Settings view
    var pendingQueueOrderChange by remember { mutableStateOf(false) } // Pending queue order change to apply when returning to main
    var pendingBatchSizeChange by remember { mutableStateOf(false) } // Pending batch size change to apply when returning to main
    var showOpenSourceLicenses by remember { mutableStateOf(false) } // New state for Open Source Licenses
    var showFilter by remember { mutableStateOf(false) } // New state for Filter view
    var moveFeedbackMessage by remember { mutableStateOf<String?>(null) } // Custom feedback state
    var showVideoCompress by remember { mutableStateOf(false) }
    var showImageCompress by remember { mutableStateOf(false) }

    // Combo System
    var comboCount by remember { mutableIntStateOf(0) }
    var lastActionTime by remember { mutableLongStateOf(0L) }
    
    // Reset combo if idle (3 seconds)
    LaunchedEffect(comboCount, lastActionTime) {
        if (comboCount > 0) {
            delay(3000)
            if (System.currentTimeMillis() - lastActionTime >= 3000) {
                comboCount = 0
            }
        }
    }

    // Sync stats when returning from compression views which update prefs directly
    LaunchedEffect(showVideoCompress, showImageCompress) {
        if (!showVideoCompress && !showImageCompress) {
            prefs.reloadStats()
            stats = prefs.stats
        }
    }

    var monthlyReviewList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var similarGroups by remember { mutableStateOf<List<List<MediaItem>>>(emptyList()) }
    // Manager State
    LaunchedEffect(Unit) {
        SimilarPhotoManager.init(context)
    }
    
    // Track if stats animation has played once in this session
    var hasPlayedStatsAnimation by rememberSaveable { mutableStateOf(false) }

    val isScanningSimilar by SimilarPhotoManager.isScanning.collectAsState()
    val scanProgress by SimilarPhotoManager.scanProgress.collectAsState()
    val activeSimilarGroups by SimilarPhotoManager.foundGroups.collectAsState()

    val isScanningLowQuality by LowQualityManager.isScanning.collectAsState()
    val scanProgressLowQuality by LowQualityManager.scanProgress.collectAsState()
    // We observe the flow, but manage display list locally to prevent ghosting/lag
    val lowQualityItemsFromManager by LowQualityManager.foundItems.collectAsState()
    val newlyScannedLowQualityIds by LowQualityManager.newlyScannedIds.collectAsState()
    
    // Global Scan Coordinator: Unified Background Service
    LaunchedEffect(allMedia) {
        if (allMedia.isNotEmpty()) {
             // 1. Explicitly start the Unified Analysis Service
             // This ensures "The Brain" is running even if individual features are disabled/idle.
             // It runs silently in background (THREAD_PRIORITY_BACKGROUND) with incremental caching.
             MediaAnalysisManager.startScan(context, allMedia)
             
             // 2. Attach Listeners / Managers
             // Similar Photos Manager (Observes MediaAnalysisManager)
             val processed = prefs.processedIds
             val ignoredIds = processed + prefs.pendingTrashIds
             SimilarPhotoManager.startScan(context, allMedia, ignoredIds)
             
             // Low Quality Manager (Observes MediaAnalysisManager)
             val savedResults = prefs.lowQualityResults
             val scannedIds = prefs.scannedLowQualityIds
             LowQualityManager.startScan(context, allMedia, ignoredIds, savedResults, scannedIds)
             
             // Smart Organize Manager
             // Smart Organize is a "Pull" service (on-demand), so it doesn't need a "startScan".
             // However, it AUTOMATICALLY benefits from MediaAnalysisManager's background work.
             // When the user opens Smart Organize, the analysis (Faces, etc.) will likely be ready in cache.
        }
    }
    
    // Persistence Debouncer for Scanned IDs
    LaunchedEffect(newlyScannedLowQualityIds) {
        if (newlyScannedLowQualityIds.isNotEmpty()) {
            val currentScanned = prefs.scannedLowQualityIds
            currentScanned.addAll(newlyScannedLowQualityIds)
            
            delay(5000) // Debounce 5s (less critical)
            
            val snapshot = HashSet(currentScanned)
            withContext(Dispatchers.IO) {
                prefs.saveScannedLowQualityIdsToDisk(snapshot)
            }
        }
    }
    
    // Local state for Low Quality Display (Critical for Swipe Performance)
    var lowQualityDisplayItems by remember { mutableStateOf<List<Pair<MediaItem, String>>>(emptyList()) }
    
    // Sync Manager -> Local (Only Append)
    // When manager finds new items, we append them if they are not already processed/displayed.
    // But we must NOT overwrite local state if we just deleted something locally.
    // Strategy: 
    // 1. Initial Load: Copy all.
    // 2. Updates: If Manager list grows, append. If Manager list shrinks (due to our removal), ignore?
    // Actually, simpler: Initialize local state when entering view.
    // But scan is ongoing.
    
    // Correct Strategy:
    // Maintain a set of "Locally Removed IDs".
    // lowQualityDisplayItems = lowQualityItemsFromManager.filter { !locallyRemovedIds.contains(it.first.id) }
    // No, that's computed state, simpler.
    // But swiping needs to update UI immediately.
    // So:
    // lowQualityDisplayItems IS the source of truth for the UI.
    // We update it when:
    // 1. Swipe -> Remove item locally.
    // 2. Manager finds NEW items -> Append them.
    
    LaunchedEffect(lowQualityItemsFromManager) {
        // Simple merge: Add items from manager that are not in current display list AND not processed
        // But since we persist processed IDs, manager shouldn't return them anyway (except during race).
        // Let's just use the Manager list but filter out things we *just* swiped if Manager hasn't updated yet?
        // Actually, easiest way to fix "Ghosting":
        // Use a local MutableList.
        // When Manager emits, we replace the local list? No, that causes the jump back.
        
        // Let's try:
        // UI uses `lowQualityDisplayItems`.
        // `lowQualityDisplayItems` is initialized from `lowQualityItemsFromManager`.
        // But we need to keep them in sync.
        
        // Alternative: The issue is likely that `handleLowQualitySwipe` calls `LowQualityManager.removeResult`.
        // This triggers `_foundItems.value = ...`.
        // This triggers Flow emission.
        // This triggers Recomposition.
        // If this loop is slow, the card might flicker.
        
        // But "Card swiped then comes back" implies the Flow emitted the OLD list again?
        // Or the UI recomposed with the old list before the new one arrived.
        
        // Fix: Use a local state that we modify *immediately* on swipe.
        // And when Manager emits, we update our local state *carefully*.
        
        val currentIds = lowQualityDisplayItems.map { it.first.id }.toSet()
        val newItems = lowQualityItemsFromManager.filter { !currentIds.contains(it.first.id) }
        
        if (newItems.isNotEmpty()) {
             // Only append NEW items. Do not restore items that are missing from manager (because we deleted them).
             // But wait, if we delete locally, they are still in Manager for a split second.
             // Manager emits. It might still contain the item.
             // So we must filter out items that we know we processed.
             val processed = prefs.processedIds // This is source of truth
             val validNewItems = newItems.filter { !processed.contains(it.first.id) }
             
             if (validNewItems.isNotEmpty()) {
                 lowQualityDisplayItems = lowQualityDisplayItems + validNewItems
             }
        }
        
        // Also handle case where Manager was cleared (e.g. stopScan)
        if (lowQualityItemsFromManager.isEmpty() && !isScanningLowQuality) {
            // Maybe we should clear?
             // lowQualityDisplayItems = emptyList() 
             // Keep it if we are viewing results?
        }
    }
    
    // Initial Load / Reset
    LaunchedEffect(showLowQuality) {
        if (showLowQuality) {
            // Do NOT clear lowQualityDisplayItems immediately if we want to show persisted results fast.
            // But we need to ensure they are valid.
            // Best approach:
            // 1. Get persisted results from prefs
            val savedResults = prefs.lowQualityResults
            val processed = prefs.processedIds
            val ignoredIds = processed + prefs.pendingTrashIds
            
            // 2. Pre-populate local display items from savedResults (fast UI response)
            // Filter out ignored items
            val restoredItems = allMedia.filter { savedResults.containsKey(it.id) && !ignoredIds.contains(it.id) }
                .map { it to savedResults[it.id]!! }
            
            if (restoredItems.isNotEmpty()) {
                lowQualityDisplayItems = restoredItems
            } else {
                lowQualityDisplayItems = emptyList()
            }
            
            // 3. Ensure scan is running (if not already)
            // It should be running from startup, but if it stopped or failed, restart it.
            // But don't restart if it's already running to avoid progress reset.
            // AND ensure we don't conflict with Similar Photos Scan.
            if (!isScanningLowQuality && !isScanningSimilar) {
                val scannedIds = prefs.scannedLowQualityIds
                LowQualityManager.startScan(context, allMedia, ignoredIds, savedResults, scannedIds)
            }
        } else {
            // Do NOT stop scan on close. Let it run in background.
            // LowQualityManager.stopScan() 
            
            // lowQualityDisplayItems = emptyList() // Keep for transition or clear?
            // If we clear, next open is blank until restore.
            // Let's clear to save memory if needed, but persistence handles restore.
            lowQualityDisplayItems = emptyList()
        }
    }
    
    // Persistence Debouncer
    LaunchedEffect(lowQualityItemsFromManager) {
        if (lowQualityItemsFromManager.isNotEmpty()) {
            // Update Cache with found items
            val cache = prefs.lowQualityResults
            lowQualityItemsFromManager.forEach { (item, reason) ->
                cache[item.id] = reason
            }
            
            delay(1000) // Debounce 1s
            
            // Save Snapshot
            val map = HashMap(cache)
            withContext(Dispatchers.IO) {
                prefs.saveLowQualityResultsToDisk(map)
            }
        }
    }
    
    var undoStack by remember { mutableStateOf(listOf<UndoAction>()) } // Item + Action Type
    var undoAnimationTarget by remember { mutableStateOf<UndoAnimationTarget?>(null) }
    var isUndoAnimating by remember { mutableStateOf(false) }
    var lowQualityUndoStack by remember { mutableStateOf(listOf<Triple<MediaItem, String, Boolean>>()) } // Item + Reason + wasTrashed
    var viewingItem by remember { mutableStateOf<MediaItem?>(null) }
    val pauseMainVideos = showStatsOverlay ||
        showShootingStats ||
        showDeleteModal ||
        showDeepOrganize ||
        showSimilarPhotos ||
        showLowQuality ||
        showLikes ||
        showMonthly ||
        showLanguage ||
        showPrivacy ||
        showSettings ||
        showFilter ||
        showVideoCompress ||
        showImageCompress ||
        monthlyReviewList.isNotEmpty() ||
        viewingItem != null

    LaunchedEffect(undoAnimationTarget?.token) {
        val token = undoAnimationTarget?.token
        if (token == null) {
            isUndoAnimating = false
            return@LaunchedEffect
        }
        isUndoAnimating = true
        delay(320)
        if (undoAnimationTarget?.token == token) {
            undoAnimationTarget = null
            isUndoAnimating = false
        }
    }

    // Back Handler
    val isRootState = viewingItem == null && 
                      monthlyReviewList.isEmpty() && 
                      !showMonthly && 
                      !showLanguage && 
                      !showPrivacy && 
                      !showSettings && 
                      !showFilter && 
                      !showLikes &&
                      !showSimilarPhotos && 
                      !showLowQuality && 
                      !showVideoCompress && 
                      !showImageCompress && 
                      !showDeepOrganize && 
                      !showStatsOverlay && 
                      !showShootingStats &&
                      !showDeleteModal

    // We only need the global BackHandler for states NOT wrapped in PredictiveBackWrapper
    // Currently, all major sub-pages are wrapped.
    // Remaining states that might need handling:
    // - viewingItem (FullScreenImageViewer) -> Not wrapped yet
    // - showStatsOverlay -> Not wrapped yet
    // - showDeleteModal -> Not wrapped yet
    
    BackHandler(enabled = !isRootState) {
        when {
            viewingItem != null -> viewingItem = null
            // monthlyReviewList & showMonthly are handled by wrapper
            // showLanguage, showPrivacy, showSettings, showFilter handled by wrapper
            // showLikes handled by wrapper
            // showDeepOrganize & sub-features handled by wrapper
            
            showStatsOverlay -> showStatsOverlay = false
            showDeleteModal -> showDeleteModal = false
        }
    }

    // Pending Move Args for Retry
    var pendingMoveArgs by remember { mutableStateOf<Triple<List<MediaItem>, String, String?>?>(null) }
    var pendingMoveUiAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Intent Sender for File Operations (Android 10/11+)
    val intentSenderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
             // User granted permission. Retry operation if pending.
             pendingMoveArgs?.let { (items, folder, existingAlbumId) ->
                 scope.launch {
                     // Retry move
                     val retrySender = FileOperationManager.moveMedia(context, items, folder)
                    if (retrySender == null) {
                         // Success
                         Toast.makeText(context, "Moved ${items.size} items to $folder", Toast.LENGTH_SHORT).show()
                         pendingMoveArgs = null
                        pendingMoveUiAction?.invoke()
                        pendingMoveUiAction = null
                     } else {
                         // Still need permission? This shouldn't happen immediately if granted.
                         Toast.makeText(context, "Permission still required or operation failed.", Toast.LENGTH_SHORT).show()
                     }
                 }
             } ?: run {
                 Toast.makeText(context, "Permission granted.", Toast.LENGTH_SHORT).show()
             }
        } else {
            // Permission denied or cancelled
            pendingMoveArgs = null
            pendingMoveUiAction = null
            Toast.makeText(context, "Permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    // Delete Launcher
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Confirmed delete
            val currentPending = prefs.pendingTrashIds
            
            // Remove from allMedia (Local Update) to prevent them from reappearing on Reset Filter
            val pendingSet = currentPending.toSet()
            if (pendingSet.isNotEmpty()) {
                allMedia = allMedia.filter { !pendingSet.contains(it.id) }
            }
            
            currentPending.clear()
            prefs.pendingTrashIds = currentPending
            pendingTrashCount = 0
            
            // Clear undo stack for trashed items as they are now gone from the app context
            // (If we used TrashRequest, they are in system trash, but app can't easily restore them back to view without permission complexity)
            // It's safer to clear the undo stack to prevent "Ghost" cards.
            undoStack = emptyList()
        }
    }

    // Load Media
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val raw = loadMedia(context)
            // Load EXIF for visible items (optimization: could be lazy)
            // For now, load all or batch. Let's do lazy loading in DraggableCard or just simple load here for now.
            // Since user complained about "reading nothing", let's ensure we read it.
            // Reading EXIF for 1000s of photos is slow.
            // Better approach: Update MediaItem when it becomes top card or expanded.
            // However, to keep it simple and fix the "reading nothing" issue immediately:
            // Let's modify loadMedia to read EXIF for the first few items or on demand.
            // Actually, `loadMedia` is just a query.
            // Let's rely on a side-effect to load EXIF when a card is shown.
            
            val processed = prefs.processedIds
            val orderedRaw = if (prefs.queueOrder == "time") raw.sortedByDescending { it.dateAdded } else raw.shuffled()
            allMedia = orderedRaw
            
            // Initial Filter Application
            var filtered = orderedRaw
            if (filterFolder != "All") {
                filtered = filtered.filter { it.bucketName == filterFolder }
            }
            if (filterType != "All") {
                filtered = filtered.filter { 
                    when (filterType) {
                        "Image" -> it.type == "photo" && !it.isLivePhoto
                        "Video" -> it.type == "video"
                        "Live" -> it.isLivePhoto
                        else -> true
                    }
                }
            }
            
            displayMedia = filtered.filter { !processed.contains(it.id) }.take(batchSize)
            loading = false
            
            // Auto-resume background scan
            // Ignore both processed items AND items currently in the trash queue
            val ignoredIds = processed + prefs.pendingTrashIds
            
            // Start Unified Scanning
            // Calling both managers allows them to attach their listeners to the unified MediaAnalysisManager
            SimilarPhotoManager.startScan(context, raw, ignoredIds)
            
            val savedResults = prefs.lowQualityResults
            val scannedIds = prefs.scannedLowQualityIds
            LowQualityManager.startScan(context, raw, ignoredIds, savedResults, scannedIds)
        }
    }

    // Check for similar photos for displayed items (Look-ahead)
    // We maintain a sorted copy of all media for efficient time-window search
    val sortedAllMedia = remember(allMedia) { allMedia.sortedBy { it.dateAdded } }
    
    // Track the set of IDs we have already requested a check for in the current batch context
    val lastCheckedIds = remember { mutableStateOf<Set<Long>>(emptySet()) }
    
    LaunchedEffect(displayMedia, sortedAllMedia) {
        if (displayMedia.isNotEmpty() && sortedAllMedia.isNotEmpty()) {
            val itemsToCheck = displayMedia.take(16)
            val currentIds = itemsToCheck.map { it.id }.toSet()
            
            // Only run if the current top items contain things we haven't checked as a batch recently.
            // When we swipe, currentIds becomes a subset of the previous batch.
            // When we load a new batch, currentIds is disjoint (mostly).
            // We use 'containsAll' to detect if we are just seeing a subset of already checked items.
            val alreadyChecked = lastCheckedIds.value.isNotEmpty() && lastCheckedIds.value.containsAll(currentIds)
            
            if (!alreadyChecked) {
                lastCheckedIds.value = currentIds
                // Launch in 'scope' (Screen scope) so it survives recomposition/swipes
                scope.launch(Dispatchers.Default) {
                    val results = SimilarPhotoManager.findSimilarForItems(context, itemsToCheck, sortedAllMedia)
                    
                    if (results.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            val newGroups = results.values.distinctBy { group ->
                                group.map { it.id }.sorted().joinToString(",")
                            }
                            
                            val existingKeys = similarGroups.map { group -> 
                                group.map { it.id }.sorted().joinToString(",") 
                            }.toSet()
                            
                            val toAdd = newGroups.filter { group ->
                                val key = group.map { it.id }.sorted().joinToString(",")
                                !existingKeys.contains(key)
                            }
                            
                            if (toAdd.isNotEmpty()) {
                                similarGroups = similarGroups + toAdd
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Determine if the TOP card has similar photos
    val topCard = displayMedia.firstOrNull()
    val topCardSimilarGroup = remember(topCard, similarGroups) {
        if (topCard == null) null
        else similarGroups.find { group -> group.any { it.id == topCard.id } }
    }

    var currentBatchTotal by remember { mutableIntStateOf(16) }
    
    // Handlers
    fun loadNextBatch(playEntryAnimation: Boolean = false) {
        val processedSnapshot = prefs.processedIds.toSet()
        val currentAllMedia = allMedia
        val currentFilterMonth = filterMonth
        val currentFilterFolder = filterFolder
        val currentFilterType = filterType
        val currentBatchSize = batchSize

        scope.launch(Dispatchers.Default) {
            if (playEntryAnimation) {
                withContext(Dispatchers.Main) {
                    isInitialLoad = true
                    entryAnimationToken++
                }
            }
            // Apply Filters
            var filtered = currentAllMedia
            
            // Month Filter (Higher Priority than Folder/Type?)
            // Let's apply all AND logic
            
            if (currentFilterMonth != "All") {
                // Filter by Month (YYYY-MM)
                val cal = Calendar.getInstance()
                filtered = filtered.filter { 
                    cal.timeInMillis = it.dateAdded * 1000L
                    val year = cal.get(Calendar.YEAR)
                    val month = cal.get(Calendar.MONTH) + 1
                    val key = "$year-${month.toString().padStart(2, '0')}"
                    key == currentFilterMonth
                }
            }
            
            if (currentFilterFolder != "All") {
                filtered = filtered.filter { it.bucketName == currentFilterFolder }
            }
            if (currentFilterType != "All") {
                filtered = filtered.filter { 
                    when (currentFilterType) {
                        "Image" -> it.type == "photo" && !it.isLivePhoto
                        "Video" -> it.type == "video"
                        "Live" -> it.isLivePhoto
                        else -> true
                    }
                }
            }
            
            val remaining = filtered.filter { !processedSnapshot.contains(it.id) }
            val nextBatch = remaining.take(currentBatchSize)
            
            withContext(Dispatchers.Main) {
                isFullAlbumDone = remaining.isEmpty()
                displayMedia = nextBatch
                currentBatchTotal = nextBatch.size // Capture initial size
                undoStack = emptyList()
            }
        }
    }

    fun handleSwipe(action: UndoAction) {
        val item = action.item

        // Sound moved to DraggableCard to play BEFORE animation
        
        // Combo Logic
        val now = System.currentTimeMillis()
        if (now - lastActionTime < 3000) {
            comboCount++
        } else {
            comboCount = 1
        }
        lastActionTime = now

        // Update Lists
        val newDisplay = displayMedia.toMutableList()
        if (newDisplay.isNotEmpty()) {
            newDisplay.removeAt(0)
        }
        displayMedia = newDisplay

        // Update Persistence
        val processed = prefs.processedIds
        processed.add(item.id)
        prefs.processedIds = processed

        when (action) {
            is UndoAction.Trash -> {
                val pending = prefs.pendingTrashIds
                pending.add(item.id)
                if (item.livePhotoVideoId != null) {
                    pending.add(item.livePhotoVideoId)
                }
                prefs.pendingTrashIds = pending
                pendingTrashCount = pending.size
                
                val newStats = stats.copy(
                    processed = stats.processed + 1,
                    trashed = stats.trashed + 1,
                    savedSize = stats.savedSize + item.size
                )
                stats = newStats
                prefs.stats = newStats
            }
            is UndoAction.Like -> {
                val liked = prefs.likedIds
                liked.add(item.id)
                prefs.likedIds = liked

                val newStats = stats.copy(
                    processed = stats.processed + 1,
                    skipped = stats.skipped + 1
                )
                stats = newStats
                prefs.stats = newStats
            }
            is UndoAction.Keep, is UndoAction.Move -> {
                // Keep or Move (Skipped)
                val newStats = stats.copy(
                    processed = stats.processed + 1,
                    skipped = stats.skipped + 1
                )
                stats = newStats
                prefs.stats = newStats
            }
        }
        
        undoStack = undoStack + action
    }

    // Apply pending settings changes when returning to main screen
    LaunchedEffect(showSettings, showStatsOverlay, showShootingStats, showDeepOrganize, showSimilarPhotos, 
                   showMonthly, showLikes, showLowQuality, showVideoCompress, showImageCompress) {
        // Only apply when ALL sub-pages and overlays are closed (user is back on main screen)
        val isOnMainScreen = !showSettings && !showStatsOverlay && !showShootingStats && !showDeepOrganize && 
                             !showSimilarPhotos && !showMonthly && !showLikes && 
                             !showLowQuality && !showVideoCompress && !showImageCompress
        if (isOnMainScreen) {
            if (pendingQueueOrderChange) {
                allMedia = if (queueOrder == "time") {
                    allMedia.sortedByDescending { it.dateAdded }
                } else {
                    allMedia.shuffled()
                }
                loadNextBatch(playEntryAnimation = true)
                pendingQueueOrderChange = false
            }
            if (pendingBatchSizeChange) {
                loadNextBatch(playEntryAnimation = false)
                pendingBatchSizeChange = false
            }
        }
    }

    fun handleLowQualitySwipe(item: MediaItem, reason: String, isTrash: Boolean) {
        val processed = prefs.processedIds
        processed.add(item.id)
        // Async Save Processed
        val processedSnapshot = HashSet(processed)
        scope.launch(Dispatchers.IO) {
            prefs.saveProcessedIdsToDisk(processedSnapshot)
        }

        if (isTrash) {
            val pending = prefs.pendingTrashIds
            pending.add(item.id)
            if (item.livePhotoVideoId != null) {
                pending.add(item.livePhotoVideoId)
            }
            // Async Save Trash
            val pendingSnapshot = HashSet(pending)
            scope.launch(Dispatchers.IO) {
                prefs.savePendingTrashIdsToDisk(pendingSnapshot)
            }
            pendingTrashCount = pending.size
            
            val newStats = stats.copy(
                processed = stats.processed + 1,
                trashed = stats.trashed + 1,
                savedSize = stats.savedSize + item.size
            )
            stats = newStats
            prefs.stats = newStats
        } else {
             val newStats = stats.copy(
                processed = stats.processed + 1,
                skipped = stats.skipped + 1
            )
            stats = newStats
            prefs.stats = newStats
        }
        
        // Remove from displayMedia if present to prevent it appearing on main page
        val currentDisplay = displayMedia
        val newDisplay = currentDisplay.filter { it.id != item.id }
        if (newDisplay.size != currentDisplay.size) {
            displayMedia = newDisplay
        }

        // Add to Low Quality Undo Stack (Independent)
        lowQualityUndoStack = lowQualityUndoStack + Triple(item, reason, isTrash)

        // Remove from Local Display List (Immediate UI update)
        lowQualityDisplayItems = lowQualityDisplayItems.filter { it.first.id != item.id }

        // Remove from LowQualityManager list (Background sync)
        LowQualityManager.removeResult(item.id)
        
        // Update persistence
        val saved = prefs.lowQualityResults
        if (saved.containsKey(item.id)) {
            saved.remove(item.id)
            // Async Save Results
            val savedSnapshot = HashMap(saved)
            scope.launch(Dispatchers.IO) {
                prefs.saveLowQualityResultsToDisk(savedSnapshot)
            }
        }
    }

    fun handleLowQualityUndo() {
        if (lowQualityUndoStack.isEmpty()) return
        val (item, reason, wasTrashed) = lowQualityUndoStack.last()
        lowQualityUndoStack = lowQualityUndoStack.dropLast(1)

        // Update Persistence
        val processed = prefs.processedIds
        processed.remove(item.id)
        // Async Save Processed
        val processedSnapshot = HashSet(processed)
        scope.launch(Dispatchers.IO) {
            prefs.saveProcessedIdsToDisk(processedSnapshot)
        }

        if (wasTrashed) {
            val pending = prefs.pendingTrashIds
            pending.remove(item.id)
            // Async Save Trash
            val pendingSnapshot = HashSet(pending)
            scope.launch(Dispatchers.IO) {
                prefs.savePendingTrashIdsToDisk(pendingSnapshot)
            }
            pendingTrashCount = pending.size
            
            val newStats = stats.copy(
                processed = stats.processed - 1,
                trashed = stats.trashed - 1,
                savedSize = stats.savedSize - item.size
            )
            stats = newStats
            prefs.stats = newStats
        } else {
            val newStats = stats.copy(
                processed = stats.processed - 1,
                skipped = stats.skipped - 1
            )
            stats = newStats
            prefs.stats = newStats
        }

        // Restore to Local Display List (Immediate UI update)
        lowQualityDisplayItems = listOf(item to reason) + lowQualityDisplayItems

        // Restore to LowQualityManager list
        LowQualityManager.restoreResult(item, reason)
        
        // Restore to persistence
        val saved = prefs.lowQualityResults
        saved[item.id] = reason
        // Async Save Results
        val savedSnapshot = HashMap(saved)
        scope.launch(Dispatchers.IO) {
            prefs.saveLowQualityResultsToDisk(savedSnapshot)
        }
    }

    // Calculate Folder Targets
    var folderUsageState by remember { mutableStateOf(prefs.folderUsage) }

    // Undo Cooldown
    var lastUndoTime by remember { mutableLongStateOf(0L) }

    fun handleUndo() {
        val now = System.currentTimeMillis()
        if (now - lastUndoTime < 300) return // Prevent rapid undo clicks causing stack corruption
        lastUndoTime = now

        if (undoStack.isEmpty()) return
        val action = undoStack.last()
        undoStack = undoStack.dropLast(1)
        val item = action.item
        
        // Restore to Display List (at front)
        isUndoAnimating = true
        displayMedia = listOf(item) + displayMedia
        undoAnimationTarget = UndoAnimationTarget(item.id, action.direction, System.currentTimeMillis())
        
        // Update Persistence
        val processed = prefs.processedIds
        processed.remove(item.id)
        prefs.processedIds = processed
        
        when (action) {
            is UndoAction.Trash -> {
                val pending = prefs.pendingTrashIds
                pending.remove(item.id)
                prefs.pendingTrashIds = pending
                pendingTrashCount = pending.size
                
                val newStats = stats.copy(
                    processed = stats.processed - 1,
                    trashed = stats.trashed - 1,
                    savedSize = stats.savedSize - item.size
                )
                stats = newStats
                prefs.stats = newStats
            }
            is UndoAction.Like -> {
                val liked = prefs.likedIds
                liked.remove(item.id)
                prefs.likedIds = liked
                
                val newStats = stats.copy(
                    processed = stats.processed - 1,
                    skipped = stats.skipped - 1
                )
                stats = newStats
                prefs.stats = newStats
            }
            is UndoAction.Keep -> {
                val newStats = stats.copy(
                    processed = stats.processed - 1,
                    skipped = stats.skipped - 1
                )
                stats = newStats
                prefs.stats = newStats
            }
            is UndoAction.Move -> {
                val moved = prefs.movedIds
                moved.remove(item.id)
                prefs.movedIds = moved

                // Update Folder Counts
                val currentUsage = prefs.folderUsage.toMutableMap()
                val targetBucketId = File(action.targetFolder).name
                
                if (action.isNewFolder) {
                    currentUsage.remove(targetBucketId)
                } else {
                    val count = currentUsage[targetBucketId] ?: 0
                    if (count > 0) {
                        currentUsage[targetBucketId] = count - 1
                    }
                }
                prefs.folderUsage = currentUsage
                folderUsageState = currentUsage

                val newStats = stats.copy(
                    processed = stats.processed - 1,
                    skipped = stats.skipped - 1
                )
                stats = newStats
                prefs.stats = newStats

                // Update allMedia to reflect the undo (fix folder counts)
                val originalItem = action.item
                allMedia = allMedia.map { 
                    if (it.id == originalItem.id) originalItem else it 
                }
                
                // Silent Move Back
                scope.launch(Dispatchers.IO) {
                    try {
                         val targetDir = File(action.sourceParentPath)
                         if (!targetDir.exists()) targetDir.mkdirs()
                         
                         val fileName = File(item.path).name
                         val currentFile = File(android.os.Environment.getExternalStorageDirectory(), action.targetFolder + "/" + fileName)
                         
                         if (currentFile.exists()) {
                             val destFile = File(targetDir, fileName)
                             
                             // Check if permission available
                             val hasManage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                 android.os.Environment.isExternalStorageManager()
                             } else {
                                 false
                             }

                             if (hasManage) {
                                 val strings = getStrings(prefs.language)
                                 if (currentFile.renameTo(destFile)) {
                                     FileOperationManager.scanFile(context, currentFile.absolutePath)
                                     FileOperationManager.scanFile(context, destFile.absolutePath)
                                     
                                     if (action.isNewFolder) {
                                         val folderToDelete = currentFile.parentFile
                                         if (folderToDelete != null && folderToDelete.exists() && folderToDelete.listFiles()?.isEmpty() == true) {
                                             folderToDelete.delete()
                                         }
                                     }

                                     withContext(Dispatchers.Main) {
                                         Toast.makeText(context, strings.undoSuccess, Toast.LENGTH_SHORT).show()
                                     }
                                 } else {
                                     // Fallback copy
                                     currentFile.inputStream().use { input ->
                                         destFile.outputStream().use { output ->
                                             input.copyTo(output)
                                         }
                                     }
                                     if (destFile.exists() && destFile.length() == currentFile.length()) {
                                         currentFile.delete()
                                         FileOperationManager.scanFile(context, currentFile.absolutePath)
                                         FileOperationManager.scanFile(context, destFile.absolutePath)
                                         
                                         if (action.isNewFolder) {
                                             val folderToDelete = currentFile.parentFile
                                             if (folderToDelete != null && folderToDelete.exists() && folderToDelete.listFiles()?.isEmpty() == true) {
                                                 folderToDelete.delete()
                                             }
                                         }

                                         withContext(Dispatchers.Main) {
                                             Toast.makeText(context, strings.undoSuccess, Toast.LENGTH_SHORT).show()
                                         }
                                     }
                                 }
                             } else {
                                 // No permission (Android 10 or denied)
                                 withContext(Dispatchers.Main) {
                                     Toast.makeText(context, "Cannot undo move: Permission required", Toast.LENGTH_SHORT).show()
                                 }
                             }
                         }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun handleEmptyTrash() {
        scope.launch(Dispatchers.IO) {
            val pendingIds = prefs.pendingTrashIds
            if (pendingIds.isEmpty()) return@launch
            
            val uris = pendingIds.mapNotNull { id -> 
                val item = allMedia.find { it.id == id }
                if (item != null) {
                    if (item.type == "video") {
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    } else {
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    }
                } else {
                    var uri: Uri? = null
                    try {
                        context.contentResolver.query(
                            MediaStore.Files.getContentUri("external"),
                            arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE),
                            "${MediaStore.Files.FileColumns._ID}=?",
                            arrayOf(id.toString()),
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val type = cursor.getInt(0)
                                uri = if (type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                                } else {
                                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    uri
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Use Trash Request (Recycle Bin) instead of permanent delete
                // This allows recovery from System Gallery -> Trash
                val pi = MediaStore.createTrashRequest(context.contentResolver, uris, true)
                try {
                    withContext(Dispatchers.Main) {
                        deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                // Older Android versions
                uris.forEach { context.contentResolver.delete(it, null, null) }
                withContext(Dispatchers.Main) {
                    // Remove from allMedia (Local Update)
                    val pendingSet = prefs.pendingTrashIds.toSet()
                    if (pendingSet.isNotEmpty()) {
                        allMedia = allMedia.filter { !pendingSet.contains(it.id) }
                    }

                    prefs.pendingTrashIds = mutableSetOf()
                    pendingTrashCount = 0
                    undoStack = undoStack.filter { it is UndoAction.Keep } // Only keep safe actions or clear all
                }
            }
        }
    }

    PredictiveBackWrapper(
        isVisible = isRootState,
        onBack = { 
            (context as? Activity)?.finish()
        }
    ) {
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.Black)
            }
        } else {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE5E5E5)) // Neutral 200
    ) {
        var topCardOffset by remember { mutableStateOf(Offset.Zero) }
        val topCardId = displayMedia.firstOrNull()?.id
        var isDragging by remember { mutableStateOf(false) }
        LaunchedEffect(topCardId) {
            topCardOffset = Offset.Zero
            isDragging = false
        }
        // Increased threshold to 300f to prevent accidental triggers (previously 150f)
        val isFolderDropMode = isDragging && topCardOffset.y > 300f
        var folderDropTargets by remember { mutableStateOf<List<FolderData>>(emptyList()) }
        var activeDropTargetId by remember { mutableStateOf<String?>(null) }
        var folderDropTargetRects by remember { mutableStateOf<Map<String, androidx.compose.ui.geometry.Rect>>(emptyMap()) }
        var showNewFolderDialog by remember { mutableStateOf(false) }
        var newFolderName by remember { mutableStateOf("") }
        
        if (displayMedia.isEmpty()) {
            // Remove remember to ensure fresh calculation every time EmptyState is shown
            val remainingCount = run {
                var filtered = allMedia
                if (filterFolder != "All") {
                    filtered = filtered.filter { it.bucketName == filterFolder }
                }
                if (filterType != "All") {
                    filtered = filtered.filter { 
                        when (filterType) {
                            "Image" -> it.type == "photo" && !it.isLivePhoto
                            "Video" -> it.type == "video"
                            "Live" -> it.isLivePhoto
                            else -> true
                        }
                    }
                }
                if (filterMonth != "All") {
                    val cal = Calendar.getInstance()
                    filtered = filtered.filter { 
                        cal.timeInMillis = it.dateAdded * 1000L
                        val year = cal.get(Calendar.YEAR)
                        val month = cal.get(Calendar.MONTH) + 1
                        val key = "$year-${month.toString().padStart(2, '0')}"
                        key == filterMonth
                    }
                }
                filtered.count { !prefs.processedIds.contains(it.id) }
            }

            EmptyState(
                onRestart = { loadNextBatch(playEntryAnimation = true) },
                pendingTrashCount = pendingTrashCount,
                onViewTrash = { showDeleteModal = true },
                filterFolder = filterFolder,
                filterType = filterType,
                filterMonth = filterMonth,
                remainingCount = remainingCount,
                isFullAlbumDone = isFullAlbumDone,
                onResetFilter = { /* Deprecated, use onReleaseKept */ },
                onReleaseKept = {
                    // Logic to release all processed items matching current filter
                    // 1. Get current processed IDs
                    val processed = prefs.processedIds
                    val liked = prefs.likedIds
                    val pendingTrash = prefs.pendingTrashIds
                    
                    // 2. Filter all media by current filter criteria
                    var targetMedia = allMedia
                    if (filterFolder != "All") {
                        targetMedia = targetMedia.filter { it.bucketName == filterFolder }
                    }
                    if (filterType != "All") {
                        targetMedia = targetMedia.filter { 
                            when (filterType) {
                                "Image" -> it.type == "photo" && !it.isLivePhoto
                                "Video" -> it.type == "video"
                                "Live" -> it.isLivePhoto
                                else -> true
                            }
                        }
                    }
                    
                    // 3. Identify items to release (Processed AND matching filter AND NOT trashed AND NOT liked AND NOT moved)
                    val toRelease = targetMedia.filter { 
                        processed.contains(it.id) && 
                        !liked.contains(it.id) && 
                        !pendingTrash.contains(it.id) &&
                        !prefs.movedIds.contains(it.id)
                    }
                    
                    if (toRelease.isNotEmpty()) {
                        val toReleaseIds = toRelease.map { it.id }.toSet()
                        processed.removeAll(toReleaseIds)
                        prefs.processedIds = processed
                        
                        // Update stats
                        val newStats = stats.copy(
                            processed = (stats.processed - toRelease.size).coerceAtLeast(0),
                            skipped = (stats.skipped - toRelease.size).coerceAtLeast(0)
                        )
                        stats = newStats
                        prefs.stats = newStats
                        
                        // Reload
                        loadNextBatch()
                    } else {
                        loadNextBatch()
                    }
                },
                onViewLikes = { showLikes = true },
                onChangeFilter = {
                    showFilter = true
                }
            )
        } else {
            // Card Stack
            
            // Calculate Folder Targets
            // var folderUsageState by remember { mutableStateOf(prefs.folderUsage) } // Moved up to VoiceLikeApp scope
            // var folderLastUsedState by remember { mutableStateOf(prefs.folderLastUsed) } // Need this for reactive sorting?
            // Actually, we can just read prefs.folderLastUsed inside the LaunchedEffect or pass it as state if we want instant UI updates.
            // Let's create a local state for it to trigger sorting updates.
            val folderLastUsedState = remember { mutableStateOf(prefs.folderLastUsed) }

    LaunchedEffect(allMedia, displayMedia, folderUsageState, folderLastUsedState.value) {
        withContext(Dispatchers.Default) {
            val currentItem = displayMedia.firstOrNull()
            val currentFolder = currentItem?.bucketName
            val lastUsedMap = folderLastUsedState.value
            
            // Group by bucket, exclude empty or non-image folders if needed
            // For now, show all folders that have at least one item
            val targets = allMedia.groupBy { it.bucketName }
                .filter { (bucketId, _) -> bucketId != currentFolder } // Exclude current folder (bucketId)
                .map { (bucketId, items) ->
                    val firstPath = items.firstOrNull()?.path.orEmpty()
                    val relFolder = if (firstPath.isNotEmpty()) {
                        val root = android.os.Environment.getExternalStorageDirectory().absolutePath
                        val normalized = if (firstPath.startsWith(root)) firstPath.substring(root.length).trimStart('/') else firstPath
                        normalized.substringBeforeLast("/")
                    } else {
                        "Pictures"
                    }
                    val displayName = items.firstOrNull()?.bucketName?.takeIf { it.isNotBlank() }
                        ?: relFolder.substringAfterLast("/")
                    FolderData(
                        id = bucketId, // bucketId as stable identifier
                        name = displayName, // friendly folder name
                        path = relFolder, // MediaStore RELATIVE_PATH like "DCIM/Camera"
                        count = items.size
                    )
                }
                .sortedWith(
                    // Scientific Sorting Logic:
                    // 1. Recently Used (Last 5 minutes) -> High Priority
                    // 2. Usage Frequency -> High Priority
                    // 3. Item Count -> Low Priority
                    compareByDescending<FolderData> { 
                        // Check if used in last 5 minutes (300,000 ms)
                        val lastUsed = lastUsedMap[it.id] ?: 0L
                        val isRecent = (System.currentTimeMillis() - lastUsed) < 300_000
                        if (isRecent) 1 else 0
                    }.thenByDescending { 
                        folderUsageState[it.id] ?: 0 
                    }.thenByDescending { it.count }
                )
            folderDropTargets = targets
        }
    }

    LaunchedEffect(folderDropTargets) {
        folderDropTargetRects = emptyMap()
    }

    LaunchedEffect(isFolderDropMode) {
        if (!isFolderDropMode) {
            folderDropTargetRects = emptyMap()
            activeDropTargetId = null
        }
    }

    // Collision Detection
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }

    LaunchedEffect(topCardOffset, isFolderDropMode, folderDropTargetRects) {
        if (isFolderDropMode && folderDropTargetRects.isNotEmpty()) {
            val cardCenterX = (screenWidthPx / 2) + topCardOffset.x
            val cardCenterY = (screenHeightPx / 2) + topCardOffset.y
            
            // Find closest folder by X distance
            // Fix: Filter out ghost rects (folders that are no longer visible/valid targets)
            val validTargetIds = folderDropTargets.map { it.id }.toSet() + "new_folder"
            val validRects = folderDropTargetRects.filterKeys { it in validTargetIds }
            
            val bandPadding = 90f
            val inBand = validRects.filterValues { rect ->
                cardCenterY >= rect.top - bandPadding && cardCenterY <= rect.bottom + bandPadding
            }
            val pool = if (inBand.isNotEmpty()) inBand else validRects
            val bestMatch = pool.entries.minByOrNull { (_, rect) ->
                val dx = cardCenterX - rect.center.x
                val dy = cardCenterY - rect.center.y
                (dx * dx) + (dy * dy)
            }
            
            if (bestMatch != null) {
                val dist = kotlin.math.sqrt(
                    (cardCenterX - bestMatch.value.center.x) * (cardCenterX - bestMatch.value.center.x) +
                        (cardCenterY - bestMatch.value.center.y) * (cardCenterY - bestMatch.value.center.y)
                )
                // Threshold: 300px (approx 100dp) radius around center is "sticky"
                // This allows selecting even if not perfectly aligned
                // Also require that we are not swiping too far horizontally (Left/Right swipe intent)
                // If |offsetX| > 500 (threshold for left/right swipe), ignore drop target
                val isHorizontalSwipe = kotlin.math.abs(topCardOffset.x) > 500f

                if (!isHorizontalSwipe && (dist < 280f || inBand.isNotEmpty())) {
                    activeDropTargetId = bestMatch.key
                } else {
                    activeDropTargetId = null
                }
            } else {
                activeDropTargetId = null
            }
        } else {
            activeDropTargetId = null
        }
    }

    val folderListState = rememberLazyListState()

    // Edge Scrolling Logic
    LaunchedEffect(isFolderDropMode) {
        if (isFolderDropMode) {
            val edgeThreshold = 100f
            val minScrollSpeed = 6f
            val maxScrollSpeed = 36f
            
            while (isActive) {
                // Card Center X (approximate)
                val cardCenterX = (screenWidthPx / 2) + topCardOffset.x
                
                // Right Edge
                val scrollSpeed = calculateEdgeScrollSpeed(
                    cardCenterX = cardCenterX,
                    screenWidthPx = screenWidthPx,
                    edgeThreshold = edgeThreshold,
                    minSpeed = minScrollSpeed,
                    maxSpeed = maxScrollSpeed
                )
                if (scrollSpeed != 0f) {
                    folderListState.scrollBy(scrollSpeed)
                }
                
                delay(16) // ~60fps
            }
        }
    }



    if (showNewFolderDialog) {
        val strings = LocalAppStrings.current
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text(strings.createNewFolder) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text(strings.folderName) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Allow empty input for default name
                        val finalFolderName = if (newFolderName.isBlank()) strings.newFolder else newFolderName
                        
                        // Create folder logic (handled in moveMedia usually creates if not exists)
                        // We just pass the name to the move function
                        val item = displayMedia.firstOrNull()
                        if (item != null) {
                            scope.launch {
                                // Move to new folder
                                // If name doesn't contain path separators, assume it's relative to Pictures or DCIM
                                // But moveMedia expects a relative path (e.g. "Pictures/New Folder/")
                                // Let's construct a reasonable relative path.
                                // Default to Pictures/Name/
                                 val baseDir = if (item.type == "video") android.os.Environment.DIRECTORY_MOVIES else android.os.Environment.DIRECTORY_PICTURES
                                 val destFolder = "$baseDir/${finalFolderName.trim()}/"
                                
                                val intentSender = FileOperationManager.moveMedia(context, listOf(item), destFolder)
                                if (intentSender != null) {
                                    pendingMoveArgs = Triple(listOf(item), destFolder, null)
                                    val originalFile = File(item.path)
                                    val parent = originalFile.parentFile?.absolutePath ?: ""
                                    pendingMoveUiAction = { handleSwipe(UndoAction.Move(item, parent, destFolder)) }
                                    intentSenderLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                                } else {
                                    // Allow "sucked in" animation to play
                                    delay(50)
                                    moveFeedbackMessage = "${strings.moveTo} $destFolder"
                                    
                                    // Update folder usage stats
                                    val currentUsage = prefs.folderUsage
                                    
                                    // Use bucket name (finalFolderName) to match existing folder logic
                                    val newBucketId = finalFolderName.trim()
                                    // Scientific Approach:
                                    // Just initialize usage to 1.
                                    // But update "Last Used" to NOW.
                                    // The sorting logic will prioritize "Recently Used" items.
                                    currentUsage[newBucketId] = 1
                                    prefs.folderUsage = currentUsage
                                    folderUsageState = currentUsage

                                    // Update Last Used
                                    val currentLastUsed = prefs.folderLastUsed
                                    currentLastUsed[newBucketId] = System.currentTimeMillis()
                                    prefs.folderLastUsed = currentLastUsed
                                    
                                    // Mark as user created
                                    val userCreated = prefs.userCreatedFolders
                                    userCreated.add(newBucketId)
                                    prefs.userCreatedFolders = userCreated
                                    
                                    // Update allMedia to reflect the move (Local Update for Immediate Feedback)
                                    val newItem = item.copy(
                                        path = destFolder + item.displayName,
                                        bucketName = finalFolderName
                                    )
                                    
                                    // Replace item in allMedia
                                    // Note: allMedia is immutable list, need to create new one
                                    // This triggers LaunchedEffect(allMedia) which rebuilds folderDropTargets
                                    allMedia = allMedia.map { if (it.id == item.id) newItem else it }
                                    
                                    // Update UI
                                    // Use UndoAction.Move to support undoing the move
                                    val originalFile = File(item.path)
                                    val parent = originalFile.parentFile?.absolutePath ?: ""
                                    handleSwipe(UndoAction.Move(item, parent, destFolder, isNewFolder = true)) // Pass flag for new folder
                                }
                            }
                        }
                        showNewFolderDialog = false
                        newFolderName = ""

                    }
                ) {
                    Text(strings.create)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 0.dp),
                contentAlignment = Alignment.Center
            ) {
                // --- Global Edge Glow Effects (Rendered BEHIND the stack) ---
                if (topCardOffset != Offset.Zero && !isUndoAnimating) {
                    val threshold = 200f
                    val dragX = topCardOffset.x
                    val dragY = topCardOffset.y
                    val absX = kotlin.math.abs(dragX)
                    val absY = kotlin.math.abs(dragY)
                    
                    val isHorizontal = absX > absY
                    val isVertical = absY > absX
                    
                    val maxOpacity = 0.6f
                    val fadeScale = 2f
                    val tX = (absX / (threshold * fadeScale)).coerceIn(0f, 1f)
                    val easedX = tX * tX * (3f - 2f * tX)
                    val tY = (absY / (threshold * fadeScale)).coerceIn(0f, 1f)
                    val easedY = tY * tY * (3f - 2f * tY)
                    
                    if (isHorizontal && dragX > 0 && !isFolderDropMode) { // Right (Like - Orange)
                         val opacity = easedX * maxOpacity
                         Box(
                             modifier = Modifier
                                 .align(Alignment.CenterEnd)
                                 .fillMaxHeight() // Full height coverage
                                .fillMaxWidth(0.8f)
                                 .graphicsLayer { alpha = opacity }
                                 .background(
                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color(0xFFFF9800).copy(alpha = 0.9f)
                                        )
                                    )
                                 )
                         )
                    } else if (isHorizontal && dragX < 0 && !isFolderDropMode) { // Left (Delete - Red)
                         val opacity = easedX * maxOpacity
                         Box(
                             modifier = Modifier
                                 .align(Alignment.CenterStart)
                                 .fillMaxHeight() // Full height coverage
                                .fillMaxWidth(0.8f)
                                 .graphicsLayer { alpha = opacity }
                                 .background(
                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFFEF4444).copy(alpha = 0.9f),
                                            Color.Transparent
                                        )
                                    )
                                 )
                         )
                    } else if (isVertical && dragY < 0) { // Top (Skip - Blue)
                         val opacity = easedY * maxOpacity
                         Box(
                             modifier = Modifier
                                 .align(Alignment.TopCenter)
                                 .fillMaxWidth() // Full width coverage
                                 .fillMaxHeight(0.4f)
                                 .graphicsLayer { alpha = opacity }
                                 .background(
                                     brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                         colors = listOf(Color(0xFF3B82F6), Color.Transparent)
                                     )
                                 )
                         )
                    }
                    // Bottom (Move) glow removed as per user request
                }
            
                // Render bottom to top
                // Visible stack size: 3 cards (for clean UI)
                // But for entrance animation, we might want to animate more "virtual" cards?
                // Actually, if we only render 3, we can only animate 3.
                // To have "more animation cards" but "3 stack cards", we need to render more but hide them or scale them to nothing?
                // Or user means: The *animation sequence* should feel like many cards flying in, but they settle into a stack of 3.
                // If we render 6 cards, the bottom ones are hidden behind top ones anyway.
                // The issue "too many piled up" means the visual stack offset/scale accumulation is too much.
                // We can render 6 cards but CLAMP the visual offset/scale for index > 2 to look like they are just part of the 3rd card.
                // This allows 6 cards to "fly in" individually, but visually settle into a clean 3-card stack.
                
                val visibleItems = displayMedia.take(6).reversed()
                
                visibleItems.forEachIndexed { index, item ->
                    val isTopCard = index == visibleItems.lastIndex
                    val rawStackIndex = visibleItems.lastIndex - index 
                    
                    // Visual Stack Logic: Clamp to max 3 visible layers
                    // Cards 4, 5, 6... will look identical to Card 3 (bottom of stack)
                    // This creates the "infinite deck" look without visual clutter
                    // val visualStackIndex = rawStackIndex.coerceAtMost(2) // Handled inside DraggableCard now
                    
                    val shouldAnalyze = rawStackIndex <= 1 // Pre-analyze current and next card
                    
                    // Entrance Animation Values
                    val entranceTranslationY = if (isInitialLoad) cardEntryOffsets.getOrElse(index) { Animatable(0f) }.value else 0f
                    val entranceRotation = if (isInitialLoad) cardEntryRotations.getOrElse(index) { Animatable(0f) }.value else 0f
                    // Rotation and Scale removed per user request
                    val undoTarget = if (isTopCard) undoAnimationTarget?.takeIf { it.itemId == item.id } else null
                    val isEntryAnimating = isInitialLoad && entranceTranslationY != 0f
                    val disableStackAnimation = isUndoAnimating && !isTopCard

                    key(item.id) {
                        Box(
                            modifier = Modifier.graphicsLayer {
                                translationY = entranceTranslationY
                                rotationZ = entranceRotation
                            }
                        ) {
                            DraggableCard(
                                item = item,
                                isTopCard = isTopCard,
                                stackIndex = rawStackIndex, // Pass RAW index to handle shadow logic correctly
                                muteVideos = muteVideos,
                                pauseMainVideos = pauseMainVideos,
                                shouldAnalyze = shouldAnalyze,
                                blockVideoDuringEntry = isEntryAnimating,
                                disableStackAnimation = disableStackAnimation,
                                suppressActionIndicators = undoTarget != null,
                                undoDirection = undoTarget?.direction,
                                undoToken = undoTarget?.token,
                                onSwipeLeft = { handleSwipe(UndoAction.Trash(item)) },
                                onSwipeRight = { handleSwipe(UndoAction.Like(item)) }, // Like
                                onSwipeUp = { handleSwipe(UndoAction.Keep(item)) },    // Keep
                                onDetail = { viewingItem = item },
                                onSwipeSound = playSwipeSound,
                                onDeleteSound = { playDeleteSound() }, // Pass delete sound
                                onLikeSound = { playLikeSound() }, // Pass like sound
                                onDragStart = { isDragging = true },
                                onDragCancel = { isDragging = false },
                                onDragUpdate = if (isTopCard) { x, y -> topCardOffset = Offset(x, y) } else null,
                                onDragEnd = if (isTopCard) { x, y ->
                                    // Capture state BEFORE resetting isDragging
                                    val wasInDropMode = isFolderDropMode
                                    val targetId = activeDropTargetId
                                    
                                    isDragging = false // Reset UI state
                                    
                                    // Always consume if we are in Drop Mode (prevent swipe triggers)
                                    if (wasInDropMode) {
                                        if (targetId != null) {
                                            // Handle Drop
                                            if (targetId == "new_folder") {
                                                showNewFolderDialog = true
                                            } else {
                                                // Move to existing folder
                                                scope.launch {
                                                    val rawTargetFolder = folderDropTargets.find { it.id == targetId }?.path ?: targetId
                                                    val targetFolder = rawTargetFolder.trim().trimEnd('/') + "/"
                                                    
                                                    // Update Usage Count
                                                    val currentUsage = prefs.folderUsage.toMutableMap() // Copy to trigger state change
                                                    currentUsage[targetId] = (currentUsage[targetId] ?: 0) + 1
                                                    prefs.folderUsage = currentUsage
                                                    folderUsageState = currentUsage

                                                    // Update Last Used Timestamp (for scientific sorting)
                                                    val currentLastUsed = prefs.folderLastUsed.toMutableMap()
                                                    currentLastUsed[targetId] = System.currentTimeMillis()
                                                    prefs.folderLastUsed = currentLastUsed
                                                    
                                                    val intentSender = FileOperationManager.moveMedia(context, listOf(item), targetFolder)
                                                    if (intentSender != null) {
                                                        pendingMoveArgs = Triple(listOf(item), targetFolder, null)
                                                        val originalFile = File(item.path)
                                                        val parent = originalFile.parentFile?.absolutePath ?: ""
                                                        pendingMoveUiAction = { handleSwipe(UndoAction.Move(item, parent, targetFolder)) }
                                                        intentSenderLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                                                    } else {
                                                        // Allow "sucked in" animation to play
                                                        delay(50)
                                                        moveFeedbackMessage = "${strings.moveTo} $targetFolder"
                                                        
                                                        // Update allMedia to reflect change for folder counts
                                                        // This is crucial for the "Folder Drop Row" counts to update immediately
                                                        val newItem = item.copy(
                                                            path = targetFolder + File(item.path).name,
                                                            bucketName = folderDropTargets.find { it.id == targetId }?.name ?: targetId,
                                                            // bucketId is tricky, ideally we reload, but for count we can use targetId if it is the bucketId
                                                            // or we just assume the group logic handles it.
                                                            // Actually, folderDropTargets logic uses bucketName for display but GROUPS by bucketId.
                                                            // If targetId is a bucketId (which it is for existing folders), we should update bucketId.
                                                            // But MediaItem doesn't expose bucketId as a var? It's a val.
                                                            // And we don't know the new bucketId hash.
                                                            // BUT, our folderDropTargets logic groups by `bucketName`?
                                                            // Let's check line 1881: `allMedia.groupBy { it.bucketName }`
                                                            // Line 1882: `filter { (bucketId, _) -> ...`. Here "bucketId" is actually "bucketName" (key of group).
                                                            // So we just need to update bucketName!
                                                        )
                                                        // Update allMedia list
                                                        allMedia = allMedia.map { if (it.id == item.id) newItem else it }
                                                        
                                                        // Calculate path for undo
                                                        val originalFile = File(item.path)
                                                        val parent = originalFile.parentFile?.absolutePath ?: ""
                                                        handleSwipe(UndoAction.Move(item, parent, targetFolder)) // Remove from stack
                                                    }
                                                }
                                            }
                                            true // Consumed (Success - DraggableCard should animate disappear)
                                        } else {
                                            false // Not consumed (Miss - DraggableCard should reset)
                                        }
                                    } else {
                                        false // Not consumed, let card handle swipe
                                    }
                                } else null
                            )
                        }
                    }
                }

                // Folder Drop Row (Bottom)
    FolderDropRow(
        visible = isFolderDropMode,
        folders = folderDropTargets,
        activeDropTargetId = activeDropTargetId,
        onFolderPositionsChanged = { newRects ->
            folderDropTargetRects = newRects
        },
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        listState = folderListState
    )
    
    // Reset Scroll Position when appearing
    LaunchedEffect(isFolderDropMode) {
        if (isFolderDropMode) {
            folderListState.scrollToItem(0)
        }
    }
            }
        }

        // Top Controls
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 24.dp, end = 24.dp)
        ) {
            // Stats Button - Blurred Circle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(40.dp)
                    .bounceClick(onClick = { 
                        if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showStatsOverlay = true 
                    })
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.BarChart,
                    contentDescription = "Stats",
                    tint = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
            
            // Batch Counter
            if (displayMedia.isNotEmpty()) {
                // Logic:
                // denominator = batchSize (user preference)
                // remaining = displayMedia.size
                // done = batchSize - remaining
                // If total available < batchSize (e.g. last batch), denominator should probably be the initial load size?
                // But loadNextBatch always takes batchSize.
                // However, if we are near the end, displayMedia might be smaller than batchSize initially.
                // Let's rely on currentBatchTotal which tracks the initial size of the current batch.
                
                // If the batch was smaller than the preference (end of list), use that smaller number as denominator.
                val denominator = currentBatchTotal
                val remaining = displayMedia.size
                val done = (denominator - remaining).coerceAtLeast(0) // Ensure no negative numbers
                
                // Format: Done / Total (e.g., 0/5)
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SlideText(
                            targetValue = done,
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = Color.Black.copy(alpha = 0.6f)
                        )
                        Text(
                            text = " / $denominator",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black.copy(alpha = 0.6f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                
                    // Current Folder (Added below batch counter)
                    if (isFolderDropMode) {
                        val currentFolder = displayMedia.firstOrNull()?.bucketName ?: ""
                        if (currentFolder.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${strings.currentFolder}: $currentFolder",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.Black.copy(alpha = 0.5f)
                            )
                        }
                    } else if (filterFolder != "All" || filterType != "All" || filterMonth != "All") {
                        // Filter Status
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.05f))
                                .bounceClick(onClick = { 
                                    if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Reset Filter
                                    filterFolder = "All"
                                    prefs.filterFolder = "All"
                                    filterType = "All"
                                    prefs.filterType = "All"
                                    filterMonth = "All"
                                    prefs.filterMonth = "All"
                                    loadNextBatch(playEntryAnimation = true)
                                })
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            val textList = mutableListOf<String>()
                            if (filterFolder != "All") textList.add(filterFolder)
                            if (filterType != "All") {
                                textList.add(when(filterType) {
                                    "Image" -> strings.filterImage
                                    "Video" -> strings.filterVideo
                                    "Live" -> strings.filterLive
                                    else -> filterType
                                })
                            }
                            if (filterMonth != "All") textList.add(filterMonth)
                            
                            Text(
                                text = textList.joinToString(" • "),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.Black.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear",
                                tint = Color.Black.copy(alpha = 0.6f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            // Clean Button - Red Tint
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                AnimatedVisibility(visible = pendingTrashCount > 0) {
                    Button(
                        onClick = { showDeleteModal = true },
                        modifier = Modifier.bounceClick(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEF2F2)), // red-50
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA)), // red-200
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Clean",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        SlideText(
                            targetValue = pendingTrashCount,
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFFDC2626)
                        )
                    }
                }
            }
        }

        val shareTarget = displayMedia.firstOrNull()

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 60.dp, start = 24.dp)
        ) {
            AnimatedVisibility(
                visible = shareTarget != null && !isFolderDropMode,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                Button(
                    onClick = { 
                        if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        shareTarget?.let { shareMedia(context, it) } 
                    },
                    modifier = Modifier.bounceClick(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(30.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black.copy(alpha = 0.1f)),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share",
                        tint = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Filter Button (Bottom Right - Symmetric)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 60.dp, end = 24.dp)
        ) {
            AnimatedVisibility(
                visible = !isFolderDropMode, // Always visible if permissions granted, unless dragging down
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                Button(
                    onClick = {
                        if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showFilter = true
                    },
                    modifier = Modifier.bounceClick(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(30.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black.copy(alpha = 0.1f)),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FilterList,
                        contentDescription = "Filter",
                        tint = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Bottom Undo Button (Centered)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = undoStack.isNotEmpty() && !isFolderDropMode,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                Button(
                    onClick = { handleUndo() },
                    modifier = Modifier.bounceClick(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(30.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black.copy(alpha = 0.1f)),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Replay,
                        contentDescription = strings.undo,
                        tint = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(strings.undo, color = Color.Black.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Stats Sub-page
        PageAnimatedVisibility(
            visible = showStatsOverlay,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            val isFolderDropModeState = isFolderDropMode // Capture state
            val showStatsOverlayState = showStatsOverlay
            PredictiveBackWrapper(
                isVisible = showStatsOverlayState,
                onBack = { 
                    showStatsOverlay = false
                    hasPlayedStatsAnimation = true 
                }
            ) {
                // Determine if we should animate this time
                val shouldAnimate = true
                
                // Check if any sub-page is open (Deep Organize, Monthly, Likes, Settings)
                // Note: Some are overlays on top of StatsOverlay, so we check their visibility states
                val isSubPageOpen = showDeepOrganize || showMonthly || showLikes || showSettings || showLanguage || showPrivacy || showFilter || showVideoCompress || showImageCompress || showShootingStats

                // Optimization: Calculate totalSize in background to avoid frame drops
                val totalSize by produceState(initialValue = 0L, key1 = allMedia) {
                    value = withContext(Dispatchers.Default) {
                        allMedia.sumOf { it.size }
                    }
                }

                StatsOverlay(
                    stats = stats, 
                    totalPhotos = allMedia.size, 
                    likedCount = prefs.likedIds.size,
                    allMedia = allMedia,
                    onDismiss = { 
                        showStatsOverlay = false
                        hasPlayedStatsAnimation = true 
                    },
                    onDeepOrganizeClick = { showDeepOrganize = true },
                    onShootingStatsClick = { showShootingStats = true },
                    onMonthlyClick = { showMonthly = true },
                    onLikesClick = { showLikes = true }, // Trigger Likes view
                    onSettingsClick = { showSettings = true },
                    totalSize = totalSize,
                    shouldAnimate = shouldAnimate,
                    isSubPageOpen = isSubPageOpen,
                    isFolderDropMode = isFolderDropModeState, // Use captured state
                    enableHaptics = !hasPlayedStatsAnimation
                )
            }
        }

        PageAnimatedVisibility(
            visible = showShootingStats,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            PredictiveBackWrapper(
                isVisible = showShootingStats,
                onBack = { showShootingStats = false }
            ) {
                ShootingStatsPage(
                    allMedia = allMedia,
                    likedIds = prefs.likedIds,
                    onBack = { showShootingStats = false }
                )
            }
        }

        PageAnimatedVisibility(
            visible = showDeepOrganize,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            PredictiveBackWrapper(
                isVisible = showDeepOrganize,
                onBack = { showDeepOrganize = false }
            ) {
                DeepOrganizeView(
                    allMedia = allMedia,
                    onBack = { showDeepOrganize = false },
                    onSimilarClick = { showSimilarPhotos = true },
                    onLowQualityClick = { showLowQuality = true },
                    onVideoCompressClick = { showVideoCompress = true },
                    onImageCompressClick = { showImageCompress = true },
                    totalMediaCount = allMedia.size
                )
            }
        }


        
        PageAnimatedVisibility(
            visible = showVideoCompress,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            PredictiveBackWrapper(
                isVisible = showVideoCompress,
                onBack = { showVideoCompress = false }
            ) {
                VideoCompressionView(
                    videos = allMedia.filter { it.type == "video" },
                    onBack = { showVideoCompress = false }
                )
            }
        }

        PageAnimatedVisibility(
            visible = showImageCompress,
            enter = FluidTransitions.SheetEnter,
            exit = FluidTransitions.SheetExit
        ) {
            PredictiveBackWrapper(
                isVisible = showImageCompress,
                onBack = { showImageCompress = false }
            ) {
                ImageCompressionView(
                    photos = allMedia.filter { it.type == "photo" },
                    onBack = { showImageCompress = false }
                )
            }
        }

        // Settings View
        PageAnimatedVisibility(
            visible = showSettings,
            enter = FluidTransitions.SheetEnter,
            exit = FluidTransitions.SheetExit
        ) {
            PredictiveBackWrapper(
                isVisible = showSettings,
                onBack = { showSettings = false }
            ) {
                SettingsView(
                    batchSize = batchSize,
                    queueOrder = queueOrder,
                    muteVideos = muteVideos,
                    soundEffectsEnabled = soundEffectsEnabled,
                    hapticsEnabled = hapticsEnabled,
                    avatarType = avatarType,
                        onBatchSizeChange = { newSize ->
                        batchSize = newSize
                        prefs.batchSize = newSize
                        pendingBatchSizeChange = true
                    },
                    onQueueOrderChange = { newOrder ->
                        queueOrder = newOrder
                        prefs.queueOrder = newOrder
                        pendingQueueOrderChange = true
                    },
                    onMuteVideosChange = { isMuted ->
                        muteVideos = isMuted
                        prefs.muteVideos = isMuted
                    },
                    onSoundEffectsChange = { isEnabled ->
                        soundEffectsEnabled = isEnabled
                        prefs.soundEffectsEnabled = isEnabled
                    },
                    onHapticsEnabledChange = { isEnabled ->
                        hapticsEnabled = isEnabled
                        prefs.hapticsEnabled = isEnabled
                    },
                    onAvatarChange = { type ->
                        avatarType = type
                        prefs.avatarType = type
                    },
                    onLanguageClick = { showLanguage = true },
                    onPrivacyClick = {
                        showPrivacy = true
                    },
                    onOpenSourceClick = {
                        showOpenSourceLicenses = true
                    },
                    onReleaseKept = {
                        val processed = prefs.processedIds
                        val liked = prefs.likedIds
                        val pendingTrash = prefs.pendingTrashIds
                        
                        val toRelease = processed.filter { !liked.contains(it) && !pendingTrash.contains(it) }.toSet()
                        
                        if (toRelease.isNotEmpty()) {
                            processed.removeAll(toRelease)
                            prefs.processedIds = processed
                            
                            val newStats = stats.copy(
                                processed = (stats.processed - toRelease.size).coerceAtLeast(0),
                                skipped = (stats.skipped - toRelease.size).coerceAtLeast(0)
                            )
                            stats = newStats
                            prefs.stats = newStats
                            
                            // Use loadNextBatch with animation to provide feedback for released items
                            loadNextBatch(playEntryAnimation = true)
                        }
                    },
                    onBack = { showSettings = false }
                )
            }
        }

        // Privacy View (Moved here to be on top of Settings)
        PageAnimatedVisibility(
            visible = showPrivacy,
            enter = FluidTransitions.ParallaxPushEnter,
            exit = FluidTransitions.ParallaxPopExit
        ) {
            PredictiveBackWrapper(
                isVisible = showPrivacy,
                onBack = { showPrivacy = false }
            ) {
                PrivacyView(onBack = {
                    showPrivacy = false
                })
            }
        }

        // Open Source Licenses View
        PageAnimatedVisibility(
            visible = showOpenSourceLicenses,
            enter = FluidTransitions.ParallaxPushEnter,
            exit = FluidTransitions.ParallaxPopExit
        ) {
            PredictiveBackWrapper(
                isVisible = showOpenSourceLicenses,
                onBack = { showOpenSourceLicenses = false }
            ) {
                OpenSourceLicensesView(onBack = {
                    showOpenSourceLicenses = false
                })
            }
        }

        // Filter View
        PageAnimatedVisibility(
            visible = showFilter,
            enter = FluidTransitions.SheetEnter,
            exit = FluidTransitions.SheetExit
        ) {
            val showFilterState = showFilter
            PredictiveBackWrapper(
                isVisible = showFilterState,
                onBack = { showFilter = false }
            ) {
                // Calculate available buckets with previews
                val folderList = remember(allMedia, prefs.processedIds, filterMonth) {
                    val processed = prefs.processedIds
                    var pending = allMedia.filter { !processed.contains(it.id) }
                    
                    // Filter by Month if selected (to show relevant folders)
                    if (filterMonth != "All") {
                        val cal = Calendar.getInstance()
                        pending = pending.filter { 
                            cal.timeInMillis = it.dateAdded * 1000L
                            val year = cal.get(Calendar.YEAR)
                            val month = cal.get(Calendar.MONTH) + 1
                            val key = "$year-${month.toString().padStart(2, '0')}"
                            key == filterMonth
                        }
                    }
                    
                    // Group by bucket
                    val grouped = pending.groupBy { it.bucketName }
                    
                    // Filter < 10, Sort by size desc
                    val sortedGroups = grouped.entries
                        .filter { it.value.size >= 10 }
                        .sortedByDescending { it.value.size }
                    
                    val list = sortedGroups.map { (name, items) ->
                        FilterFolderData(
                            name = name,
                            count = items.size,
                            previews = items.take(7)
                        )
                    }.toMutableList()
                    
                    // Add All option
                    if (pending.isNotEmpty()) {
                        list.add(0, FilterFolderData(
                            name = "All",
                            count = pending.size,
                            previews = pending.take(7)
                        ))
                    }
                    list
                }
                
                FilterView(
                    currentFolder = filterFolder,
                    currentType = filterType,
                    currentMonth = filterMonth,
                    folderList = folderList,
                    onApply = { folder, type, month ->
                        filterFolder = folder
                        prefs.filterFolder = folder
                        filterType = type
                        prefs.filterType = type
                        filterMonth = month
                        prefs.filterMonth = month
                        loadNextBatch(playEntryAnimation = true)
                        showFilter = false
                    },
                    onBack = { showFilter = false },
                    onSelectMonth = { showMonthly = true }, // Open MonthlyListView
                    onClearMonth = {
                        filterMonth = "All"
                        prefs.filterMonth = "All"
                        loadNextBatch()
                    }
                )
            }
        }
        
        // Monthly Selection View (Reused from old Monthly feature)
        PageAnimatedVisibility(
            visible = showMonthly,
            enter = FluidTransitions.ParallaxPushEnter,
            exit = FluidTransitions.ParallaxPopExit
        ) {
            PredictiveBackWrapper(
                isVisible = showMonthly,
                onBack = { showMonthly = false }
            ) {
                // If monthlyReviewList is empty, we are in Selection Mode (List of Months)
                // If it's not empty, we are in Review Mode (Old logic). 
                // But now we want Selection Mode to just SELECT the month and return to Filter/Main.
                
                // We need to differentiate: Are we here for "Monthly Organize" (Old) or "Filter Selection" (New)?
                // User said: "Monthly Organize is no longer a separate feature, but a filter way".
                // So when we click a month, we should SET FILTER and GO BACK.
                
                MonthlyListView(
                    allMedia = allMedia,
                    processedIds = prefs.processedIds,
                    currentMonth = filterMonth,
                    onMonthClick = { items -> 
                        // New Logic: Extract Month Key from the first item and set as Filter
                        if (items.isNotEmpty()) {
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = items[0].dateAdded * 1000L
                            val year = cal.get(Calendar.YEAR)
                            val month = cal.get(Calendar.MONTH) + 1
                            val key = "$year-${month.toString().padStart(2, '0')}"
                            
                            filterMonth = key
                            prefs.filterMonth = key
                            
                            // Reset other filters? User might want to combine.
                            // But usually selecting a month implies "Show me THIS month".
                            // Let's keep others or reset? 
                            // If we reset, we lose "Video only in May".
                            // Let's keep others.
                            
                            loadNextBatch()
                            showMonthly = false
                            // If we came from FilterView, should we go back to FilterView or Main?
                            // User said "Month page as a subpage of filter".
                            // So we should probably go back to FilterView?
                            // But "MonthlyListView" takes full screen.
                            // If we just set `showMonthly = false`, we reveal what's under it.
                            // If `showFilter` is true, we reveal FilterView.
                            // Yes, `showFilter` is still true (it's under `showMonthly`).
                            // So closing `showMonthly` reveals `showFilter`.
                            // But we also want to update `selectedMonth` in FilterView state.
                            // `FilterView` uses `currentMonth` param which comes from `filterMonth` state in Activity.
                            // Since we updated `filterMonth` state, `FilterView` will recompose with new month selected.
                            // Perfect.
                        }
                    },
                    onAllClick = {
                        filterMonth = "All"
                        prefs.filterMonth = "All"
                        loadNextBatch()
                        showMonthly = false
                    },
                    onBack = { showMonthly = false }
                )
            }
        }

        // Language Selection View
        PageAnimatedVisibility(
            visible = showLanguage,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            PredictiveBackWrapper(
                isVisible = showLanguage,
                onBack = { showLanguage = false }
            ) {
                LanguageView(
                    currentLanguage = currentLanguage,
                    onLanguageSelected = { lang ->
                        currentLanguage = lang
                        prefs.language = lang
                        showLanguage = false // Close language view, revealing Settings below
                    },
                    onBack = { showLanguage = false } // Just close language view
                )
            }
        }


        // Monthly View
    fun handleMonthlyUndo(
        undoStack: List<UndoAction>, 
        updateUndoStack: (List<UndoAction>) -> Unit,
        updateQueue: (MediaItem) -> Unit, // Add back to front of queue
        updateFolderUsageState: (Map<String, Int>) -> Unit
    ) {
        if (undoStack.isEmpty()) return
        val action = undoStack.last()
        updateUndoStack(undoStack.dropLast(1))
        val item = action.item

        // Restore to Queue (at front)
        updateQueue(item)
        
        // Update Persistence
        val processed = prefs.processedIds
        processed.remove(item.id)
        prefs.processedIds = processed
        
        when (action) {
            is UndoAction.Trash -> {
                val pending = prefs.pendingTrashIds
                pending.remove(item.id)
                prefs.pendingTrashIds = pending
                pendingTrashCount = pending.size
                
                val newStats = stats.copy(
                    processed = stats.processed - 1,
                    trashed = stats.trashed - 1,
                    savedSize = stats.savedSize - item.size
                )
                stats = newStats
                prefs.stats = newStats
            }
            is UndoAction.Like -> {
                val liked = prefs.likedIds
                liked.remove(item.id)
                prefs.likedIds = liked
                
                val newStats = stats.copy(
                    processed = stats.processed - 1,
                    skipped = stats.skipped - 1
                )
                stats = newStats
                prefs.stats = newStats
            }
            is UndoAction.Keep -> {
                val newStats = stats.copy(
                    processed = stats.processed - 1,
                    skipped = stats.skipped - 1
                )
                stats = newStats
                prefs.stats = newStats
            }
            is UndoAction.Move -> {
                val newStats = stats.copy(
                    processed = stats.processed - 1,
                    skipped = stats.skipped - 1
                )
                stats = newStats
                prefs.stats = newStats
                
                // If it was a new folder creation, remove the folder from usage stats
                if (action.isNewFolder) {
                    val targetBucketId = File(action.targetFolder).name
                    val currentUsage = prefs.folderUsage
                    currentUsage.remove(targetBucketId)
                    prefs.folderUsage = currentUsage
                    updateFolderUsageState(currentUsage) // Trigger UI update
                } else {
                    // Decrement count for existing folder
                    val targetBucketId = File(action.targetFolder).name
                    val currentUsage = prefs.folderUsage
                    val count = currentUsage[targetBucketId] ?: 0
                    if (count > 0) {
                        currentUsage[targetBucketId] = count - 1
                        prefs.folderUsage = currentUsage
                        updateFolderUsageState(currentUsage) // Trigger UI update
                    }
                }
            }
        }
    }
    
    // Old Monthly View logic removed as it was duplicate
    // We now reuse showMonthly for just the Selection view above
    // And MonthlyReviewView is no longer used directly from main flow, 
    // instead we filter main flow by month.
    
    // However, we need to keep handleMonthlyUndo if it's used elsewhere?
    // It seems it was only used by MonthlyReviewView.
    // Since we removed MonthlyReviewView usage, we can remove this function too if not needed.
    // Wait, the AnimatedVisibility block for showMonthly was duplicated above in my SearchReplace.
    // I need to REMOVE the old block I just read (lines 2227-2300 approx).
    
    // ... Actually, the toolcall result shows I have TWO AnimatedVisibility(visible = showMonthly) blocks now.
    // One I added in previous step (correct one), and one existing one (old one).
    // I must remove the OLD one.

        // Similar Photos View
        PageAnimatedVisibility(
            visible = showSimilarPhotos,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            PredictiveBackWrapper(
                isVisible = showSimilarPhotos,
                onBack = { showSimilarPhotos = false }
            ) {
                SimilarPhotosView(
                    similarGroups = activeSimilarGroups,
                    isScanning = isScanningSimilar,
                    progress = scanProgress,
                    onClose = { showSimilarPhotos = false },
                    onDecision = { idsToDelete, idsProcessed ->
                        // Immediate save to prevent progress loss
                        if (idsToDelete.isNotEmpty()) {
                            val pending = prefs.pendingTrashIds
                            pending.addAll(idsToDelete)
                            prefs.pendingTrashIds = pending
                            pendingTrashCount = pending.size
                            
                            // Update stats
                            val deletedSize = allMedia.filter { idsToDelete.contains(it.id) }.sumOf { it.size }
                            val keptCount = idsProcessed.size - idsToDelete.size
                            
                            val newStats = stats.copy(
                                processed = stats.processed + idsProcessed.size,
                                trashed = stats.trashed + idsToDelete.size,
                                skipped = stats.skipped + keptCount,
                                savedSize = stats.savedSize + deletedSize
                            )
                            stats = newStats
                            prefs.stats = newStats
                        } else if (idsProcessed.isNotEmpty()) {
                            // Keep All Case
                            val newStats = stats.copy(
                                processed = stats.processed + idsProcessed.size,
                                skipped = stats.skipped + idsProcessed.size
                            )
                            stats = newStats
                            prefs.stats = newStats
                        }
                        
                        if (idsProcessed.isNotEmpty()) {
                            val processed = prefs.processedIds
                            processed.addAll(idsProcessed)
                            prefs.processedIds = processed
                        }
                        
                        // Update SimilarPhotoManager internal ignored list
                        SimilarPhotoManager.addIgnoredIds(idsProcessed + idsToDelete)
                        
                        // Immediately update SimilarPhotoManager state to remove processed items from view
                        SimilarPhotoManager.removeProcessedItems(idsProcessed + idsToDelete)
                    },
                    onFinishProcessing = { _, _ ->
                        // Just close, as data is saved incrementally
                        showSimilarPhotos = false
                    }
                )
            }
        }

        PageAnimatedVisibility(
            visible = showLowQuality,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            PredictiveBackWrapper(
                isVisible = showLowQuality,
                onBack = { showLowQuality = false }
            ) {
                LowQualityView(
                    items = lowQualityDisplayItems, // Use Local State
                    isScanning = isScanningLowQuality,
                    progress = scanProgressLowQuality,
                    pendingTrashCount = pendingTrashCount,
                    canUndo = lowQualityUndoStack.isNotEmpty(),
                    muteVideos = muteVideos,
                    onClose = { showLowQuality = false },
                    onSwipe = { item, reason, isTrash -> handleLowQualitySwipe(item, reason, isTrash) },
                    onUndo = { handleLowQualityUndo() },
                    onViewTrash = { showDeleteModal = true }
                )
            }
        }
        
        // Likes View (TikTok Style)
        PageAnimatedVisibility(
            visible = showLikes,
            enter = FluidTransitions.SheetEnter,
            exit = FluidTransitions.SheetExit
        ) {
            PredictiveBackWrapper(
                isVisible = showLikes,
                onBack = { showLikes = false }
            ) {
                // Filter for Liked Items: processed AND marked as LIKED (right swipe)
                // Use produceState to offload heavy filtering/shuffling to background thread
                // Add allMedia.size as key to ensure it updates when media loads
                val likedItems by produceState<List<MediaItem>>(initialValue = emptyList(), key1 = showLikes, key2 = allMedia.size) {
                    if (showLikes) {
                        value = withContext(Dispatchers.Default) {
                            val liked = prefs.likedIds
                            // Convert to Set<Long> explicitly to avoid Gson double/long mismatch issues
                            val likedSet = liked.map { it.toLong() }.toSet()
                            allMedia.filter { likedSet.contains(it.id) }.shuffled()
                        }
                    }
                }
                
                LikesView(
                    likedItems = likedItems,
                    onBack = { showLikes = false },
                    onShare = { item -> shareMedia(context, item) },
                    prefs = prefs, // Pass prefs to handle comments
                    allMedia = allMedia,
                    folderUsage = folderUsageState,
                    userCreatedFolders = prefs.userCreatedFolders
                )
            }
        }

        // Delete Review Modal
        AnimatedVisibility(
            visible = showDeleteModal,
            enter = FluidTransitions.PopEnter,
            exit = FluidTransitions.PopExit
        ) {
            DeleteReviewModal(
                pendingTrashIds = prefs.pendingTrashIds,
                allMedia = allMedia,
                onConfirm = { selectedIds ->
                    // 1. Identify unselected items (to be released)
                    val allPending = prefs.pendingTrashIds
                    val releasedIds = allPending.filter { !selectedIds.contains(it) }

                    // 2. Update stats for released items
                    if (releasedIds.isNotEmpty()) {
                        val releasedCount = releasedIds.size
                        val releasedSize = allMedia.filter { releasedIds.contains(it.id) }.sumOf { it.size }

                        val newStats = stats.copy(
                            trashed = stats.trashed - releasedCount,
                            skipped = stats.skipped + releasedCount, // Move to skipped
                            savedSize = stats.savedSize - releasedSize
                        )
                        stats = newStats
                        prefs.stats = newStats
                        
                        // Remove released items from undo stack if possible, or just leave them (complex)
                        // Ideally we should also remove them from pendingTrashIds which is done below.
                    }

                    // 3. Update pending trash to ONLY selected items
                    prefs.pendingTrashIds = selectedIds.toMutableSet()
                    pendingTrashCount = selectedIds.size

                    // 4. Trigger system delete
                    handleEmptyTrash()
                    
                    showDeleteModal = false
                },
                onCancel = { showDeleteModal = false }
            )
        }

        // Full Screen Viewer
        AnimatedVisibility(
            visible = viewingItem != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            viewingItem?.let { item ->
                FullScreenImageViewer(item = item, onDismiss = { viewingItem = null })
            }
        }

        // Feedback Overlay
        AnimatedVisibility(
            visible = moveFeedbackMessage != null,
            enter = FluidTransitions.SubtleSlideIn,
            exit = FluidTransitions.SubtleSlideOut,
            modifier = Modifier.align(Alignment.Center)
        ) {
            moveFeedbackMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .padding(bottom = 120.dp) // Lift up a bit
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(text = msg, color = Color.White, fontSize = 14.sp)
                }
            }
        }
        
        LaunchedEffect(moveFeedbackMessage) {
            if (moveFeedbackMessage != null) {
                delay(1500) // 1.5s display time (faster than Toast's 2s+ fade)
                moveFeedbackMessage = null
            }
        }
    }
    }
    }
    }
}

@Composable
fun PageAnimatedVisibility(
    visible: Boolean,
    enter: androidx.compose.animation.EnterTransition,
    exit: androidx.compose.animation.ExitTransition,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var openKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(visible) {
        if (visible) {
            openKey++
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = enter,
        exit = exit,
        modifier = modifier
    ) {
        key(openKey) {
            content()
        }
    }
}

@Composable
fun EmptyState(
    onRestart: () -> Unit,
    pendingTrashCount: Int = 0,
    onViewTrash: () -> Unit = {},
    filterFolder: String = "All",
    filterType: String = "All",
    filterMonth: String = "All",
    onResetFilter: () -> Unit = {},
    onChangeFilter: () -> Unit = {},
    onReleaseKept: () -> Unit = {},
    onViewLikes: () -> Unit = {},
    remainingCount: Int = 0,
    isFullAlbumDone: Boolean = false
) {
    val strings = LocalAppStrings.current
    val isFilterActive = filterFolder != "All" || filterType != "All" || filterMonth != "All"
    val isTrulyDone = isFullAlbumDone || (isFilterActive && remainingCount == 0)
    var showReleaseDialog by remember { mutableStateOf(false) }
    var releasedMessageVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val cardOffsetY = remember { Animatable(0f) }
    val cardAlpha = remember { Animatable(1f) }
    var isLeaving by remember { mutableStateOf(false) }

    // Fix for "Blank Screen" bug: If animation played but we are still here (loadNextBatch didn't navigate away or empty result), reset.
    LaunchedEffect(isLeaving, remainingCount) {
        if (isLeaving) {
            // Wait slightly longer than animation (260ms)
            delay(400) 
            // If we are still here, it means we didn't leave (recomposed with new state or same state).
            // Reset to visible.
            isLeaving = false
            cardAlpha.snapTo(1f)
            cardOffsetY.snapTo(0f)
        }
    }

    if (showReleaseDialog) {
        AlertDialog(
            onDismissRequest = { showReleaseDialog = false },
            title = { Text(text = strings.releaseDialogTitle) },
            text = { Text(text = strings.releaseDialogContent) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReleaseDialog = false
                        onReleaseKept()
                        scope.launch {
                            releasedMessageVisible = true
                            delay(2000)
                            releasedMessageVisible = false
                        }
                    }
                ) {
                    Text(strings.confirm, color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReleaseDialog = false }) {
                    Text(strings.cancel, color = Color.Black)
                }
            },
            containerColor = Color.White,
            textContentColor = Color.Black,
            titleContentColor = Color.Black
        )
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.65f)
                .padding(bottom = 64.dp)
                .graphicsLayer {
                    translationY = cardOffsetY.value
                    alpha = cardAlpha.value
                },
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFFF5F5F5), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isTrulyDone) Icons.Outlined.CheckCircle else Icons.Outlined.Refresh,
                        contentDescription = "Status",
                        tint = if (isTrulyDone) Color(0xFF10B981) else Color.Black.copy(alpha = 0.4f),
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = if (isTrulyDone) strings.filterCompletedTitle else strings.emptyStateTitle, 
                    fontSize = 24.sp, 
                    fontFamily = FontFamily.Serif,
                    color = Color.Black.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = if (isTrulyDone) {
                        val typeLabel = when(filterType) {
                            "Image" -> strings.filterImage
                            "Video" -> strings.filterVideo
                            "Live" -> strings.filterLive
                            else -> strings.filterAll
                        }
                        val folderLabel = if (filterFolder == "All") strings.filterAll else filterFolder
                        val monthLabel = if (filterMonth != "All") " • $filterMonth" else ""
                        val conditions = "$folderLabel ($typeLabel)$monthLabel"
                        try {
                            String.format(strings.filterCompletedSubtitle, conditions)
                        } catch (e: Exception) {
                            conditions
                        }
                    } else {
                        if (remainingCount > 0) {
                             try {
                                 String.format(strings.readyForNextBatch, remainingCount)
                             } catch (e: Exception) {
                                 "$remainingCount items"
                             }
                        } else {
                             strings.emptyStateSubtitle
                        }
                    },
                    fontSize = 14.sp,
                    color = Color.Black.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                if (isTrulyDone) {
                    // Option 1: Release Processed (Primary)
                    Button(
                        onClick = { showReleaseDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                         if (releasedMessageVisible) {
                             Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                             Spacer(modifier = Modifier.width(8.dp))
                             Text(strings.released)
                         } else {
                             Text(strings.releaseKept, modifier = Modifier.padding(vertical = 8.dp))
                         }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Option 2: View Favorites (Secondary)
                    OutlinedButton(
                        onClick = onViewLikes,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black.copy(alpha = 0.1f))
                    ) {
                        Text(strings.viewLikes, color = Color.Black, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Option 3: Change Filter (Tertiary)
                    TextButton(
                        onClick = onChangeFilter,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.changeFilter, color = Color.Gray)
                    }
                    
                } else {
                    Button(
                        onClick = {
                            if (isLeaving) return@Button
                            isLeaving = true
                            scope.launch {
                                launch {
                                    cardAlpha.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
                                }
                                cardOffsetY.animateTo(-screenHeightPx, tween(260, easing = FastOutSlowInEasing))
                                onRestart()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLeaving
                    ) {
                        Text(if (remainingCount > 0) strings.continueOrganizing else strings.emptyStateButton, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }

                if (pendingTrashCount > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    TextButton(
                        onClick = onViewTrash,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = strings.viewTrash,
                            color = Color(0xFFEF4444), // Red
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        CelebrationConfetti(intensity = if (isFullAlbumDone) 500 else if (isTrulyDone) 300 else 100)
    }
}

@Composable
fun StatsOverlay(
    stats: AppStats, 
    totalPhotos: Int, 
    likedCount: Int, // New
    allMedia: List<MediaItem>, // Added allMedia for analysis
    onDismiss: () -> Unit, 
    onDeepOrganizeClick: () -> Unit,
    onShootingStatsClick: () -> Unit,
    onLikesClick: () -> Unit,
    onMonthlyClick: () -> Unit,
    onSettingsClick: () -> Unit,
    totalSize: Long,
    shouldAnimate: Boolean = true,
    isSubPageOpen: Boolean = false,
    isFolderDropMode: Boolean,
    enableHaptics: Boolean = true
) {
    val strings = LocalAppStrings.current
    val currentLang = LocalAppLanguage.current
    val haptic = LocalHapticFeedback.current
    val hapticsAllowed = LocalHapticsEnabled.current
    
    // Track if we have already marked animation as played for this session (handled by parent mostly, but we use this to gate local animation)
    // Actually, parent controls `shouldAnimate`. 
    // `isSubPageOpen` controls whether we should STOP immediately.
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White) // High opacity white
            .clickable(enabled = false) {}, // Block clicks
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp) // Reduced horizontal padding
                .padding(top = 40.dp) // Added top padding to shift down
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            val labelStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                color = Color.Black.copy(alpha = 0.5f),
                fontWeight = FontWeight.Medium
            )
            val numberStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 72.sp, // Increased from 56 to 72
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold // Bold numbers
            )
            val unitStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 24.sp, // Smaller unit
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold
            )

            // 1. Processed
            Column(horizontalAlignment = Alignment.Start) {
                RollingText(
                    targetValue = stats.processed,
                    format = { it.toInt().toString() },
                    style = numberStyle,
                    color = Color.Black.copy(alpha = 0.9f),
                    shouldAnimate = shouldAnimate,
                    stopAnimation = isSubPageOpen,
                    enableHaptics = enableHaptics
                )
                Text(
                    text = strings.totalProcessed,
                    style = labelStyle
                )
                if (totalPhotos > 0) {
                     Text(
                        text = "${String.format("%.1f", (stats.processed.toFloat() / totalPhotos) * 100)}% ${strings.ofGallery}",
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        color = Color.Black.copy(alpha = 0.3f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Deleted
            Column(horizontalAlignment = Alignment.Start) {
                RollingText(
                    targetValue = stats.trashed,
                    format = { it.toInt().toString() },
                    style = numberStyle,
                    color = Color(0xFFEF4444), // Red
                    shouldAnimate = shouldAnimate,
                    stopAnimation = isSubPageOpen,
                    enableHaptics = enableHaptics
                )
                Text(
                    text = strings.deleted,
                    style = labelStyle
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Favorites (Likes)
            Column(horizontalAlignment = Alignment.Start) {
                RollingText(
                    targetValue = likedCount,
                    format = { it.toInt().toString() },
                    style = numberStyle,
                    color = Color(0xFFFF9800), // Orange
                    shouldAnimate = shouldAnimate,
                    stopAnimation = isSubPageOpen,
                    enableHaptics = enableHaptics
                )
                Text(
                    text = strings.likes ?: "Likes",
                    style = labelStyle
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Saved Space
            val isGB = stats.savedSize > 1024 * 1024 * 1024
            val spaceVal = if (isGB) {
                stats.savedSize / 1024.0 / 1024.0 / 1024.0
            } else {
                stats.savedSize / 1024.0 / 1024.0
            }
            val spaceUnit = if (isGB) "GB" else "MB"
            
            Column(horizontalAlignment = Alignment.Start) {
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingText(
                        targetValue = spaceVal,
                        format = { String.format("%.1f", it.toFloat()) },
                        style = numberStyle,
                        color = Color(0xFF3B82F6), // Blue
                        shouldAnimate = shouldAnimate,
                        stopAnimation = isSubPageOpen,
                        enableHaptics = enableHaptics
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = spaceUnit,
                        style = unitStyle,
                        color = Color(0xFF3B82F6),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                Text(
                    text = strings.savedSpace,
                    style = labelStyle
                )
                if (totalSize > 0) {
                     val totalSizeGB = totalSize / 1024.0 / 1024.0 / 1024.0
                     Text(
                        text = String.format(strings.savedSpaceRatioFormat, String.format("%.1f", (stats.savedSize.toFloat() / totalSize) * 100), String.format("%.1f", totalSizeGB)),
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        color = Color.Black.copy(alpha = 0.3f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Time Saved
            val timeSaved = stats.trashed * 5 + stats.skipped * 2
            // Split value and unit for Time
            val timeUnit = when {
                timeSaved < 60 -> "s"
                timeSaved < 3600 -> "m"
                else -> "h"
            }
            val timeValNumber: Double = when {
                timeSaved < 60 -> timeSaved.toDouble()
                timeSaved < 3600 -> (timeSaved / 60).toDouble()
                else -> timeSaved / 3600.0
            }
            val timeFormat: (Number) -> String = when {
                timeSaved < 3600 -> { { it.toInt().toString() } }
                else -> { { String.format("%.2f", it.toFloat()) } }
            }
            
            Column(horizontalAlignment = Alignment.Start) {
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingText(
                        targetValue = timeValNumber,
                        format = timeFormat,
                        style = numberStyle,
                        color = Color(0xFF22C55E), // Green
                        shouldAnimate = shouldAnimate,
                        stopAnimation = isSubPageOpen,
                        enableHaptics = enableHaptics
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = timeUnit,
                        style = unitStyle,
                        color = Color(0xFF22C55E),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                Text(
                    text = strings.timeSaved,
                    style = labelStyle
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom Controls Row (Moved here)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 24.dp) // Add some bottom padding
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { 
                                if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onShootingStatsClick() 
                            }
                            .background(Color.Black.copy(alpha = 0.05f))
                            .padding(horizontal = 12.dp, vertical = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CameraAlt,
                            contentDescription = strings.shootingStatsTitle,
                            tint = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.shootingStatsTitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black.copy(alpha = 0.8f)
                        )
                    }
                }
                // Deep Organize Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .bounceClick(onClick = { 
                                if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDeepOrganizeClick() 
                            }) // Moved clickable here
                            .background(Color.Black.copy(alpha = 0.05f))
                            .padding(horizontal = 12.dp, vertical = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = "Deep Organize",
                            tint = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.deepOrganizeTitle ?: "Deep Organize",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black.copy(alpha = 0.8f)
                        )
                    }
                }

                // Monthly Organize Button (Removed, now in Filter)
                /*
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(4.dp)
                ) {
                   // Removed
                }
                */

                // Likes Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .bounceClick(onClick = { 
                                if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLikesClick() 
                            })
                            .background(Color.Black.copy(alpha = 0.05f))
                            .padding(horizontal = 12.dp, vertical = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FavoriteBorder,
                            contentDescription = strings.likes ?: "Likes",
                            tint = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.likes ?: "Likes",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black.copy(alpha = 0.8f)
                        )
                    }
                }

                // Settings Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .bounceClick(onClick = { 
                                if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSettingsClick() 
                            })
                            .background(Color.Black.copy(alpha = 0.05f))
                            .padding(horizontal = 12.dp, vertical = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = strings.settings,
                            tint = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.settings,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
        
        // Close Button (Top Right) - Moved to be LAST to ensure it is ON TOP of the scrollable content
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 24.dp)
                .size(64.dp) // Large hit area
                .clip(CircleShape)
                .bounceClick(onClick = { onDismiss() })
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp) // Visual size
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close",
                    tint = Color.Black.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun FullScreenImageViewer(item: MediaItem, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceAtLeast(1f)
        if (scale > 1f) {
            val maxOffset = (scale - 1f) * 1000f // Rough bound
            offset += offsetChange
            // Optional: Clamp offset
        } else {
            offset = Offset.Zero
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.uri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .transformable(state = transformableState)
        )
    }
}

@Composable
fun DraggableCard(
    item: MediaItem,
    isTopCard: Boolean,
    stackIndex: Int,
    muteVideos: Boolean,
    pauseMainVideos: Boolean,
    shouldAnalyze: Boolean = isTopCard,
    blockVideoDuringEntry: Boolean = false,
    disableStackAnimation: Boolean = false,
    suppressActionIndicators: Boolean = false,
    undoDirection: SwipeDirection? = null,
    undoToken: Long? = null,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeUp: () -> Unit,
    onDetail: () -> Unit,
    onSwipeSound: () -> Unit = {},
    onDeleteSound: () -> Unit = {}, // New callback for delete
    onLikeSound: () -> Unit = {}, // New callback for like
    onDragStart: (() -> Unit)? = null, // New
    onDragUpdate: ((Float, Float) -> Unit)? = null,
    onDragEnd: ((Float, Float) -> Boolean)? = null,
    onDragCancel: (() -> Unit)? = null // New
) {
    val strings = LocalAppStrings.current
    val haptic = LocalHapticFeedback.current
    val hapticsAllowed = LocalHapticsEnabled.current
    
    // Dimensions (Moved up for initial state calculation)
    val config = LocalContext.current.resources.configuration
    val density = LocalDensity.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    // Initial State for Undo Animation (Fixes flash at center before animation starts)
    val initialX = if (undoToken != null && undoDirection != null && isTopCard) {
        when (undoDirection) {
            SwipeDirection.Left -> -screenWidthPx * 1.2f
            SwipeDirection.Right -> screenWidthPx * 1.2f
            else -> 0f
        }
    } else 0f
    
    val initialY = if (undoToken != null && undoDirection != null && isTopCard) {
        when (undoDirection) {
            SwipeDirection.Up -> -screenHeightPx * 1.2f
            SwipeDirection.Down -> screenHeightPx * 1.2f
            else -> 0f
        }
    } else 0f
    
    val initialAlpha = if (undoToken != null && undoDirection != null && isTopCard) 0f else 1f
    val initialScale = if (undoToken != null && undoDirection != null && isTopCard) 0.96f else 1f

    // Animation State
    val offsetX = remember { Animatable(initialX) }
    val offsetY = remember { Animatable(initialY) }
    val alphaAnim = remember { Animatable(initialAlpha) }
    val exitScale = remember { Animatable(initialScale) }

    // Press State for Bounce Effect
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.6f),
        label = "pressScale"
    )

    // Propagate drag updates for Top Card
    if (isTopCard && onDragUpdate != null) {
        val currentX = offsetX.value
        val currentY = offsetY.value
        LaunchedEffect(currentX, currentY) {
            onDragUpdate(currentX, currentY)
        }
    }

    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val currentOnSwipeRight by rememberUpdatedState(onSwipeRight)
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    val currentOnDeleteSound by rememberUpdatedState(onDeleteSound)
    val currentOnLikeSound by rememberUpdatedState(onLikeSound)
    val currentOnSwipeSound by rememberUpdatedState(onSwipeSound)

    val scope = rememberCoroutineScope()
    
    // UI State
    var isExpanded by remember { mutableStateOf(false) }
    
    // Visual Properties
    // Clamp stackIndex to max 2 for visual position/scale (so cards > 2 pile up at the bottom)
    val effectiveStackIndex = stackIndex.coerceAtMost(2)
    val scaleValue = 1f - (effectiveStackIndex * 0.04f)
    val scale by if (disableStackAnimation) {
        rememberUpdatedState(scaleValue)
    } else {
        animateFloatAsState(
            targetValue = scaleValue,
            // Use Spring for stack promotion to handle rapid updates (interruptions) gracefully
            // Stiffness 350f = "MediumLow" (Soft but responsive)
            // Damping 0.75f = "LowBouncy" (Subtle life)
            animationSpec = spring(stiffness = 350f, dampingRatio = 0.75f)
        )
    }
    val yOffsetValue = (effectiveStackIndex * 15).dp
    val yOffset by if (disableStackAnimation) {
        rememberUpdatedState(yOffsetValue)
    } else {
        animateDpAsState(
            targetValue = yOffsetValue,
            animationSpec = spring(stiffness = 350f, dampingRatio = 0.75f)
        )
    }
    
    // Rotation logic
    val rotation = (offsetX.value / 60).coerceIn(-10f, 10f)
    
    // Scale logic for swipe up (Make it thinner/slimmer) AND swipe down (Folder Mode)
    // When offsetY is negative (swiping up), scaleX decreases slightly
    val dynamicScale = if (offsetY.value < 0) {
        (1f + (offsetY.value / 1000f)).coerceAtLeast(0.9f)
    } else if (offsetY.value > 0) {
        // Swipe Down -> Smaller
        (1f - (offsetY.value / 1000f)).coerceAtLeast(0.5f)
    } else {
        1f
    }
    
    // Dimensions
    val threshold = 160f

    // Video Player
    var isMuted by remember { mutableStateOf(muteVideos) }
    var isVideoSeeking by remember { mutableStateOf(false) }
    
    val shouldPlayVideo = isTopCard && !isExpanded && !pauseMainVideos && !blockVideoDuringEntry
    val shouldRenderVideoPlayer = shouldPlayVideo
    
    // HDR Support
    val activity = LocalContext.current as? Activity
    val shouldUseHdr = isTopCard && isHdrMimeType(item.mimeType)
    DisposableEffect(shouldUseHdr) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.window?.colorMode = if (shouldUseHdr) {
                android.content.pm.ActivityInfo.COLOR_MODE_HDR
            } else {
                android.content.pm.ActivityInfo.COLOR_MODE_DEFAULT
            }
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity?.window?.colorMode = android.content.pm.ActivityInfo.COLOR_MODE_DEFAULT
            }
        }
    }

    LaunchedEffect(undoToken, isTopCard) {
        if (undoToken == null || undoDirection == null || !isTopCard) return@LaunchedEffect
        val startX = when (undoDirection) {
            SwipeDirection.Left -> -screenWidthPx * 1.2f
            SwipeDirection.Right -> screenWidthPx * 1.2f
            else -> 0f
        }
        val startY = when (undoDirection) {
            SwipeDirection.Up -> -screenHeightPx * 1.2f
            SwipeDirection.Down -> screenHeightPx * 1.2f
            else -> 0f
        }
        offsetX.snapTo(startX)
        offsetY.snapTo(startY)
        alphaAnim.snapTo(0f)
        exitScale.snapTo(0.96f)
        launch { offsetX.animateTo(0f, tween(260, easing = FastOutSlowInEasing)) }
        launch { offsetY.animateTo(0f, tween(260, easing = FastOutSlowInEasing)) }
        launch { alphaAnim.animateTo(1f, tween(180)) }
        launch { exitScale.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
    }
    
    // EXIF State
    var exifLoaded by remember { mutableStateOf(false) }
    var currentItem by remember { mutableStateOf(item) }
    var mlLabels by remember { mutableStateOf<List<String>>(emptyList()) }
    var analyzedLang by remember { mutableStateOf<String?>(null) } // Track language of current labels
    
    val context = LocalContext.current
    val currentLang = LocalAppLanguage.current.code // "en", "zh-SC", "zh-TC", etc.
    
    LaunchedEffect(shouldAnalyze, item.id, currentLang) {
        if (shouldAnalyze && item.type == "photo") {
            // Re-analyze if labels are empty OR language has changed
            if (mlLabels.isEmpty() || analyzedLang != currentLang) {
                withContext(Dispatchers.IO) {
                    val labels = ImageAnalyzer.analyze(context, item.uri, currentLang)
                    withContext(Dispatchers.Main) {
                        mlLabels = labels
                        analyzedLang = currentLang
                    }
                }
            }
        }
    }

    LaunchedEffect(item.id) {
        if (!exifLoaded && item.type == "photo") {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(item.uri)?.use { stream ->
                        val exif = ExifInterface(stream)
                        val cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE) ?: ""
                        val cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL) ?: ""
                        val iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY) ?: "--"
                        val shutter = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME) ?: "--"
                        val focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH) ?: "--"
                        val aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER) ?: "--"
                        val latLong = exif.latLong
                        var location = if (latLong != null) "${String.format("%.4f", latLong[0])}, ${String.format("%.4f", latLong[1])}" else "Unknown Location"
                        
                        // Reverse Geocoding for City Name
                        if (latLong != null) {
                            try {
                                val geocoder = Geocoder(context, Locale.getDefault())
                                // Deprecated in API 33 but still usable, or use listener. For now use sync blocking in IO thread.
                                @Suppress("DEPRECATION")
                                val addresses = geocoder.getFromLocation(latLong[0], latLong[1], 1)
                                if (!addresses.isNullOrEmpty()) {
                                    val address = addresses[0]
                                    // Try to get City/Locality, fallback to AdminArea
                                    val city = address.locality ?: address.subAdminArea ?: address.adminArea
                                    val country = address.countryName
                                    if (city != null) {
                                        location = if (country != null) "$city, $country" else city
                                    }
                                }
                            } catch (e: Exception) {
                                // Ignore geocoding errors (network, etc)
                                e.printStackTrace()
                            }
                        }

                        val camera = if (cameraMake.isNotEmpty() || cameraModel.isNotEmpty()) "$cameraMake $cameraModel".trim() else "--"
                        
                        // Format Aperture (f/1.8) and Focal Length (26mm)
                        val fNumber = if (aperture != "--") "f/$aperture" else "--"
                        val focal = if (focalLength != "--") {
                            try {
                                val parts = focalLength.split("/")
                                if (parts.size == 2) {
                                    "${(parts[0].toDouble() / parts[1].toDouble()).toInt()}mm"
                                } else {
                                    "${focalLength.toDouble().toInt()}mm"
                                }
                            } catch (e: Exception) { focalLength }
                        } else "--"

                        currentItem = item.copy(
                            camera = camera,
                            iso = iso,
                            shutter = formatShutterSpeed(shutter),
                            focalLength = focal,
                            aperture = fNumber,
                            location = location
                        )
                        exifLoaded = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Root Container for Card
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = BiasAlignment(0f, -0.12f) // Moved up slightly per request (Center is 0, Top is -1)
    ) {
        // Glow effects removed from here and moved to parent

        // --- The Card ---
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt() + yOffset.roundToPx()) }
                .scale(scale * dynamicScale * exitScale.value * pressScale) // Apply both stack scale, dynamic scale, and press scale
                .graphicsLayer { alpha = alphaAnim.value }
                //.rotate(rotation) // Rotation removed per user request
                .fillMaxWidth(0.8f) // Slightly reduced from 0.85f per request
                .fillMaxHeight(0.6f) // Reduced from 0.62f to 0.6f to ensure bottom clearance
                // Only show shadow for the top 3 visible layers (indices 0, 1, 2)
                // Cards deeper in the stack (index > 2) are hidden behind index 2 anyway, so we remove their shadow to prevent darkening
                .shadow(elevation = if (stackIndex > 2) 0.dp else 12.dp, shape = RoundedCornerShape(24.dp)) // Matched rounded-3xl
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                // --- Gesture Handling ---
                // 1. Monitor Press State for bounce animation (non-blocking)
                .pointerInput(isTopCard, isExpanded) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        if (isTopCard && !isExpanded) {
                            isPressed = true
                            waitForUpOrCancellation()
                            isPressed = false
                        }
                    }
                }
                // 2. Drag Gestures (Priority over Tap)
                .pointerInput(isTopCard, isExpanded, isVideoSeeking) {
                    if (!isTopCard || isExpanded || isVideoSeeking) return@pointerInput
                    
                    val velocityTracker = VelocityTracker()

                    detectDragGestures(
                        onDragStart = {
                             isPressed = true
                             velocityTracker.resetTracking()
                             currentOnDragStart?.invoke()
                        },
                        onDragCancel = {
                            isPressed = false
                            currentOnDragCancel?.invoke()
                            // Reset
                            scope.launch { 
                                launch { offsetX.animateTo(0f, tween(160, easing = LinearOutSlowInEasing)) }
                                launch { offsetY.animateTo(0f, tween(160, easing = LinearOutSlowInEasing)) }
                            }
                        },
                        onDragEnd = {
                            isPressed = false
                            // Calculate velocity
                            val velocity = velocityTracker.calculateVelocity()
                            val velocityX = velocity.x
                            val velocityY = velocity.y
                            
                            // Don't call onDragCancel here, it resets state too early!
                            scope.launch {
                                // Check if parent consumes the drag (e.g. drop success)
                                val consumed = currentOnDragEnd?.invoke(offsetX.value, offsetY.value) == true
                                
                                if (consumed) {
                                    // Parent consumed interaction (Drop Success).
                                    // Animate disappearance (sucked into folder)
                                    launch { exitScale.animateTo(0.1f, tween(250)) }
                                    launch { alphaAnim.animateTo(0f, tween(250)) }
                                    launch { offsetY.animateTo(offsetY.value + 200f, tween(250)) }
                                    return@launch
                                }
                                
                                val isFolderMode = offsetY.value > 420f
                                
                                // Fling Thresholds (Pixels per second)
                                val flingThreshold = 800f 
                                val verticalFlingThreshold = 800f

                                if (!isFolderMode && offsetX.value < -threshold) {
                                    // Left -> Trash (Position Only, No Velocity)
                                    if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentOnDeleteSound() // Play delete sound
                                    // Use FastOutLinearInEasing for exit animations to avoid "slowing down" at the end
                                    // Faster animation (200ms) for snappy feel
                                    offsetX.animateTo(-1500f, tween(200, easing = FastOutLinearInEasing))
                                    currentOnSwipeLeft()
                                } else if (!isFolderMode && offsetX.value > threshold) {
                                    // Right -> Like (Position Only, No Velocity)
                                    if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentOnLikeSound() // Play like sound
                                    offsetX.animateTo(1500f, tween(200, easing = FastOutLinearInEasing))
                                    currentOnSwipeRight()
                                } else if (offsetY.value < -threshold || (velocityY < -verticalFlingThreshold && offsetY.value < 0)) {
                                    // Up -> Skip (Position OR Velocity)
                                    if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentOnSwipeSound() // Play swipe sound
                                    offsetY.animateTo(-2000f, tween(200, easing = FastOutLinearInEasing))
                                    currentOnSwipeUp()
                                } else {
                                    // Reset - Use Spring for natural "snap back" physics
                                    // Adjusted for "Snappy" feel: Higher Stiffness, Less Bounciness
                                    launch { 
                                        offsetX.animateTo(
                                            0f, 
                                            spring(
                                                stiffness = 2000f, // Higher than Medium(1500)
                                                dampingRatio = 0.85f // Less bouncy than 0.75
                                            )
                                        ) 
                                    }
                                    launch { 
                                        offsetY.animateTo(
                                            0f, 
                                            spring(
                                                stiffness = 2000f,
                                                dampingRatio = 0.85f
                                            )
                                        ) 
                                    }
                                }
                            }
                        },

                        onDrag = { change: PointerInputChange, dragAmount: androidx.compose.ui.geometry.Offset ->
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                            scope.launch {
                                offsetX.snapTo(offsetX.value + dragAmount.x)
                                offsetY.snapTo(offsetY.value + dragAmount.y)
                            }
                        }
                    )
                }
                // 3. Tap Gestures (Placed AFTER drag to ensure it doesn't block events)
                .pointerInput(isTopCard) {
                    if (!isTopCard || isExpanded) return@pointerInput
                    if (item.type == "video") return@pointerInput
                    detectTapGestures(
                        onTap = { onDetail() }
                    )
                }
        ) {
            Column(
                Modifier.fillMaxSize()
            ) {
            // --- Media Content (Flex 1) ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFFF3F4F6))
            ) {
                if (item.type == "video") {
                    if (shouldRenderVideoPlayer) {
                        // Auto-play video
                        VideoPlayer(
                            item = item,
                            isMuted = isMuted,
                            shouldPlay = shouldPlayVideo,
                            onSeekStateChange = { isVideoSeeking = it }
                        )
                        
                        if (shouldPlayVideo) {
                            // Mute Toggle
                            IconButton(
                                onClick = { isMuted = !isMuted },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.2f))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                                    contentDescription = "Mute",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize().background(Color(0xFFF3F4F6))) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(item.uri)
                                    .decoderFactory(VideoFrameDecoder.Factory())
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Icon(
                                imageVector = Icons.Outlined.PlayCircleOutline,
                                contentDescription = "Video",
                                tint = Color.Black.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp).align(Alignment.Center)
                            )
                        }
                    }
                } else if (item.isLivePhoto && item.livePhotoVideoUri != null) {
                    // Live Photo
                    Box(Modifier.fillMaxSize().background(Color(0xFFF3F4F6))) {
                        // Base Photo
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(item.uri)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit, // Contain logic
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Live Video Overlay
                        if (shouldRenderVideoPlayer) {
                             VideoPlayer(
                                 item = item.copy(uri = item.livePhotoVideoUri!!, mimeType = "video/mp4"),
                                 isMuted = isMuted,
                                 shouldPlay = shouldPlayVideo,
                                 playerResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                                 onSeekStateChange = { isVideoSeeking = it }
                             )
                        }
                             
                        // Live Badge
                        Box(
                             modifier = Modifier
                                 .align(Alignment.TopStart)
                                 .padding(16.dp)
                                 .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                 .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                             Text("LIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                             
                        // Mute Toggle (Shared logic, maybe refactor later)
                        if (shouldPlayVideo) {
                             IconButton(
                                onClick = { isMuted = !isMuted },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                                    .background(Color.Black.copy(alpha = 0.2f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                                    contentDescription = "Mute",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Photo
                    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFFF3F4F6))) {
                        val containerWidth = maxWidth
                        val containerHeight = maxHeight
                        
                        // Default alignment
                        var alignment = Alignment.Center
                        var contentScale = ContentScale.Fit
                        
                        if (item.width > 0 && item.height > 0) {
                             val imageRatio = item.width.toFloat() / item.height.toFloat()
                             
                             // Check if container dimensions are finite (they should be in this layout)
                             if (containerWidth.value.isFinite() && containerHeight.value.isFinite()) {
                                 val containerRatio = containerWidth.value / containerHeight.value
                                 
                                 // If image is "wider" than container (relatively), it fits width-wise (Fit).
                                 // We check if the resulting vertical gap is too small.
                                 if (imageRatio > containerRatio) {
                                     // Fits width, gap at top/bottom
                                     // Rendered Height = ContainerWidth / ImageRatio
                                     val renderedHeight = containerWidth.value / imageRatio
                                     val topGap = (containerHeight.value - renderedHeight) / 2
                                     
                                     // Threshold for "narrow" gap: 60dp
                                     if (topGap > 0 && topGap < 60f) {
                                         // Align to top
                                         alignment = Alignment.TopCenter
                                         // If we just align top with Fit, the image will be at top, gap at bottom (2x).
                                         // This satisfies "Align to top edge start extending down".
                                     }
                                 }
                             }
                        }
                        
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(item.uri)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = contentScale,
                            alignment = alignment,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // --- Info Layer (Bottom, Fixed Height) ---
            // React: bg-white, border-t, p-5
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val date = Date(item.dateAdded * 1000)
                val format = java.text.SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
                
                Column {
                    Text(
                        text = format.format(date), 
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp, 
                        letterSpacing = 2.sp,
                        color = Color.Black.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (currentItem.location != "Unknown Location") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = "Location",
                                tint = Color.Black.copy(alpha = 0.5f),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = currentItem.location, 
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.Black.copy(alpha = 0.5f)
                            )
                        }
                    }
                    if (mlLabels.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = "AI",
                                tint = Color(0xFF8B5CF6).copy(alpha = 0.8f),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = mlLabels.joinToString(" / "), 
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF8B5CF6).copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                
                // Info Button
                IconButton(
                    onClick = { 
                        if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isExpanded = true 
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.05f))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Info",
                        tint = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // --- Action Indicators (Overlays) ---
        if (isTopCard && !isExpanded && !suppressActionIndicators) {
            val isFolderMode = offsetY.value > 150f
            Box(Modifier.fillMaxSize()) {
                if (!isFolderMode && offsetX.value > 100) {
                     // LIKE (Orange) - Right Swipe
                     Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 24.dp) // Top attached (padding 0)
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                            .border(2.dp, Color(0xFFFF9800), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)) // Orange
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Favorite, null, tint = Color(0xFFFF9800), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.likes ?: "LIKE", fontSize = 16.sp, color = Color(0xFFFF9800), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    }
                } else if (!isFolderMode && offsetX.value < -100) {
                     // DELETE (Red) - Left Swipe
                     Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 24.dp) // Top attached (padding 0)
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                            .border(2.dp, Color(0xFFEF4444), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)) // Red
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.delete, fontSize = 16.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    }
                } else if (offsetY.value < -100) {
                     // KEEP (Blue) - Up Swipe
                     Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 24.dp) // Top attached (padding 0)
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                            .border(2.dp, Color(0xFF3B82F6), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)) // Blue
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.ArrowUpward, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.skip, fontSize = 16.sp, color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    }
                } else if (isFolderMode) {
                     // MOVE (Purple) - Down Swipe
                     Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 24.dp) // Top attached (padding 0)
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                            .border(2.dp, Color(0xFF9C27B0), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)) // Purple
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.DriveFileMove, null, tint = Color(0xFF9C27B0), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.move, fontSize = 16.sp, color = Color(0xFF9C27B0), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    }
                }
            }
        }
        
        // --- Expanded EXIF Overlay ---
        AnimatedVisibility(
            visible = isExpanded,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
             Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.95f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Absorb clicks without ripple */ }
            ) {
                // Close Button (Bottom Right)
                IconButton(
                    onClick = { isExpanded = false },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 24.dp, end = 24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.05f))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = Color.Black.copy(alpha = 0.4f)
                    )
                }
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp) // Reduced horizontal padding
                        .offset(y = (-20).dp), // Adjusted offset
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center // Center vertically
                ) {
                    val date = Date(item.dateAdded * 1000)
                    val format = java.text.SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
                    
                    Text(format.format(date), fontFamily = FontFamily.Monospace, color = Color.Black.copy(alpha = 0.4f), letterSpacing = 2.sp)
                    Spacer(modifier = Modifier.height(20.dp)) // Reduced 32 -> 20
                    
                    // Filename
                    Text(
                        currentItem.displayName, 
                        fontFamily = FontFamily.Serif, 
                        fontSize = 16.sp, 
                        textAlign = TextAlign.Center,
                        maxLines = 2, 
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 240.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp)) // Reduced 12 -> 8
                    Text(strings.filename, fontSize = 10.sp, color = Color.Black.copy(alpha = 0.4f), letterSpacing = 1.sp)
                    
                    Spacer(modifier = Modifier.height(24.dp)) // Reduced 32 -> 24
                    
                    // Grid
                    val gridSpacer = 16.dp // Standardized spacer
                    
                    Row(Modifier.fillMaxWidth()) {
                         Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                             Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Image, strings.resolution, modifier = Modifier.size(24.dp), tint = Color.Black.copy(alpha = 0.8f))
                                Spacer(Modifier.height(4.dp))
                                Text(currentItem.resolution.ifEmpty { "--" }, fontFamily = FontFamily.Serif, fontSize = 14.sp)
                                Text(strings.resolution, fontSize = 10.sp, color = Color.Black.copy(alpha = 0.4f), letterSpacing = 1.sp)
                            }
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val sizeStr = if (currentItem.size > 1024 * 1024) String.format("%.1f MB", currentItem.size / 1024.0 / 1024.0) else String.format("%.0f KB", currentItem.size / 1024.0)
                                Icon(Icons.Outlined.Save, strings.size, modifier = Modifier.size(24.dp), tint = Color.Black.copy(alpha = 0.8f))
                                Spacer(Modifier.height(4.dp))
                                Text(sizeStr, fontFamily = FontFamily.Serif, fontSize = 14.sp)
                                Text(strings.size, fontSize = 10.sp, color = Color.Black.copy(alpha = 0.4f), letterSpacing = 1.sp)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(gridSpacer))
                    
                    Row(Modifier.fillMaxWidth()) {
                         Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                             Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.PhotoCamera, strings.camera, modifier = Modifier.size(24.dp), tint = Color.Black.copy(alpha = 0.8f))
                                Spacer(Modifier.height(4.dp))
                                Text(currentItem.camera, fontFamily = FontFamily.Serif, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(strings.camera, fontSize = 10.sp, color = Color.Black.copy(alpha = 0.4f), letterSpacing = 1.sp)
                            }
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.LocationOn, strings.location, modifier = Modifier.size(24.dp), tint = Color.Black.copy(alpha = 0.8f))
                                Spacer(Modifier.height(4.dp))
                                Text(if (currentItem.location == "Unknown Location") strings.unknownLocation else currentItem.location, fontFamily = FontFamily.Serif, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(strings.location, fontSize = 10.sp, color = Color.Black.copy(alpha = 0.4f), letterSpacing = 1.sp)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(gridSpacer))
                    
                    Row(Modifier.fillMaxWidth()) {
                         Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                             Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Iso, strings.iso, modifier = Modifier.size(24.dp), tint = Color.Black.copy(alpha = 0.8f))
                                Spacer(Modifier.height(4.dp))
                                Text(currentItem.iso, fontFamily = FontFamily.Serif, fontSize = 14.sp)
                                Text(strings.iso, fontSize = 10.sp, color = Color.Black.copy(alpha = 0.4f), letterSpacing = 1.sp)
                            }
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Timer, strings.shutter, modifier = Modifier.size(24.dp), tint = Color.Black.copy(alpha = 0.8f))
                                Spacer(Modifier.height(4.dp))
                                Text(currentItem.shutter, fontFamily = FontFamily.Serif, fontSize = 14.sp)
                                Text(strings.shutter, fontSize = 10.sp, color = Color.Black.copy(alpha = 0.4f), letterSpacing = 1.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(gridSpacer))

                    Row(Modifier.fillMaxWidth()) {
                         Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                             Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Camera, "Focal Length", modifier = Modifier.size(24.dp), tint = Color.Black.copy(alpha = 0.8f))
                                Spacer(Modifier.height(4.dp))
                                Text(currentItem.focalLength, fontFamily = FontFamily.Serif, fontSize = 14.sp)
                                Text("Focal", fontSize = 10.sp, color = Color.Black.copy(alpha = 0.4f), letterSpacing = 1.sp)
                            }
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Lens, "Aperture", modifier = Modifier.size(24.dp), tint = Color.Black.copy(alpha = 0.8f))
                                Spacer(Modifier.height(4.dp))
                                Text(currentItem.aperture, fontFamily = FontFamily.Serif, fontSize = 14.sp)
                                Text("Aperture", fontSize = 10.sp, color = Color.Black.copy(alpha = 0.4f), letterSpacing = 1.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun VideoPlayer(
    item: MediaItem,
    isMuted: Boolean,
    shouldPlay: Boolean = true,
    playerResizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    onSeekStateChange: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    var isReady by remember { mutableStateOf(false) } // Track if video is ready to render
    var isPlaying by remember { mutableStateOf(false) }
    var hasFirstFrame by remember { mutableStateOf(false) }
    
    // Progress State
    var currentTime by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var preparedItemId by remember { mutableStateOf<Long?>(null) }

    val isVideo = item.type == "video"

    val exoPlayer = remember { VideoPlayerCache.get(context) }
    val textureViewState = remember { mutableStateOf<TextureView?>(null) }
    val surfaceFrameState = remember { mutableStateOf<AspectRatioFrameLayout?>(null) }
    var videoAspectRatio by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(item.id, shouldPlay) {
        if (!shouldPlay) {
            if (preparedItemId != null) {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                preparedItemId = null
                isReady = false
                hasFirstFrame = false
                duration = 0L
            }
            return@LaunchedEffect
        }
        if (preparedItemId != item.id) {
            try {
                isReady = false
                hasFirstFrame = false
                val mediaItem = ExoMediaItem.Builder()
                    .setUri(item.uri)
                    .setMimeType(item.mimeType)
                    .build()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                preparedItemId = item.id
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(shouldPlay) {
        exoPlayer.playWhenReady = shouldPlay
        if (!shouldPlay) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    // Progress Loop
    if (isVideo) {
        LaunchedEffect(Unit) {
            while (isActive) {
                try {
                    if (exoPlayer.isPlaying && !isSeeking) {
                        currentTime = exoPlayer.currentPosition
                    }
                } catch (e: Exception) {
                    // Ignore errors if player is released
                }
                kotlinx.coroutines.delay(500)
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    isReady = true
                    duration = exoPlayer.duration.coerceAtLeast(0)
                }
            }

            override fun onIsPlayingChanged(isPlayingValue: Boolean) {
                isPlaying = isPlayingValue
            }

            override fun onRenderedFirstFrame() {
                hasFirstFrame = true
            }
            
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) {
                    val ratio = (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height
                    videoAspectRatio = ratio
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            textureViewState.value?.let { exoPlayer.clearVideoTextureView(it) }
            exoPlayer.removeListener(listener)
            VideoPlayerCache.release(exoPlayer)
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (!hasFirstFrame) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.uri)
                    .decoderFactory(VideoFrameDecoder.Factory())
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        AndroidView(
            factory = {
                val frame = AspectRatioFrameLayout(context).apply {
                    layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    setResizeMode(playerResizeMode)
                    setBackgroundColor(android.graphics.Color.WHITE)
                }
                val textureView = TextureView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    isOpaque = false
                }
                frame.addView(textureView)
                exoPlayer.setVideoTextureView(textureView)
                textureViewState.value = textureView
                surfaceFrameState.value = frame
                frame
            },
            update = { frame ->
                frame.setResizeMode(playerResizeMode)
                if (videoAspectRatio > 0f) {
                    frame.setAspectRatio(videoAspectRatio)
                }
                val textureView = (frame.getChildAt(0) as? TextureView)
                if (textureView != null) {
                    if (textureViewState.value !== textureView) {
                        textureViewState.value = textureView
                    }
                    exoPlayer.setVideoTextureView(textureView)
                }
                surfaceFrameState.value = frame
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = if (hasFirstFrame) 1f else 0f)
        )

        // Controls for Video Type
        if (isVideo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 96.dp)
                    .pointerInput(isSeeking) {
                        detectTapGestures(
                            onTap = {
                                if (!isSeeking) {
                                    if (isPlaying) {
                                        exoPlayer.pause()
                                    } else {
                                        exoPlayer.play()
                                    }
                                }
                            }
                        )
                    }
            )
            val showProgress = isReady && duration > 0 && (isPlaying || isSeeking)
            if (showProgress) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 32.dp) // Increased bottom padding from 16 to 32
                ) {
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val isDragged by interactionSource.collectIsDraggedAsState()
                    
                    // LaunchedEffect(isPressed, isDragged) removed to prevent state conflicts
                    
                    Slider(
                        modifier = Modifier.height(20.dp), // Increase touch target slightly
                        value = currentTime.toFloat(),
                        onValueChange = {
                            isSeeking = true
                            val newTime = it.toLong()
                            currentTime = newTime
                            exoPlayer.seekTo(newTime) // Continuous seek
                        },
                        onValueChangeFinished = {
                            // Final seek ensures precision
                            exoPlayer.seekTo(currentTime)
                            isSeeking = false
                        },
                        valueRange = 0f..duration.toFloat(),
                        interactionSource = interactionSource,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                }
            }
            if (isReady && !isPlaying && !isSeeking) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier
                            .size(64.dp)
                            .bounceClick(onClick = {
                                if (!isSeeking) {
                                    if (isPlaying) {
                                        exoPlayer.pause()
                                    } else {
                                        exoPlayer.play()
                                    }
                                }
                            })
                    )
                }
            }
        }
    }
}

fun Modifier.shadow(
    elevation: androidx.compose.ui.unit.Dp,
    shape: androidx.compose.ui.graphics.Shape
) = this.graphicsLayer {
    this.shadowElevation = elevation.toPx()
    this.shape = shape
    this.clip = false
}

// Media Loader
fun formatShutterSpeed(shutter: String): String {
    if (shutter == "--") return "--"
    return try {
        val value = shutter.toDouble()
        if (value < 1.0 && value > 0) {
            val reciprocal = (1.0 / value).roundToInt()
            "1/${reciprocal}s"
        } else {
            // Remove trailing .0 for integers
            val text = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
            "${text}s"
        }
    } catch (e: Exception) {
        shutter
    }
}

fun loadMedia(context: Context): List<MediaItem> {
    val items = mutableListOf<MediaItem>()
    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DATE_ADDED,
        MediaStore.Files.FileColumns.MEDIA_TYPE,
        MediaStore.Files.FileColumns.SIZE,
        MediaStore.Files.FileColumns.WIDTH,
        MediaStore.Files.FileColumns.HEIGHT,
        MediaStore.Files.FileColumns.MIME_TYPE,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME
    )
    
    val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?) AND ${MediaStore.MediaColumns.IS_TRASHED}=0"
    } else {
        "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
    }
    val selectionArgs = arrayOf(
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
    )
    // Randomize order: No sort order in query, shuffle later
    val sortOrder = null

    data class RawItem(
        val id: Long, val date: Long, val type: String, val size: Long,
        val w: Int, val h: Int, val mime: String, val name: String, val uri: Uri, val bucketId: String, val duration: Long, val path: String
    )

    try {
        val durationCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.MediaColumns.DURATION
        } else {
            null
        }

        // Add duration to projection if available
        val queryProjection = if (durationCol != null) {
            projection + durationCol + MediaStore.Files.FileColumns.DATA
        } else {
            projection + MediaStore.Files.FileColumns.DATA
        }

        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            queryProjection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val wCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
            val hCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val durationIndex = if (durationCol != null) cursor.getColumnIndex(durationCol) else -1
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)

            val rawItems = mutableListOf<RawItem>()

            while (cursor.moveToNext()) {
                val bucketName = cursor.getString(bucketNameCol) ?: ""
                // Filter out 3rd party apps (WeChat, QQ, etc.)
                if (bucketName.contains("WeChat", true) || 
                    bucketName.contains("WeiXin", true) || 
                    bucketName.contains("QQ", true) ||
                    bucketName.contains("Tencent", true)) {
                    continue
                }

                val id = cursor.getLong(idCol)
                val date = cursor.getLong(dateCol)
                val typeInt = cursor.getInt(typeCol)
                val size = cursor.getLong(sizeCol)
                val w = cursor.getInt(wCol)
                val h = cursor.getInt(hCol)
                val mime = cursor.getString(mimeCol) ?: ""
                val name = cursor.getString(nameCol) ?: ""
                val bucketId = cursor.getString(bucketCol) ?: "unknown"
                val bucketNameStr = cursor.getString(bucketNameCol) ?: "Unknown" // Get bucket name
                val duration = if (durationIndex != -1) cursor.getLong(durationIndex) else 0L
                val path = cursor.getString(pathCol) ?: ""
                
                val type = if (typeInt == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) "video" else "photo"
                val contentUri = if (type == "video") {
                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                } else {
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                }
                
                rawItems.add(RawItem(id, date, type, size, w, h, mime, name, contentUri, bucketNameStr, duration, path)) // Use bucketName
            }

            // Grouping Logic for Live Photos
            // Group by Bucket ID + Filename (without extension) to avoid cross-folder merges
            val grouped = rawItems.groupBy { "${it.bucketId}_${it.name.substringBeforeLast(".")}" }
            
            grouped.forEach { (_, group) ->
                // Check if we have a pair (Image + Video)
                // Relaxed Logic: Find ANY photo and ANY video in the group to merge as Live Photo
                // This handles cases where group.size > 2 (e.g. duplicate files, extra sidecars)
                
                val photos = group.filter { it.type == "photo" }
                val videos = group.filter { it.type == "video" }
                
                if (photos.isNotEmpty() && videos.isNotEmpty()) {
                     // Merge as Live Photo
                     // Take the first available pair
                     val photo = photos.first()
                     val video = videos.first()
                     
                     items.add(MediaItem(
                         id = photo.id,
                         uri = photo.uri,
                         type = "photo", // Treat as photo main
                         dateAdded = photo.date,
                         size = photo.size + video.size,
                         width = photo.w,
                         height = photo.h,
                         mimeType = photo.mime,
                         displayName = photo.name,
                         resolution = "${photo.w}x${photo.h}",
                         isLivePhoto = true,
                         livePhotoVideoUri = video.uri,
                         livePhotoVideoId = video.id,
                         bucketName = photo.bucketId, // Use bucketId as name placeholder or need map
                         duration = video.duration,
                         path = photo.path
                     ))
                     
                     // Add remaining items (if any) as standalone
                     // Exclude the merged photo and video
                     val remainingPhotos = photos.drop(1)
                     val remainingVideos = videos.drop(1)
                     
                     (remainingPhotos + remainingVideos).forEach { item ->
                        items.add(MediaItem(
                            id = item.id,
                            uri = item.uri,
                            type = item.type,
                            dateAdded = item.date,
                            size = item.size,
                            width = item.w,
                            height = item.h,
                            mimeType = item.mime,
                            displayName = item.name,
                            resolution = "${item.w}x${item.h}",
                            bucketName = item.bucketId,
                            duration = item.duration,
                            path = item.path
                        ))
                     }
                } else {
                    // Add all items individually
                    group.forEach { item ->
                        items.add(MediaItem(
                            id = item.id,
                            uri = item.uri,
                            type = item.type,
                            dateAdded = item.date,
                            size = item.size,
                            width = item.w,
                            height = item.h,
                            mimeType = item.mime,
                            displayName = item.name,
                            resolution = "${item.w}x${item.h}",
                            bucketName = item.bucketId,
                            duration = item.duration,
                            path = item.path
                        ))
                    }
                }
            }
            
            // Shuffle the items for completely random order
            items.shuffle()
        }
    } catch (e: Exception) {
        Log.e("VoiceLike", "Error loading media", e)
    }
    return items
}

data class ConfettiParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var color: Color,
    var alpha: Float = 1f,
    var rotation: Float = 0f,
    var rotationSpeed: Float = 0f
)

@Composable
fun CelebrationConfetti(intensity: Int = 100) {
    val haptic = LocalHapticFeedback.current
    val hapticsAllowed = LocalHapticsEnabled.current
    val particles = remember(intensity) {
        val colors = listOf(
            Color(0xFFEF4444), Color(0xFF3B82F6), Color(0xFF10B981), 
            Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFFEC4899)
        )
        List(intensity) {
            val angle = Math.random() * 2 * Math.PI
            val speed = (Math.random() * 15 + 10).toFloat() * (if (intensity > 100) 1.5f else 1f)
            ConfettiParticle(
                x = 0f, 
                y = 0f,
                vx = (kotlin.math.cos(angle) * speed).toFloat(),
                vy = (kotlin.math.sin(angle) * speed - 15).toFloat(), // Higher upward boost
                color = colors.random(),
                rotation = (Math.random() * 360).toFloat(),
                rotationSpeed = (Math.random() * 10 - 5).toFloat()
            )
        }
    }
    
    val time = remember { Animatable(0f) }
    
    LaunchedEffect(intensity) {
        if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        time.snapTo(0f)
        time.animateTo(
            targetValue = if (intensity > 100) 4f else 2.5f,
            animationSpec = tween(if (intensity > 100) 4000 else 2500, easing = LinearEasing)
        )
    }
    
    Canvas(modifier = Modifier.fillMaxSize(), onDraw = {
        val cx = size.width / 2
        val cy = size.height / 2
        val t = time.value
        
        particles.forEach { p ->
            // Physics: x = x0 + vx*t
            // y = y0 + vy*t + 0.5*g*t^2
            val dx = p.vx * t * 40 
            val dy = p.vy * t * 40 + 0.5f * 60f * t * t * 40 // Gravity
            
            val x = cx + dx
            val y = cy + dy
            val rotation = p.rotation + p.rotationSpeed * t * 10
            val alpha = (1f - t / (if (intensity > 100) 4f else 2.5f)).coerceIn(0f, 1f)
            
            if (y < size.height && alpha > 0) {
                rotate(rotation, pivot = androidx.compose.ui.geometry.Offset(x, y)) {
                    drawRect(
                        color = p.color.copy(alpha = alpha),
                        topLeft = androidx.compose.ui.geometry.Offset(x - 8, y - 4),
                        size = androidx.compose.ui.geometry.Size(16f, 8f)
                    )
                }
            }
        }
    })
}
