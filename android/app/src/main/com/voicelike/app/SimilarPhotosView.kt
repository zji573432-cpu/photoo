package com.voicelike.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SimilarPhotosView(
    similarGroups: List<List<MediaItem>>,
    isScanning: Boolean = false,
    progress: Float = 0f,
    onClose: () -> Unit,
    onFinishProcessing: (Set<Long>, Set<Long>) -> Unit, // Legacy support, can be ignored if using onDecision
    onDecision: (Set<Long>, Set<Long>) -> Unit = { _, _ -> } // Immediate callback: (deleted, processed)
) {
    // We no longer maintain currentGroupIndex. 
    // We always show the FIRST group in the list.
    // When a decision is made, the list is updated externally (by removing processed items),
    // causing the next group to naturally slide into the first position.
    
    // Ensure we only process groups that are valid pairs (or at least > 1)
    val validGroups = remember(similarGroups) {
        similarGroups.filter { it.size >= 2 }
    }

    // Stabilize the current group: Try to keep showing the same group even if list updates (incremental scan)
    var displayedGroupId by remember { mutableStateOf<Long?>(null) }

    val currentGroup = remember(validGroups, displayedGroupId) {
        if (displayedGroupId != null) {
            val found = validGroups.find { it.firstOrNull()?.id == displayedGroupId }
            if (found != null) return@remember found
        }
        validGroups.firstOrNull()
    }

    LaunchedEffect(currentGroup) {
        displayedGroupId = currentGroup?.firstOrNull()?.id
    }
    
    // IDs marked for deletion and processing (kept for legacy onFinishProcessing if needed)
    var idsToDelete by remember { mutableStateOf(setOf<Long>()) }
    var idsProcessed by remember { mutableStateOf(setOf<Long>()) }
    
    val strings = LocalAppStrings.current

    // Item Details Cache (for Location/Date)
    val itemDetails = remember { mutableStateMapOf<Long, MediaItem>() }

    // Done Check (Empty State)
    if (currentGroup == null && !isScanning) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF3F4F6))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { /* Intercept clicks */ },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Done",
                        tint = Color(0xFF22C55E), // Green
                        modifier = Modifier.size(40.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = strings.noSimilarPhotosFound ?: "No similar photos found",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = strings.similarPhotosDone ?: "You're all caught up!",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = {
                        onFinishProcessing(idsToDelete, idsProcessed)
                        onClose()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .bounceClick(onClick = {
                            onFinishProcessing(idsToDelete, idsProcessed)
                            onClose()
                        })
                ) {
                    Text(strings.done ?: "Done")
                }
            }
        }
        return
    }

    // Scanning State
    if (currentGroup == null && isScanning) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF3F4F6))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { /* Intercept clicks */ },
            contentAlignment = Alignment.Center
        ) {
             Column(horizontalAlignment = Alignment.CenterHorizontally) {
                 CircularProgressIndicator(color = Color.Black)
                 Spacer(modifier = Modifier.height(16.dp))
                 Text(
                     text = strings.scanningWait ?: "Please wait...", 
                     color = Color.Black,
                     fontSize = 18.sp,
                     fontWeight = FontWeight.Bold
                 )
                 Spacer(modifier = Modifier.height(8.dp))
                 Text(
                     text = strings.scanningDescriptionDetailed ?: "This may take 1-3 minutes. You can exit and let it run in the background.", 
                     color = Color.Gray,
                     fontSize = 14.sp,
                     textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                     modifier = Modifier.padding(horizontal = 32.dp)
                 )
                 
                 Button(onClick = onClose, modifier = Modifier.padding(top = 32.dp)) {
                     Text(strings.exit ?: "Exit") 
                 }
             }
        }
        return
    }

    // Selection State for Current Group
    // Reset selection when the group changes (based on ID of first item)
    val currentGroupId = currentGroup?.firstOrNull()?.id
    var keptId by remember(currentGroupId) { mutableStateOf<Long?>(null) }
    
    // Expanded Detail State
    var expandedId by remember { mutableStateOf<Long?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6)) // Gray-100 background
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { /* Intercept clicks */ }
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(top = 48.dp), // Add padding to internal content instead of root to allow full screen bg
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isScanning) strings.scanning else "${strings.similar} (${validGroups.size})",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    
                    IconButton(onClick = {
                        onFinishProcessing(idsToDelete, idsProcessed)
                        onClose()
                    }) {
                        Icon(Icons.Outlined.Close, "Close", tint = Color.Black)
                    }
                }
                
                if (isScanning) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = Color(0xFF3B82F6),
                        trackColor = Color(0xFFE5E7EB),
                    )
                }
            }

            Text(
                text = strings.selectBest,
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Comparison Area (Centered Vertically)
            val pair = currentGroup!!.take(2)
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { item ->
                        val isSelected = keptId == item.id
                        val details = itemDetails[item.id] ?: item
                        
                        // Card Container with Surface for correct Shadow
                        // Note: Surface doesn't support custom bounceClick easily without losing elevation shadow logic
                        // So we use bounceClick on modifier but Surface onClick must be null to not conflict?
                        // Or we wrap Surface in Box with bounceClick. Let's wrap Surface content.
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(9f / 16f)
                                .bounceClick(onClick = { 
                                    if (keptId == item.id) keptId = null else keptId = item.id
                                })
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(24.dp),
                                shadowElevation = 12.dp,
                                color = Color.White,
                                border = if (isSelected) BorderStroke(3.dp, Color(0xFF3B82F6)) else null
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Photo Area
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                    ) {
                                        // PhotoCard with NO bottom clip (to merge with white footer visually if needed, 
                                        // but here we clip top only to match card shape)
                                        PhotoCard(
                                            item = item,
                                            modifier = Modifier.fillMaxSize(),
                                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
                                            isTopCard = true,
                                            showActions = false, // Disable details button
                                            isExpanded = false,
                                            onToggleExpand = {}, // Disable expand
                                            onDataLoaded = { updated -> itemDetails[updated.id] = updated }
                                        )
                                        
                                        // Selection Checkmark Overlay
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(12.dp)
                                                    .background(Color(0xFF3B82F6), CircleShape)
                                                    .size(24.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                    
                                    // Footer Info Area (White Space)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White)
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // Date
                                        val date = Date(details.dateAdded * 1000)
                                        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                        Text(
                                            text = format.format(date),
                                            fontSize = 10.sp,
                                            color = Color.Gray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        
                                        // Location
                                        if (details.location != "Unknown Location") {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = details.location,
                                                fontSize = 10.sp,
                                                color = Color.Gray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // "Keep Selected" Button (Primary)
                Button(
                    onClick = {
                        if (keptId != null) {
                            // Mark unselected as delete
                            val unkept = pair.filter { it.id != keptId }.map { it.id }.toSet()
                            val allInPair = pair.map { it.id }.toSet()
                            
                            // Update local state (for legacy)
                            idsToDelete = idsToDelete + unkept
                            idsProcessed = idsProcessed + allInPair
                            
                            // Immediate update
                            onDecision(unkept, allInPair)
                            
                            // Next group (handled by state update)
                            // currentGroupIndex++
                        }
                    },
                    enabled = keptId != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text(strings.keepSelected, fontSize = 16.sp)
                }

                // Secondary Actions Row: Keep All | Delete All
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Keep All
                    Button(
                        onClick = {
                            val allInPair = pair.map { it.id }.toSet()
                            
                            // Update local state
                            idsProcessed = idsProcessed + allInPair
                            
                            // Immediate update (no deletes, but mark as processed)
                            onDecision(emptySet(), allInPair)
                            
                            // currentGroupIndex++
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF3F4F6),
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        Text(strings.keepAll, fontSize = 14.sp)
                    }

                    // Delete All (New)
                    Button(
                        onClick = {
                            val allInPair = pair.map { it.id }.toSet()
                            
                            // Update local state
                            idsToDelete = idsToDelete + allInPair
                            idsProcessed = idsProcessed + allInPair
                            
                            // Immediate update
                            onDecision(allInPair, allInPair)
                            
                            // currentGroupIndex++
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFEE2E2), // Red-100
                            contentColor = Color(0xFFDC2626) // Red-600
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.deleteAll, fontSize = 14.sp)
                    }
                }
            }
        }
        
        // Full Screen Expanded Overlay
        if (expandedId != null) {
            val expandedItem = currentGroup?.find { it.id == expandedId }
            if (expandedItem != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.9f))
                        .clickable { expandedId = null }
                        .padding(top = 48.dp, bottom = 24.dp, start = 16.dp, end = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PhotoCard(
                        item = expandedItem,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(9f/16f)
                            .clip(RoundedCornerShape(16.dp)),
                        isTopCard = true,
                        showActions = true,
                        isExpanded = true, 
                        onToggleExpand = { expandedId = null }
                    )
                }
            }
        }
    }
}
