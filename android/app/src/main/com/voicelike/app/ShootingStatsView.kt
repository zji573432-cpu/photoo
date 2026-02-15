package com.voicelike.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ShootingStatsView(
    allMedia: List<MediaItem>,
    likedIds: Set<Long>
) {
    val strings = LocalAppStrings.current
    val appLanguage = LocalAppLanguage.current
    val currentLang = LocalAppLanguage.current.code
    val haptic = LocalHapticFeedback.current
    val hapticsAllowed = LocalHapticsEnabled.current
    var selectedPeriod by remember { mutableStateOf("Year") } // "Year" or "Month"
    
    // State for navigation
    var displayedDate by remember { mutableStateOf(Calendar.getInstance()) }
    
    val targetYear = displayedDate.get(Calendar.YEAR)
    val targetMonth = displayedDate.get(Calendar.MONTH) // 0-indexed
    val now = Calendar.getInstance()
    val maxYear = now.get(Calendar.YEAR)
    val maxMonthForYear = if (targetYear >= maxYear) now.get(Calendar.MONTH) else Calendar.DECEMBER
    val canGoNext = if (selectedPeriod == "Year") {
        targetYear < maxYear
    } else {
        targetYear < maxYear || (targetYear == maxYear && targetMonth < maxMonthForYear)
    }
    val canGoPrev = true

    LaunchedEffect(selectedPeriod) {
        val updated = displayedDate.clone() as Calendar
        if (updated.get(Calendar.YEAR) > maxYear) {
            updated.set(Calendar.YEAR, maxYear)
            updated.set(Calendar.MONTH, now.get(Calendar.MONTH))
        }
        if (selectedPeriod == "Month" && updated.get(Calendar.YEAR) == maxYear && updated.get(Calendar.MONTH) > now.get(Calendar.MONTH)) {
            updated.set(Calendar.MONTH, now.get(Calendar.MONTH))
        }
        displayedDate = updated
    }

    // Reset displayed date to now when period changes (optional, but good for UX)
    // Or keep it? Let's keep it to current context if possible, but switching Year->Month should probably go to current month of that year.
    // Logic: When switching to Month, use the displayedYear + current month? Or just keep displayedDate as is.
    // Keeping displayedDate as is works fine.

    // Data Class for Stats (Moved out of Composable for clarity)
    data class ShootingStatsData(
        val photoCount: Int = 0,
        val videoCount: Int = 0,
        val totalCount: Int = 0,
        val totalDurationMins: Int = 0,
        val screenshotCount: Int = 0,
        val likedCount: Int = 0,
        val cameraPhotoCount: Int = 0,
        val mostActiveDay: Map.Entry<String, List<MediaItem>>? = null,
        val locationCount: Int = 0,
        val heatmapData: Map<String, Int> = emptyMap(),
        val movieComparison: String = ""
    )

    // Offload heavy calculations to background thread
    val statsData by produceState(
        initialValue = ShootingStatsData(),
        allMedia,
        selectedPeriod,
        targetYear,
        targetMonth,
        likedIds
    ) {
        value = withContext(Dispatchers.Default) {
            // Filter Data based on Period and Displayed Date
            val filteredMedia = if (selectedPeriod == "Year") {
                allMedia.filter { 
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = it.dateAdded * 1000L
                    cal.get(Calendar.YEAR) == targetYear
                }
            } else {
                allMedia.filter {
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = it.dateAdded * 1000L
                    cal.get(Calendar.YEAR) == targetYear && cal.get(Calendar.MONTH) == targetMonth
                }
            }

            // Compute Metrics
            val photoCount = filteredMedia.count { it.type == "photo" }
            val videoCount = filteredMedia.count { it.type == "video" }
            val totalCount = photoCount + videoCount
            
            // Duration (in minutes)
            val totalDurationMs = filteredMedia.sumOf { it.duration }
            val totalDurationMins = (totalDurationMs / 60000).toInt()
            
            // Movie Comparison Logic
            val movieComparison = run {
                val moviesEn = listOf(
                    "Titanic" to 195, 
                    "Avatar" to 162, 
                    "The Matrix" to 136, 
                    "Inception" to 148, 
                    "The Godfather" to 175,
                    "Pulp Fiction" to 154,
                    "Forrest Gump" to 142
                )
                val moviesZh = listOf(
                    "战狼2" to 123, 
                    "你好，李焕英" to 128, 
                    "满江红" to 159, 
                    "流浪地球" to 125, 
                    "哪吒之魔童降世" to 110,
                    "长津湖" to 176,
                    "热辣滚烫" to 129
                )
                
                if (totalDurationMins < 45) {
                    val ratio = String.format("%.1f", totalDurationMins.toFloat() / 3f)
                    String.format(strings.shootingStatsShortDrama, ratio)
                } else if (totalDurationMins < 120) {
                    val ratio = String.format("%.1f", totalDurationMins.toFloat() / 45f)
                    String.format(strings.shootingStatsTvDrama, ratio)
                } else {
                    val list = if (currentLang.startsWith("zh")) moviesZh else moviesEn
                    val index = (totalDurationMins % list.size)
                    val (name, duration) = list[index]
                    val ratio = String.format("%.1f", totalDurationMins.toFloat() / duration)
                    if (currentLang.startsWith("zh")) {
                        "相当于 $ratio 部《$name》"
                    } else {
                        "Equiv. to $ratio x $name"
                    }
                }
            }

            val screenshotCount = filteredMedia.count { 
                it.type == "photo" &&
                (it.bucketName.contains("Screenshot", ignoreCase = true) || 
                    it.bucketName.contains("截屏", ignoreCase = true) ||
                    it.bucketName.contains("截图", ignoreCase = true))
            }
            val likedCount = filteredMedia.count { likedIds.contains(it.id) }
            val cameraPhotoCount = (photoCount - screenshotCount).coerceAtLeast(0)
            
            // Most Active Day
            val mostActiveDay = if (filteredMedia.isEmpty()) null else {
                val grouped = filteredMedia.groupBy { 
                    val date = Date(it.dateAdded * 1000L)
                    SimpleDateFormat("MM-dd", Locale.getDefault()).format(date)
                }
                grouped.maxByOrNull { it.value.size }
            }

            // Location (Bucket Name)
            val locationCount = filteredMedia.map { it.bucketName }.distinct().size

            // Heatmap Data
            val heatmapData = mutableMapOf<String, Int>()
            filteredMedia.forEach {
                val date = Date(it.dateAdded * 1000L)
                val key = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
                heatmapData[key] = (heatmapData[key] ?: 0) + 1
            }

            ShootingStatsData(
                photoCount, videoCount, totalCount, totalDurationMins, 
                screenshotCount, likedCount, cameraPhotoCount, 
                mostActiveDay, locationCount, heatmapData, movieComparison
            )
        }
    }

    // Unpack stats for UI usage
    val photoCount = statsData.photoCount
    val videoCount = statsData.videoCount
    val totalCount = statsData.totalCount
    val totalDurationMins = statsData.totalDurationMins
    val movieComparison = statsData.movieComparison
    val screenshotCount = statsData.screenshotCount
    val likedCount = statsData.likedCount
    val cameraPhotoCount = statsData.cameraPhotoCount
    val mostActiveDay = statsData.mostActiveDay
    val locationCount = statsData.locationCount
    val heatmapData = statsData.heatmapData

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 0.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // --- Header & Segmented Control ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .then(staggeredRevealModifier(1)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Row(
                modifier = Modifier
                    .background(Color(0xFFF3F4F6), RoundedCornerShape(50))
                    .padding(4.dp)
            ) {
                listOf("Year" to strings.shootingStatsYear, "Month" to strings.shootingStatsMonth).forEach { (key, label) ->
                    val isSelected = selectedPeriod == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) Color.White else Color.Transparent)
                            .clickable { 
                                if (!isSelected) {
                                    if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedPeriod = key 
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.Black else Color.Gray
                        )
                    }
                }
            }
        }

        // --- Summary Card with Navigation ---
        val summaryLocale = remember(appLanguage) {
            when (appLanguage) {
                AppLanguage.English -> Locale.ENGLISH
                AppLanguage.SimplifiedChinese -> Locale.SIMPLIFIED_CHINESE
                AppLanguage.TraditionalChinese -> Locale.TRADITIONAL_CHINESE
                AppLanguage.Korean -> Locale.KOREAN
                AppLanguage.Japanese -> Locale.JAPANESE
                AppLanguage.French -> Locale.FRENCH
                AppLanguage.German -> Locale.GERMAN
                AppLanguage.Italian -> Locale.ITALIAN
                AppLanguage.Portuguese -> Locale("pt")
                AppLanguage.Spanish -> Locale("es")
                AppLanguage.Russian -> Locale("ru")
                AppLanguage.Thai -> Locale("th")
                AppLanguage.Vietnamese -> Locale("vi")
                AppLanguage.Indonesian -> Locale("id")
                AppLanguage.Arabic -> Locale("ar")
            }
        }
        val summaryTitle = if (selectedPeriod == "Year") {
            targetYear.toString()
        } else {
            if (appLanguage == AppLanguage.SimplifiedChinese || appLanguage == AppLanguage.TraditionalChinese || appLanguage == AppLanguage.Japanese || appLanguage == AppLanguage.Korean) {
                 SimpleDateFormat("yyyy年 MMMM", summaryLocale).format(displayedDate.time)
            } else {
                 SimpleDateFormat("MMMM yyyy", summaryLocale).format(displayedDate.time)
            }
        }
        
        // Use correct format string based on period
        val summaryFormatStr = if (selectedPeriod == "Year") strings.shootingStatsSummary else strings.shootingStatsSummaryMonth
        
        val summaryText = remember(summaryFormatStr, summaryTitle, totalCount) {
            try {
                String.format(summaryFormatStr, summaryTitle, totalCount)
            } catch (e: Exception) {
                "$summaryTitle: $totalCount items"
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(staggeredRevealModifier(2))
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFFFF7ED)) // Light Orange/Cream
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = summaryText,
                    fontSize = 32.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF9A3412),
                    lineHeight = 40.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
        val itemsText = remember(strings.shootingStatsItems, cameraPhotoCount, videoCount, screenshotCount, strings.shootingStatsScreenshots) {
            try {
                String.format(strings.shootingStatsItems, cameraPhotoCount, videoCount) + " | ${screenshotCount} ${strings.shootingStatsScreenshots}"
            } catch (e: Exception) {
                "$cameraPhotoCount Photos | $videoCount Videos | $screenshotCount ${strings.shootingStatsScreenshots}"
            }
        }

                Text(
                    text = itemsText,
                    fontSize = 14.sp,
                    color = Color(0xFFC2410C).copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 0.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (canGoPrev) {
                                if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val cal = displayedDate.clone() as Calendar
                                if (selectedPeriod == "Year") cal.add(Calendar.YEAR, -1) else cal.add(Calendar.MONTH, -1)
                                displayedDate = cal
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Prev", tint = Color(0xFF9A3412))
                    }

                    IconButton(
                        onClick = {
                            if (canGoNext) {
                                if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val cal = displayedDate.clone() as Calendar
                                if (selectedPeriod == "Year") cal.add(Calendar.YEAR, 1) else cal.add(Calendar.MONTH, 1)
                                displayedDate = cal
                            }
                        },
                        enabled = canGoNext,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next",
                            tint = if (canGoNext) Color(0xFF9A3412) else Color(0xFF9A3412).copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // --- 2x2 Grid Layout for Stats ---
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Row 1: Video & Location
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(staggeredRevealModifier(3))
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Video Stats
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFEFF6FF)) // Light Blue
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Videocam,
                            contentDescription = strings.shootingStatsVideoTitle,
                            tint = Color(0xFF1E40AF),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.shootingStatsVideoTitle,
                            fontSize = 12.sp,
                            color = Color(0xFF1E40AF),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val durationText: String = remember(strings.shootingStatsVideoDuration, totalDurationMins, videoCount) {
                        try {
                            if (totalDurationMins == 0 && videoCount > 0) {
                                // Show "< 1" or handle gracefully
                                String.format(strings.shootingStatsVideoDuration, "< 1")
                            } else {
                                String.format(strings.shootingStatsVideoDuration, totalDurationMins)
                            }
                        } catch (e: Exception) {
                            "$totalDurationMins mins"
                        }
                    }
                    Text(
                        text = durationText,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E3A8A)
                    )
                    Text(
                        text = movieComparison,
                        fontSize = 11.sp,
                        color = Color(0xFF60A5FA),
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Location Stats
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFFEF3C7)) // Light Amber
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Place,
                            contentDescription = strings.shootingStatsLocationTitle,
                            tint = Color(0xFF92400E),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.shootingStatsLocationTitle,
                            fontSize = 12.sp,
                            color = Color(0xFF92400E),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val locationText = remember(strings.shootingStatsLocationPlaces, locationCount) {
                        try {
                            String.format(strings.shootingStatsLocationPlaces, locationCount)
                        } catch (e: Exception) {
                            "$locationCount Places"
                        }
                    }
                    Text(
                        text = locationText,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF78350F)
                    )
                }
            }

            // Row 2: Screenshots & Active Day
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(staggeredRevealModifier(4))
                    .height(IntrinsicSize.Min)
                    .heightIn(min = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Screenshots
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFF0FDF4)) // Light Green
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.FavoriteBorder,
                                contentDescription = strings.likes,
                                tint = Color(0xFF166534),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = strings.likes,
                                fontSize = 12.sp,
                                color = Color(0xFF166534),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = likedCount.toString(),
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF14532D)
                        )
                    }
                }
                
                // Most Active Day
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFFAF5FF)) // Light Purple
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Whatshot,
                                contentDescription = strings.shootingStatsActiveDayTitle,
                                tint = Color(0xFF6B21A8),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = strings.shootingStatsActiveDayTitle,
                                fontSize = 12.sp,
                                color = Color(0xFF6B21A8),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (mostActiveDay != null) {
                            Text(
                                text = String.format(strings.shootingStatsMostActiveDay, mostActiveDay.key, mostActiveDay.value.size),
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF581C87)
                            )
                        } else {
                            Text(
                                text = "-",
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF581C87)
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // --- Heatmap (Contribution Graph) ---
        Text(
            text = strings.shootingStatsHeatmapTitle,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier
                .padding(bottom = 12.dp)
                .then(staggeredRevealModifier(5))
        )
        
        // Heatmap Data (Already calculated in statsData)

    val heatmapColors = listOf(
        Color(0xFFF3F4F6),
        Color(0xFFD1FAE5),
        Color(0xFF6EE7B7),
        Color(0xFF34D399),
        Color(0xFF059669),
        Color(0xFF047857)
    )
        
        if (selectedPeriod == "Month") {
            // Transposed Grid (Calendar Style): Column of Rows
            // 6 Weeks x 7 Days
            val calendarGrid = remember(targetYear, targetMonth) {
                 val grid = mutableListOf<Date>()
                 val cal = Calendar.getInstance()
                 cal.set(Calendar.YEAR, targetYear)
                 cal.set(Calendar.MONTH, targetMonth)
                 cal.set(Calendar.DAY_OF_MONTH, 1)
                 
                 // Find start of week (Sunday)
                 val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon...
                 cal.add(Calendar.DATE, -(dayOfWeek - 1))
                 
                 // Add 42 days (6 weeks)
                 for (i in 0 until 42) {
                     grid.add(cal.time)
                     cal.add(Calendar.DATE, 1)
                 }
                 grid
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(staggeredRevealModifier(6)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.wrapContentWidth()
                ) {
                    for (w in 0 until 6) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            for (d in 0 until 7) {
                                val index = w * 7 + d
                                if (index < calendarGrid.size) {
                                    val cellDate = calendarGrid[index]
                                    val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cellDate)
                                    val count = heatmapData[dateKey] ?: 0
                                    
                                    // Color Logic
                                    val color = when {
                                        count == 0 -> heatmapColors[0]
                                        count <= 2 -> heatmapColors[1]
                                        count <= 5 -> heatmapColors[2]
                                        count <= 10 -> heatmapColors[3]
                                        count <= 20 -> heatmapColors[4]
                                        else -> heatmapColors[5]
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(color)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
        } else {
            // Year View: Keep Horizontal Scroll but maybe optimized?
            // Use existing logic for Year (Horizontal)
            val weeks = 53
            val scrollState = rememberScrollState()
            LaunchedEffect(selectedPeriod, targetYear) {
                 scrollState.scrollTo(0)
            }

            val weekCellSize = 10.dp
            val weekColumnWidth = 16.dp
            val weekRowSpacing = 3.dp

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .then(staggeredRevealModifier(6))
                    .heightIn(min = 90.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                for (w in 0 until weeks) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(weekRowSpacing),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(weekColumnWidth)
                    ) {
                        for (dayOfWeek in 0 until 7) {
                            val cellDate = getDateForGrid(targetYear, targetMonth, selectedPeriod, w, dayOfWeek, weeks)
                            val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cellDate)
                            val count = heatmapData[dateKey] ?: 0
                            
                            val color = when {
                                count == 0 -> heatmapColors[0]
                                count <= 2 -> heatmapColors[1]
                                count <= 5 -> heatmapColors[2]
                                count <= 10 -> heatmapColors[3]
                                count <= 20 -> heatmapColors[4]
                                else -> heatmapColors[5]
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(weekCellSize)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(color)
                            )
                        }
                    }
                }
            }

            val startCal = remember(targetYear, selectedPeriod) {
                Calendar.getInstance().apply {
                    set(Calendar.YEAR, targetYear)
                    set(Calendar.MONTH, Calendar.JANUARY)
                    set(Calendar.DAY_OF_MONTH, 1)
                    val dayOfWeek = get(Calendar.DAY_OF_WEEK)
                    add(Calendar.DATE, -(dayOfWeek - 1))
                }
            }
            val monthLabelIndices = remember(targetYear, selectedPeriod) {
                listOf(2, 5, 8, 11).map { monthIndex ->
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.YEAR, targetYear)
                    cal.set(Calendar.MONTH, monthIndex)
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    val diffDays = ((cal.timeInMillis - startCal.timeInMillis) / (1000L * 60 * 60 * 24)).toInt()
                    val weekIndex = (diffDays / 7).coerceIn(0, weeks - 1)
                    weekIndex to (monthIndex + 1).toString()
                }.toMap()
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                for (w in 0 until weeks) {
                    Box(
                        modifier = Modifier
                            .width(weekColumnWidth),
                        contentAlignment = Alignment.Center
                    ) {
                        monthLabelIndices[w]?.let { label ->
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip
                            )
                        }
                    }
                }
            }
        }
        
        // Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .then(staggeredRevealModifier(7)),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.shootingStatsHeatmapLess,
                fontSize = 10.sp, 
                color = Color.Gray
            )
            Spacer(modifier = Modifier.width(4.dp))
            heatmapColors.forEach {
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(it))
                Spacer(modifier = Modifier.width(2.dp))
            }
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = strings.shootingStatsHeatmapMore,
                fontSize = 10.sp, 
                color = Color.Gray
            )
        }
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun ShootingStatsPage(
    allMedia: List<MediaItem>,
    likedIds: Set<Long>,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val layoutDirection = LocalLayoutDirection.current
    val appLanguage = LocalAppLanguage.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, start = 16.dp, bottom = 16.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.05f))
            ) {
                Icon(
                    Icons.Outlined.ArrowBack,
                    "Back",
                    tint = Color.Black,
                    modifier = if (layoutDirection == LayoutDirection.Rtl) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier
                )
            }
            Text(
                text = strings.shootingStatsTitle,
                fontSize = if (appLanguage == AppLanguage.Russian) 16.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
                textAlign = TextAlign.Center,
                lineHeight = if (appLanguage == AppLanguage.Russian) 18.sp else TextUnit.Unspecified
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        ShootingStatsView(allMedia = allMedia, likedIds = likedIds)
    }
}

// Helper to calculate date for grid cell (Year view only now)
fun getDateForGrid(year: Int, month: Int, period: String, weekIndex: Int, dayIndex: Int, totalWeeks: Int): Date {
    val cal = Calendar.getInstance()
    cal.set(Calendar.YEAR, year)
    cal.set(Calendar.MONTH, Calendar.JANUARY)
    cal.set(Calendar.DAY_OF_MONTH, 1)
    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    cal.add(Calendar.DATE, -(dayOfWeek - 1))
    cal.add(Calendar.DATE, weekIndex * 7 + dayIndex)
    return cal.time
}
