package com.voicelike.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.text.style.TextOverflow
import coil.decode.VideoFrameDecoder
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.itemsIndexed
import java.util.UUID

data class FilterFolderData(
    val name: String,
    val count: Int,
    val previews: List<MediaItem>
)

@Composable
fun FilterView(
    currentFolder: String,
    currentType: String,
    currentMonth: String = "All",
    folderList: List<FilterFolderData>,
    onApply: (String, String, String) -> Unit, // folder, type, month
    onBack: () -> Unit,
    onSelectMonth: () -> Unit, // Open MonthlyListView
    onClearMonth: () -> Unit = {} // Added callback to clear month
) {
    val strings = LocalAppStrings.current
    var selectedFolder by remember { mutableStateOf(currentFolder) }
    var selectedType by remember { mutableStateOf(currentType) }
    var selectedMonth by remember { mutableStateOf(currentMonth) }

    LaunchedEffect(currentMonth) {
        selectedMonth = currentMonth
    }
    
    // Page ID for animation reset
    val pageId = remember { UUID.randomUUID().toString() }

    val haptic = LocalHapticFeedback.current
    val hapticsAllowed = LocalHapticsEnabled.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .background(Color.Black.copy(alpha = 0.05f), CircleShape)
            ) {
                Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.Black)
            }
            
            Text(
                text = strings.filterTitle,
                fontSize = if (LocalAppLanguage.current == AppLanguage.Russian) 16.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = if (LocalAppLanguage.current == AppLanguage.Russian) 18.sp else androidx.compose.ui.unit.TextUnit.Unspecified
            )
            
            // Apply Button
            TextButton(
                onClick = { 
                    if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onApply(selectedFolder, selectedType, selectedMonth) 
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Text(strings.apply, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            // Month Section
            item {
                Text(
                    text = strings.filterMonth ?: "Month",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .then(staggeredRevealModifier(0, key = pageId + "month_title"))
                )

                // Month Selector Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(staggeredRevealModifier(1, key = pageId + "month_card"))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF3F4F6))
                        .bounceClick(onClick = { 
                            if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelectMonth() 
                        })
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (selectedMonth == "All") strings.filterAll else selectedMonth,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.Black
                            )
                            if (selectedMonth == "All") {
                                Text(
                                    text = strings.selectMonth ?: "Select a specific month",
                                    fontSize = 12.sp,
                                    color = Color.Black.copy(alpha = 0.5f)
                                )
                            }
                        }
                        if (selectedMonth != "All") {
                            IconButton(
                                onClick = { 
                                    if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedMonth = "All"
                                    onClearMonth()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Clear Month",
                                    tint = Color.Black.copy(alpha = 0.6f)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = "Select Month",
                                tint = Color.Black.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Media Type Section
            item {
                Text(
                    text = strings.filterType,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .then(staggeredRevealModifier(2, key = pageId + "type_title"))
                )
                
                val types = listOf(
                    "All" to strings.filterAll,
                    "Image" to strings.filterImage,
                    "Video" to strings.filterVideo,
                    "Live" to strings.filterLive
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(staggeredRevealModifier(3, key = pageId + "type_row"))
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    types.forEach { (key, label) ->
                        val isSelected = selectedType == key
                        FilterChip(
                            selected = isSelected,
                            onClick = { 
                                if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedType = key 
                            },
                            label = { Text(label, maxLines = 1, softWrap = false) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color.Black,
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFFF3F4F6),
                                labelColor = Color.Black
                            ),
                            border = null,
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Folder Section
            item {
                Text(
                    text = strings.filterFolder,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .then(staggeredRevealModifier(4, key = pageId + "folder_title"))
                )
            }
            
            itemsIndexed(folderList) { index, folder ->
                // Optimize animation: Only stagger first few items, then show quickly
                val animIndex = if (index < 6) 5 + index else 1
                
                FolderItem(
                    data = folder,
                    isSelected = selectedFolder == folder.name,
                    onClick = { 
                        if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        // Update Folder
                        onApply(folder.name, selectedType, selectedMonth)
                    },
                    modifier = staggeredRevealModifier(
                        index = animIndex,
                        key = pageId + "folder_${folder.name}"
                    )
                )
            }
        }
    }
}

@Composable
fun FolderItem(
    data: FilterFolderData,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color.Black.copy(alpha = 0.15f) else Color.Transparent)
            .bounceClick(onClick = onClick)
            .padding(12.dp)
    ) {
        // Title Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "${data.name} (${data.count})",
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = Color.Black,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Selected",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Preview Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val totalSlots = 7
            val previewsToShow = minOf(data.previews.size, 6)
            val hasOverflow = data.count > 6
            
            for (i in 0 until previewsToShow) {
                 Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(4.dp))) {
                     AsyncImage(
                         model = ImageRequest.Builder(LocalContext.current)
                             .data(data.previews[i].uri)
                             .decoderFactory(VideoFrameDecoder.Factory())
                             .crossfade(true)
                             .size(200) // Thumbnail size
                             .build(),
                         contentDescription = null,
                         contentScale = ContentScale.Crop,
                         modifier = Modifier.fillMaxSize()
                     )
                 }
            }
            
            if (hasOverflow) {
                 Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(4.dp))) {
                     if (data.previews.size > 6) {
                         AsyncImage(
                             model = ImageRequest.Builder(LocalContext.current)
                                 .data(data.previews[6].uri)
                                 .decoderFactory(VideoFrameDecoder.Factory())
                                 .crossfade(true)
                                 .size(200)
                                 .build(),
                             contentDescription = null,
                             contentScale = ContentScale.Crop,
                             modifier = Modifier.fillMaxSize()
                         )
                     }
                     // Overlay
                     Box(
                         modifier = Modifier
                             .fillMaxSize()
                             .background(Color.Black.copy(alpha = 0.6f)),
                         contentAlignment = Alignment.Center
                     ) {
                         val remaining = data.count - 6
                         val fontSize = if (remaining.toString().length > 3) 10.sp else 12.sp
                         Text(
                             text = "+$remaining",
                             color = Color.White,
                             fontWeight = FontWeight.Bold,
                             fontSize = fontSize,
                             maxLines = 1,
                             overflow = TextOverflow.Visible,
                             softWrap = false
                         )
                     }
                 }
            }
            
            val usedSlots = previewsToShow + (if (hasOverflow) 1 else 0)
            for (i in usedSlots until totalSlots) {
                 Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
            }
        }
    }
}
