package com.voicelike.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withContext // Added import
import androidx.compose.material.icons.outlined.Check // Added import for checkmark

import kotlin.math.ceil
import kotlin.math.max

@Composable
fun DeepOrganizeView(
    allMedia: List<MediaItem>, // Added parameter
    onBack: () -> Unit,
    onSimilarClick: () -> Unit,
    onLowQualityClick: () -> Unit,
    onVideoCompressClick: () -> Unit,
    onImageCompressClick: () -> Unit,
    totalMediaCount: Int = 0
) {
    val isScanning by MediaAnalysisManager.isScanning.collectAsState()
    val progress by MediaAnalysisManager.progress.collectAsState()
    val scanRatePerMinute by MediaAnalysisManager.scanRatePerMinute.collectAsState()
    val strings = LocalAppStrings.current
    
    // --- Counts State ---
    var similarCount by remember { mutableStateOf(0) }
    var lowQualityCount by remember { mutableStateOf(0) }
    var videoCount by remember { mutableStateOf(0) }
    var imageCompressCount by remember { mutableStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    
    // Watch Similar
    val similarGroups by SimilarPhotoManager.foundGroups.collectAsState()
    LaunchedEffect(similarGroups) {
        similarCount = similarGroups.sumOf { it.size }
    }
    
    // Watch Low Quality
    LaunchedEffect(isScanning, progress) {
        lowQualityCount = MediaAnalysisManager.getLowQualityCount()
    }
    
    LaunchedEffect(allMedia) {
        videoCount = allMedia.count { it.type == "video" }
    }

    LaunchedEffect(allMedia) {
        val thresholdMb = prefs.imageCompressThresholdMb.coerceIn(3, 10)
        val minBytes = thresholdMb * 1024L * 1024L
        imageCompressCount = allMedia.count { it.type == "photo" && it.size >= minBytes }
    }

    // Calculate percentage text
    val percent = (progress * 100).toInt()
    val totalPhotos = allMedia.count { it.type == "photo" }
    val remainingPhotos = max(0, totalPhotos - (progress * totalPhotos).toInt())
    val estimatedFromRate = if (scanRatePerMinute > 0f) {
        max(1, ceil(remainingPhotos / scanRatePerMinute).toInt())
    } else {
        max(1, ceil(remainingPhotos / 2000.0).toInt())
    }
    
    // Dynamic Description
    val rawDesc = strings.scanningDescriptionDetailed
        ?: "This may take about %d minutes depending on your gallery size. You can exit and let it run in the background."
    val desc = when {
        rawDesc.contains("%d") -> String.format(rawDesc, estimatedFromRate)
        rawDesc.contains("1-3") -> rawDesc.replace("1-3", "$estimatedFromRate")
        rawDesc.contains("1~3") -> rawDesc.replace("1~3", "$estimatedFromRate")
        rawDesc.contains("1〜3") -> rawDesc.replace("1〜3", "$estimatedFromRate")
        rawDesc.contains("1～3") -> rawDesc.replace("1～3", "$estimatedFromRate")
        else -> rawDesc
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6)) // Light gray background
    ) {
        // --- Header ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, bottom = 16.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.Black)
            }
            
            Text(
                text = strings.deepOrganizeTitle ?: "Deep Organize",
                fontSize = if (LocalAppLanguage.current == AppLanguage.Russian) 16.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = if (LocalAppLanguage.current == AppLanguage.Russian) 18.sp else androidx.compose.ui.unit.TextUnit.Unspecified
            )
        }

        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // --- Progress Block ---
            // Using a Gradient Background to match the style of feature cards
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent), // Transparent to show gradient
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .then(staggeredRevealModifier(0)) // Clip for background
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1F2937)) // Cool Dark Gray (Slate 800 style) - Professional & Modern
                        .padding(24.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isScanning) strings.deepOrganizeScanning ?: "Scanning Gallery..." else strings.deepOrganizeComplete ?: "Scan Complete",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White // White text on gradient
                            )
                            
                            Text(
                                text = "$percent%",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f),
                        )
                        
                        if (isScanning) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = desc,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
            
            // --- Feature Grid ---
            // Removed "Tools" title per user request
            
            // Similar Photos
            FeatureCard(
                title = strings.similar ?: "Similar Photos",
                subtitle = strings.similarPhotosSubtitle ?: "Find and clean duplicate shots",
                backgroundColor = Color(0xFF45B7D1), // Blue
                titleColor = Color.White,
                count = similarCount,
                onClick = onSimilarClick,
                modifier = staggeredRevealModifier(1)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Low Quality
            FeatureCard(
                title = strings.lowQuality ?: "Low Quality",
                subtitle = strings.lowQualitySubtitle ?: "Blurry, dark, and bad photos",
                backgroundColor = Color(0xFFFF6B6B), // Red
                titleColor = Color.White,
                count = lowQualityCount,
                onClick = onLowQualityClick,
                modifier = staggeredRevealModifier(2)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            FeatureCard(
                title = strings.videoCompressTitle ?: "Video Compression",
                subtitle = strings.videoCompressSubtitle ?: "Reduce size with minimal quality loss",
                backgroundColor = Color(0xFF8B5CF6),
                titleColor = Color.White,
                count = videoCount,
                onClick = onVideoCompressClick,
                modifier = staggeredRevealModifier(3)
            )

            Spacer(modifier = Modifier.height(16.dp))

            FeatureCard(
                title = strings.imageCompressTitle,
                subtitle = strings.imageCompressSubtitle,
                backgroundColor = Color(0xFF10B981),
                titleColor = Color.White,
                count = imageCompressCount,
                onClick = onImageCompressClick,
                modifier = staggeredRevealModifier(4)
            )
        }
    }
}

@Composable
fun FeatureCard(
    title: String,
    subtitle: String,
    backgroundColor: Color,
    titleColor: Color,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = title,
                    fontSize = 20.sp, // Larger title
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = titleColor.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }
            
            // Count or Checkmark
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(titleColor.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (count == 0) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Done",
                        tint = titleColor,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = if (count > 99) "99+" else count.toString(),
                        color = titleColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
