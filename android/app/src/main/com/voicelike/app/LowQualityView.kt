package com.voicelike.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun LowQualityView(
    items: List<Pair<MediaItem, String>>, // Item + Reason
    isScanning: Boolean,
    progress: Float,
    pendingTrashCount: Int,
    canUndo: Boolean, // Added
    muteVideos: Boolean,
    onClose: () -> Unit,
    onSwipe: (MediaItem, String, Boolean) -> Unit, // Added reason to callback
    onUndo: () -> Unit, // Added
    onViewTrash: () -> Unit
) {
    val strings = LocalAppStrings.current
    
    // Manage local list to allow animation before removing from parent list
    // Actually, parent handles list state, we just callback.
    // But for smooth UI, we display from the list.
    
    // Scanning State
    if (items.isEmpty() && isScanning) {
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
                 Text(text = strings.scanning, color = Color.Gray)
                 
                 LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .width(200.dp)
                        .height(4.dp),
                    color = Color.Black,
                    trackColor = Color(0xFFE5E7EB),
                )

                 Button(onClick = onClose, modifier = Modifier.padding(top = 32.dp)) {
                     Text(strings.stop)
                 }
             }
        }
        return
    }

    // Done State
    if (items.isEmpty() && !isScanning) {
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
                    text = strings.noLowQualityFound,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = strings.lowQualityDone,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(strings.done)
                }
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6)) // Gray-100
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { /* Intercept clicks */ }
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 24.dp, end = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = strings.lowQuality,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = "${items.size} items",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Trash Button
                    AnimatedVisibility(visible = pendingTrashCount > 0) {
                        Button(
                            onClick = onViewTrash,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEF2F2)), // red-50
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA)), // red-200
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Clean",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(pendingTrashCount.toString(), color = Color(0xFFDC2626), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, "Close", tint = Color.Black)
                    }
                }
            }
            
            if (isScanning) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .height(4.dp),
                    color = Color.Black,
                    trackColor = Color(0xFFE5E7EB),
                )
            }
        }

        // Card Stack
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = BiasAlignment(0f, -0.12f) // Match main page alignment
        ) {
            // Show top 3
            val visibleItems = items.take(3).reversed()
            
            visibleItems.forEachIndexed { index, pair ->
                val (item, reasonKey) = pair
                val isTopCard = index == visibleItems.lastIndex
                val stackIndex = visibleItems.lastIndex - index
                
                // Localized Reason
                val reason = when(reasonKey) {
                    "blur" -> strings.blur
                    "underexposed" -> strings.underexposed
                    "overexposed" -> strings.overexposed
                    "smallFile" -> strings.smallFile
                    "lowResolution" -> strings.lowResolution
                    "closedEyes" -> strings.closedEyes
                    "solidColor" -> strings.solidColor
                    else -> reasonKey
                }

                key(item.id) {
                    DraggableQualityCard(
                        item = item,
                        reason = reason,
                        isTopCard = isTopCard,
                        stackIndex = stackIndex,
                        muteVideos = muteVideos,
                        onSwipeLeft = { onSwipe(item, reasonKey, true) },
                        onSwipeRight = { onSwipe(item, reasonKey, false) },
                        onSwipeUp = { onSwipe(item, reasonKey, false) } // Swipe Up = Keep
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
                visible = canUndo,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 }
            ) {
                Button(
                    onClick = onUndo,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(30.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black.copy(alpha = 0.1f)),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    modifier = Modifier.bounceClick(onClick = onUndo)
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
    }
}

@Composable
fun DraggableQualityCard(
    item: MediaItem,
    reason: String,
    isTopCard: Boolean,
    stackIndex: Int,
    muteVideos: Boolean,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeUp: () -> Unit
) {
    val strings = LocalAppStrings.current
    // Animation State
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    
    // Visual Properties
    val scale by animateFloatAsState(targetValue = 1f - (stackIndex * 0.04f)) // Matched Main Page 0.04
    val yOffset by animateDpAsState(targetValue = (stackIndex * 15).dp)
    
    // Rotation logic (Matched Main Page)
    val rotation = (offsetX.value / 60).coerceIn(-10f, 10f)
    
    // Scale logic for swipe up (Make it thinner/slimmer) - Matched Main Page
    val swipeUpScale = if (offsetY.value < 0) {
        (1f + (offsetY.value / 1000f)).coerceAtLeast(0.9f)
    } else {
        1f
    }
    
    val haptic = LocalHapticFeedback.current
    val hapticsAllowed = LocalHapticsEnabled.current
    val threshold = 160f

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.value.roundToInt(), yOffset.roundToPx() + offsetY.value.roundToInt()) }
            .scale(scale * swipeUpScale)
            .rotate(rotation) // Restored rotation for consistency
            .fillMaxWidth(0.85f)
            .aspectRatio(9f/14f) 
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .pointerInput(isTopCard) {
                if (!isTopCard) return@pointerInput
                
                detectDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (offsetX.value < -threshold) {
                                // Left -> Delete
                                if (hapticsAllowed) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                offsetX.animateTo(-1500f, tween(300, easing = FastOutLinearInEasing))
                                onSwipeLeft()
                            } else if (offsetX.value > threshold) {
                                // Right -> Keep
                                if (hapticsAllowed) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                offsetX.animateTo(1500f, tween(300, easing = FastOutLinearInEasing))
                                onSwipeRight()
                            } else if (offsetY.value < -threshold) {
                                // Up -> Keep
                                if (hapticsAllowed) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                offsetY.animateTo(-2000f, tween(300, easing = FastOutLinearInEasing))
                                onSwipeUp()
                            } else {
                                // Reset (Matched Main Page Spring)
                                launch { 
                                    offsetX.animateTo(
                                        0f, 
                                        spring(
                                            stiffness = Spring.StiffnessMedium,
                                            dampingRatio = Spring.DampingRatioLowBouncy
                                        )
                                    ) 
                                }
                                launch { 
                                    offsetY.animateTo(
                                        0f, 
                                        spring(
                                            stiffness = Spring.StiffnessMedium,
                                            dampingRatio = Spring.DampingRatioLowBouncy
                                        )
                                    ) 
                                }
                            }
                        }
                    },
                    onDrag = { change: PointerInputChange, dragAmount: Offset ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount.x)
                            offsetY.snapTo(offsetY.value + dragAmount.y)
                        }
                    }
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Reason Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFEF2F2)) // Red-50
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = reason,
                    color = Color(0xFFDC2626), // Red-600
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            // Photo
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                PhotoCard(
                    item = item,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp), // Only bottom rounded
                    isTopCard = isTopCard,
                    showActions = false, // Minimal UI
                    muteVideos = muteVideos
                )
                
                // Overlay Indicators
                if (offsetX.value < -50) {
                    // Delete Indicator (Red)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 24.dp) // Top attached (padding 0)
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                            .border(2.dp, Color(0xFFEF4444), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)) // Red
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(strings.delete, fontSize = 16.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                } else if (offsetX.value > 50 || offsetY.value < -50) {
                    // Keep Indicator (Blue) - Right or Up
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 24.dp) // Top attached (padding 0)
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                            .border(2.dp, Color(0xFF3B82F6), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)) // Blue
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(strings.skip, fontSize = 16.sp, color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}
