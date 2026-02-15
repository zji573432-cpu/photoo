package com.voicelike.app

import android.location.Geocoder
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.roundToInt

@Composable
fun PhotoCard(
    item: MediaItem,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    isTopCard: Boolean = true,
    stackIndex: Int = 0,
    isExpanded: Boolean = false,
    muteVideos: Boolean = true,
    onToggleExpand: () -> Unit = {},
    showActions: Boolean = true,
    onDataLoaded: (MediaItem) -> Unit = {}
) {
    var isMuted by remember { mutableStateOf(muteVideos) }
    val context = LocalContext.current
    val strings = LocalAppStrings.current

    // EXIF State
    var exifLoaded by remember { mutableStateOf(false) }
    var currentItem by remember { mutableStateOf(item) }

    // Load EXIF
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
                        
                        // Reverse Geocoding
                        if (latLong != null) {
                            try {
                                val geocoder = Geocoder(context, Locale.getDefault())
                                @Suppress("DEPRECATION")
                                val addresses = geocoder.getFromLocation(latLong[0], latLong[1], 1)
                                if (!addresses.isNullOrEmpty()) {
                                    val address = addresses[0]
                                    val city = address.locality ?: address.subAdminArea ?: address.adminArea
                                    val country = address.countryName
                                    if (city != null) {
                                        location = if (country != null) "$city, $country" else city
                                    }
                                }
                            } catch (e: Exception) {
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
                        onDataLoaded(currentItem)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color.White)
            .clip(shape)
    ) {
        // --- Media Content ---
        if (item.type == "video") {
            if (isTopCard && !isExpanded) {
                VideoPlayer(item = item, isMuted = isMuted, shouldPlay = isTopCard)
                
                if (showActions) {
                    IconButton(
                        onClick = { isMuted = !isMuted },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .bounceClick(onClick = { isMuted = !isMuted })
                            .background(Color.Black.copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        AnimatedContent(
                            targetState = isMuted,
                            transitionSpec = {
                                scaleIn() togetherWith scaleOut()
                            },
                            label = "mute_icon"
                        ) { muted ->
                            Icon(
                                imageVector = if (muted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                                contentDescription = "Mute",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.uri)
                            .decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
                            .crossfade(true)
                            .build(),
                        loading = { SkeletonLoader(Modifier.fillMaxSize()) },
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
            Box(Modifier.fillMaxSize()) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.uri)
                        .crossfade(false) // Disable crossfade to prevent transparency flash during undo/load
                        .build(),
                    loading = { SkeletonLoader(Modifier.fillMaxSize()) },
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                
                if (isTopCard && !isExpanded) {
                     VideoPlayer(
                         item = item.copy(uri = item.livePhotoVideoUri!!, mimeType = "video/mp4"),
                         isMuted = isMuted,
                         shouldPlay = isTopCard,
                         playerResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                     )
                     
                     Box(
                         modifier = Modifier
                             .align(Alignment.TopStart)
                             .padding(16.dp)
                             .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                             .padding(horizontal = 6.dp, vertical = 2.dp)
                     ) {
                         Text("LIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                     }
                     
                     if (showActions) {
                         IconButton(
                            onClick = { isMuted = !isMuted },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .bounceClick(onClick = { isMuted = !isMuted })
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.2f))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                        ) {
                            AnimatedContent(
                                targetState = isMuted,
                                transitionSpec = {
                                    scaleIn() togetherWith scaleOut()
                                },
                                label = "mute_icon_live"
                            ) { muted ->
                                Icon(
                                    imageVector = if (muted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                                    contentDescription = "Mute",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                     }
                } else {
                     Box(
                         modifier = Modifier
                             .align(Alignment.TopStart)
                             .padding(16.dp)
                             .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                             .padding(horizontal = 6.dp, vertical = 2.dp)
                     ) {
                         Text("LIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                     }
                }
            }
        } else {
            // Photo
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.uri)
                        .crossfade(true)
                        .build(),
                    loading = { SkeletonLoader(Modifier.fillMaxSize()) },
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        // Info Button
        if (!isExpanded && showActions) {
            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .size(32.dp) // Smaller button size
                    .bounceClick(onClick = onToggleExpand)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.2f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Info",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp) // Smaller icon size
                )
            }
        }
        
        // Expanded EXIF Overlay (Identical to DraggableCard)
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
                    ) { /* Absorb clicks */ }
            ) {
                // Close Button
                IconButton(
                    onClick = onToggleExpand,
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
                        .padding(32.dp)
                        .offset(y = (-40).dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val date = Date(item.dateAdded * 1000)
                    val format = java.text.SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
                    
                    Text(format.format(date), fontFamily = FontFamily.Monospace, color = Color.Black.copy(alpha = 0.4f), letterSpacing = 2.sp)
                    Spacer(modifier = Modifier.height(32.dp))
                    
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
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(strings.filename ?: "Filename", fontSize = 10.sp, color = Color.Black.copy(alpha = 0.4f), letterSpacing = 1.sp)
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Grid
                    Row(Modifier.fillMaxWidth()) {
                         Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                             Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Image, null, modifier = Modifier.size(24.dp), tint = Color.Black.copy(alpha = 0.8f))
                                Spacer(Modifier.height(4.dp))
                                Text(currentItem.resolution.ifEmpty { "--" }, fontFamily = FontFamily.Serif, fontSize = 14.sp)
                                Text(strings.resolution ?: "Resolution", fontSize = 10.sp, color = Color.Black.copy(alpha = 0.4f), letterSpacing = 1.sp)
                            }
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val sizeStr = if (currentItem.size > 1024 * 1024) String.format("%.1f MB", currentItem.size / 1024.0 / 1024.0) else String.format("%.0f KB", currentItem.size / 1024.0)
                                Icon(Icons.Outlined.Save, null, modifier = Modifier.size(24.dp), tint = Color.Black.copy(alpha = 0.8f))
                                Spacer(Modifier.height(4.dp))
                                Text(sizeStr, fontFamily = FontFamily.Serif, fontSize = 14.sp)
                                Text(strings.size ?: "Size", fontSize = 10.sp, color = Color.Black.copy(alpha = 0.4f), letterSpacing = 1.sp)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(Modifier.fillMaxWidth()) {
                         Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                             Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.PhotoCamera, null, modifier = Modifier.size(24.dp), tint = Color.Black.copy(alpha = 0.8f))
                                Spacer(Modifier.height(4.dp))
                                Text(currentItem.camera, fontFamily = FontFamily.Serif, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(strings.camera ?: "Camera", fontSize = 10.sp, color = Color.Black.copy(alpha = 0.4f), letterSpacing = 1.sp)
                            }
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.LocationOn, null, modifier = Modifier.size(24.dp), tint = Color.Black.copy(alpha = 0.8f))
                                Spacer(Modifier.height(4.dp))
                                Text(if (currentItem.location == "Unknown Location") strings.unknownLocation ?: "Unknown" else currentItem.location, fontFamily = FontFamily.Serif, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(strings.location ?: "Location", fontSize = 10.sp, color = Color.Black.copy(alpha = 0.4f), letterSpacing = 1.sp)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(Modifier.fillMaxWidth()) {
                         Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                             Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Iso, null, modifier = Modifier.size(24.dp), tint = Color.Black.copy(alpha = 0.8f))
                                Spacer(Modifier.height(4.dp))
                                Text(currentItem.iso, fontFamily = FontFamily.Serif, fontSize = 14.sp)
                                Text(strings.iso ?: "ISO", fontSize = 10.sp, color = Color.Black.copy(alpha = 0.4f), letterSpacing = 1.sp)
                            }
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Timer, null, modifier = Modifier.size(24.dp), tint = Color.Black.copy(alpha = 0.8f))
                                Spacer(Modifier.height(4.dp))
                                Text(currentItem.shutter, fontFamily = FontFamily.Serif, fontSize = 14.sp)
                                Text(strings.shutter ?: "Shutter", fontSize = 10.sp, color = Color.Black.copy(alpha = 0.4f), letterSpacing = 1.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(Modifier.fillMaxWidth()) {
                         Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                             Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Camera, null, modifier = Modifier.size(24.dp), tint = Color.Black.copy(alpha = 0.8f))
                                Spacer(Modifier.height(4.dp))
                                Text(currentItem.focalLength, fontFamily = FontFamily.Serif, fontSize = 14.sp)
                                Text("Focal", fontSize = 10.sp, color = Color.Black.copy(alpha = 0.4f), letterSpacing = 1.sp)
                            }
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Lens, null, modifier = Modifier.size(24.dp), tint = Color.Black.copy(alpha = 0.8f))
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
