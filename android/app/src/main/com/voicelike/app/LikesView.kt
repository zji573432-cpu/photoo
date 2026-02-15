package com.voicelike.app

import android.location.Geocoder
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.common.util.UnstableApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.exifinterface.media.ExifInterface
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Locale
import kotlin.math.roundToInt

import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ArrowDropDown
import java.io.File

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun LikesView(
    likedItems: List<MediaItem>,
    onBack: () -> Unit,
    onShare: (MediaItem) -> Unit,
    prefs: PreferencesManager,
    allMedia: List<MediaItem>,
    folderUsage: Map<String, Int>,
    userCreatedFolders: Set<String>
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val topInset = 48.dp
    val scope = rememberCoroutineScope()
    
    // Danmaku State
    var danmakuEnabled by remember { mutableStateOf(prefs.danmakuEnabled) }

    // Selection State (null = Likes, String = Folder Name)
    var selectedBucketName by remember { mutableStateOf<String?>(null) }
    var showDropdown by remember { mutableStateOf(false) }

    // Liked Items State (Hoisted for Remove Dialog visibility)
    // Renamed concept to "displayQueue" but keeping variable name to minimize diff
    val likedQueueState = remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    val lastAppendSize = remember { mutableStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { kotlin.math.max(1, likedQueueState.value.size) })
    val folderCache = remember { mutableStateMapOf<String, List<MediaItem>>() }
    var isFolderLoading by remember { mutableStateOf(false) }

    // Computed Folders
    // Use local state for sorting counts to trigger recomposition
    var likesFolderCounts by remember { mutableStateOf(prefs.likesFolderSelectionCounts) }
    
    val folders = remember(allMedia, likesFolderCounts, userCreatedFolders) {
        val groups = allMedia.groupBy { it.bucketName }
        groups.keys.filter { !it.isBlank() }.sortedWith(
            compareByDescending<String> { name ->
                // Sort by selection count in Likes view
                likesFolderCounts[name] ?: 0
            }
        )
    }

    // Current Source Items for Infinite Scroll
    var currentSourceItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    LaunchedEffect(allMedia) {
        folderCache.clear()
    }

    LaunchedEffect(likedItems, selectedBucketName, allMedia) {
        val bucket = selectedBucketName
        if (bucket == null) {
            isFolderLoading = false
            currentSourceItems = likedItems
            likedQueueState.value = if (likedItems.isNotEmpty()) {
                withContext(Dispatchers.Default) { likedItems.shuffled() }
            } else {
                emptyList()
            }
        } else {
            val cached = folderCache[bucket]
            if (cached != null) {
                isFolderLoading = false
                currentSourceItems = cached
                likedQueueState.value = cached
            } else {
                isFolderLoading = true
                val source = withContext(Dispatchers.IO) {
                    allMedia.filter {
                        it.bucketName == bucket &&
                        File(it.path ?: "").exists()
                    }
                }
                val sorted = withContext(Dispatchers.Default) {
                    source.sortedByDescending { it.dateAdded }
                }
                folderCache[bucket] = sorted
                currentSourceItems = sorted
                likedQueueState.value = sorted
                isFolderLoading = false
            }
        }
        
        lastAppendSize.value = likedQueueState.value.size
        if (likedQueueState.value.isNotEmpty()) {
            pagerState.scrollToPage(0)
        }
    }
    
    // Comment State
    var showCommentSheet by remember { mutableStateOf(false) }
    var currentCommentItem by remember { mutableStateOf<MediaItem?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Comment Deletion State
    var showDeleteCommentDialog by remember { mutableStateOf(false) }
    var commentToDeleteIndex by remember { mutableStateOf(-1) }

    // Remove State
    var showRemoveDialog by remember { mutableStateOf(false) }
    var itemToRemove by remember { mutableStateOf<MediaItem?>(null) }

    AnimatedVisibility(
        visible = showCommentSheet && currentCommentItem != null,
        enter = FluidTransitions.SheetEnter,
        exit = FluidTransitions.SheetExit
    ) {
        val item = currentCommentItem ?: return@AnimatedVisibility
        val itemId = item.id
        var comments by remember { mutableStateOf(prefs.comments[itemId] ?: emptyList()) }
        var input by remember { mutableStateOf(TextFieldValue("")) }
        val clipboardManager = LocalClipboardManager.current
        var expandedCommentIndex by remember { mutableStateOf(-1) }
        
        ModalBottomSheet(
            onDismissRequest = { showCommentSheet = false },
            sheetState = sheetState,
            containerColor = Color.White,
            scrimColor = Color.Black.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxHeight(0.5f)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .imePadding()
            ) {
                // Header
                Text(
                    text = "${comments.size} ${strings.commentsHeader}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)
                )
                
                // List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(comments.size) { index ->
                        val comment = comments[index]
                        val isCommentMenuExpanded = expandedCommentIndex == index
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isCommentMenuExpanded) Color.Black.copy(alpha = 0.06f) else Color.Transparent
                                )
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        expandedCommentIndex = index
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Avatar
                                val avatarRes = if (prefs.avatarType == "boy") R.drawable.boy else R.drawable.girl
                                AsyncImage(
                                    model = ImageRequest.Builder(context = context)
                                        .data(avatarRes)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color.LightGray)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = strings.commentUserMe,
                                        fontSize = 14.sp,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = comment,
                                        fontSize = 18.sp,
                                        color = Color.Black
                                    )
                                }
                            }
                            
                            MaterialTheme(
                                shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp))
                            ) {
                                DropdownMenu(
                                    expanded = isCommentMenuExpanded,
                                    onDismissRequest = { expandedCommentIndex = -1 },
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.9f))
                                        .width(180.dp)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(strings.copyComment, color = Color.White) },
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(comment))
                                            Toast.makeText(context, strings.commentCopied, Toast.LENGTH_SHORT).show()
                                            expandedCommentIndex = -1
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                    DropdownMenuItem(
                                        text = { Text(strings.delete, color = Color(0xFFFF6B6B)) },
                                        onClick = {
                                            commentToDeleteIndex = index
                                            showDeleteCommentDialog = true
                                            expandedCommentIndex = -1
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                if (showDeleteCommentDialog && commentToDeleteIndex != -1) {
                    AlertDialog(
                        onDismissRequest = { 
                            showDeleteCommentDialog = false 
                            commentToDeleteIndex = -1
                        },
                        title = { Text(strings.deleteCommentTitle) },
                        text = { Text(strings.deleteCommentMessage) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (commentToDeleteIndex in comments.indices) {
                                        val newComments = comments.toMutableList()
                                        newComments.removeAt(commentToDeleteIndex)
                                        comments = newComments
                                        val allComments = prefs.comments
                                        allComments[itemId] = newComments
                                        prefs.comments = allComments
                                    }
                                    showDeleteCommentDialog = false
                                    commentToDeleteIndex = -1
                                }
                            ) {
                                Text(strings.delete, color = Color.Red)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { 
                                showDeleteCommentDialog = false 
                                commentToDeleteIndex = -1
                            }) {
                                Text(strings.cancel)
                            }
                        },
                        containerColor = Color.White
                    )
                }
                
                // Input
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text(strings.commentPlaceholder) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF3F4F6),
                            unfocusedContainerColor = Color(0xFFF3F4F6),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (input.text.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val newComment = input.text.trim()
                                if (newComment.isNotEmpty()) {
                                    val newComments = comments + newComment
                                    comments = newComments
                                    val allComments = prefs.comments
                                    allComments[itemId] = newComments
                                    prefs.comments = allComments
                                    input = TextFieldValue("")
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.Send, strings.send, tint = Color(0xFFFE2C55))
                        }
                    }
                }
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { /* Absorb clicks */ }
    ) {
        if (isFolderLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                }
            }
        } else if (likedQueueState.value.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (selectedBucketName == null) strings.noLikes else "No items in folder",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 16.sp
                )
            }
        } else {
            LaunchedEffect(likedQueueState.value.size) {
                val size = likedQueueState.value.size
                if (size > 0 && pagerState.currentPage >= size) {
                    pagerState.scrollToPage(size - 1)
                }
            }
            LaunchedEffect(pagerState.currentPage, likedQueueState.value.size, currentSourceItems.size) {
                if (currentSourceItems.isNotEmpty() &&
                    pagerState.currentPage >= likedQueueState.value.size - 2 &&
                    lastAppendSize.value == likedQueueState.value.size
                ) {
                    val extra = withContext(Dispatchers.Default) { currentSourceItems.shuffled() }
                    likedQueueState.value = likedQueueState.value + extra
                    lastAppendSize.value = likedQueueState.value.size
                }
            }
            
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { index -> 
                    val itemId = likedQueueState.value.getOrNull(index)?.id
                    if (itemId != null) "${itemId}_$index" else "empty_$index"
                }
            ) { page ->
                val item = likedQueueState.value.getOrNull(page) ?: return@VerticalPager
                val isActive = page == pagerState.currentPage
                var currentItem by remember { mutableStateOf(item) }
                var exifLoaded by remember { mutableStateOf(false) }

                LaunchedEffect(item.id) {
                    if (!exifLoaded && item.type == "photo") {
                        withContext(Dispatchers.IO) {
                            try {
                                context.contentResolver.openInputStream(item.uri)?.use { stream: InputStream ->
                                    val exif = ExifInterface(stream)
                                    val latLong = exif.latLong
                                    var location = "Unknown Location"
                                    
                                    if (latLong != null) {
                                        try {
                                            val geocoder = Geocoder(context, Locale.getDefault())
                                            @Suppress("DEPRECATION")
                                            val addresses = geocoder.getFromLocation(latLong[0], latLong[1], 1)
                                            if (!addresses.isNullOrEmpty()) {
                                                val address = addresses[0]
                                                location = if (address.locality != null) {
                                                    "${address.locality}, ${address.countryName}"
                                                } else {
                                                    address.getAddressLine(0)
                                                }
                                                currentItem = currentItem.copy(location = location)
                                            }
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                    exifLoaded = true
                                }
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                }
                
                Box(Modifier.fillMaxSize()) {
                    if (item.type == "video") {
                        VideoPlayer(item = item, isMuted = false, shouldPlay = isActive, playerResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT)
                    } else if (item.isLivePhoto && item.livePhotoVideoUri != null) {
                         VideoPlayer(item = item.copy(uri = item.livePhotoVideoUri!!, mimeType = "video/mp4"), isMuted = false, shouldPlay = isActive, playerResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT)
                         
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 16.dp, top = topInset + 48.dp)
                                .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("LIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(context = context)
                                .data(item.uri)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    // Danmaku Overlay (Inside Pager Item)
                    if (danmakuEnabled) {
                        val comments = prefs.comments[item.id] ?: emptyList()
                        DanmakuOverlay(comments = comments)
                    }
                    
                    // Bottom Info (Location/Date)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 16.dp)
                    ) {
                        if (currentItem.location != "Unknown Location") {
                            Text(
                                text = currentItem.location,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(currentItem.dateAdded * 1000))
                        Text(
                            text = dateStr,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            
            // Top Dropdown Bar
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topInset)
                    .zIndex(10f),
                contentAlignment = Alignment.Center
            ) {
                 // Use a Material3 styled Surface for better elevation and shape
                 Surface(
                     shape = RoundedCornerShape(24.dp),
                     color = Color.Black.copy(alpha = 0.6f),
                     contentColor = Color.White,
                     shadowElevation = 4.dp,
                     onClick = { showDropdown = true },
                     modifier = Modifier.height(40.dp).bounceClick()
                 ) {
                     Row(
                         verticalAlignment = Alignment.CenterVertically,
                         modifier = Modifier.padding(horizontal = 16.dp)
                     ) {
                         Text(
                             text = selectedBucketName ?: strings.likes,
                             fontWeight = FontWeight.Bold,
                             fontSize = 16.sp
                         )
                         Spacer(Modifier.width(4.dp))
                         Icon(
                             imageVector = if (showDropdown) Icons.Default.KeyboardArrowUp else Icons.Default.ArrowDropDown,
                             contentDescription = null,
                             modifier = Modifier.size(20.dp)
                         )
                     }
                 }
                 
                 // Material3 DropdownMenu with shape
                val menuMaxHeight = 320.dp
                // Offset removed to keep it close to the header
                MaterialTheme(
                    shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp))
                ) {
                    DropdownMenu(
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false },
                        offset = DpOffset(0.dp, 4.dp), // Minimal spacing
                        modifier = Modifier
                           .background(Color.Black.copy(alpha = 0.9f))
                           .heightIn(max = menuMaxHeight)
                           .width(200.dp)
                    ) {
                         // Header
                         DropdownMenuItem(
                             text = { Text(strings.likes, fontWeight = FontWeight.Bold, color = Color.White) },
                             onClick = {
                                 selectedBucketName = null
                                 showDropdown = false
                             },
                             leadingIcon = { Icon(Icons.Outlined.Favorite, null, tint = Color(0xFFFF5252)) },
                             trailingIcon = if (selectedBucketName == null) {
                                 { Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                             } else null,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.bounceClick()
                         )
                         
                         HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color.White.copy(alpha = 0.2f))
                         
                         // Scrollable content handled by DropdownMenu internally, but let's ensure it looks good
                         folders.forEach { name ->
                             DropdownMenuItem(
                                 text = { 
                                     Text(
                                        text = name,
                                        color = Color.White,
                                        fontWeight = if (selectedBucketName == name) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                     )
                                 },
                                 onClick = {
                                     selectedBucketName = name
                                     showDropdown = false
                                     // Update selection count for sorting
                                     val newCounts = likesFolderCounts.toMutableMap()
                                     newCounts[name] = (newCounts[name] ?: 0) + 1
                                     prefs.likesFolderSelectionCounts = newCounts
                                     likesFolderCounts = newCounts
                                 },
                                 leadingIcon = { 
                                     Icon(
                                         imageVector = Icons.Outlined.Folder, 
                                         contentDescription = null, 
                                         tint = Color.LightGray 
                                     ) 
                                 },
                                 trailingIcon = if (selectedBucketName == name) {
                                     { Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                                 } else null,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.bounceClick()
                             )
                         }
                     }
                 }
            }

            // Right Side Action Buttons
            val currentItem = likedQueueState.value.getOrNull(pagerState.currentPage)
            if (currentItem != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Comment Button
                    val commentCount = prefs.comments[currentItem.id]?.size ?: 0
                    
                    Box {
                        IconButton(
                            onClick = {
                                currentCommentItem = currentItem
                                showCommentSheet = true
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .bounceClick(onClick = {
                                    currentCommentItem = currentItem
                                    showCommentSheet = true
                                })
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                contentDescription = strings.comment,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        // Comment Count Badge
                        if (commentCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 0.dp, y = 0.dp) // Adjusted to be further from center
                                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (commentCount > 99) "99+" else commentCount.toString(),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Share Button
                    IconButton(
                        onClick = { onShare(currentItem) },
                        modifier = Modifier
                            .size(56.dp)
                            .bounceClick(onClick = { onShare(currentItem) })
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = strings.share,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    // Danmaku Switch
                    Box {
                        IconButton(
                            onClick = { 
                                danmakuEnabled = !danmakuEnabled 
                                prefs.danmakuEnabled = danmakuEnabled
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .bounceClick(onClick = { 
                                    danmakuEnabled = !danmakuEnabled 
                                    prefs.danmakuEnabled = danmakuEnabled
                                })
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notes,
                                contentDescription = "Danmaku",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        if (danmakuEnabled) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd) // Moved to bottom-right quadrant
                                    .padding(4.dp) // Padding inside the button
                                    .background(Color.White, CircleShape)
                                    .padding(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }

                    // Remove Like Button (Only in Likes Mode)
                    if (selectedBucketName == null) {
                        IconButton(
                            onClick = {
                                itemToRemove = currentItem
                                showRemoveDialog = true
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .bounceClick(onClick = {
                                    itemToRemove = currentItem
                                    showRemoveDialog = true
                                })
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.HeartBroken,
                                contentDescription = strings.removeLike,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }
        
        // Remove Dialog
        if (showRemoveDialog && itemToRemove != null) {
            AlertDialog(
                onDismissRequest = { 
                    showRemoveDialog = false 
                    itemToRemove = null
                },
                title = { Text(strings.removeLikeTitle) },
                text = { Text(strings.removeLikeMessage) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val item = itemToRemove!!
                            val newList = likedQueueState.value.filter { it.id != item.id }
                            likedQueueState.value = newList
                            
                            val liked = prefs.likedIds
                            liked.remove(item.id)
                            prefs.likedIds = liked
                            
                            showRemoveDialog = false
                            itemToRemove = null
                        }
                    ) {
                        Text(strings.remove, color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showRemoveDialog = false 
                        itemToRemove = null
                    }) {
                        Text(strings.cancel)
                    }
                },
                containerColor = Color.White
            )
        }
        
        // Back Button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = topInset, start = 16.dp)
                .bounceClick(onClick = onBack)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.2f))
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowBack,
                contentDescription = strings.back,
                tint = Color.White
            )
        }
    }
}

@Composable
fun DanmakuOverlay(
    comments: List<String>,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
        if (comments.isNotEmpty()) {
            val trackHeight = 60.dp
            val startY = 100.dp 
            
            val playOnce = comments.size <= 3
            val trackCount = if (playOnce) comments.size else minOf(5, comments.size)
            
            val nextIndex = remember(comments) { java.util.concurrent.atomic.AtomicInteger(0) }
            
            for (i in 0 until trackCount) {
                DanmakuTrack(
                    comments = comments,
                    nextIndex = if (playOnce) null else nextIndex,
                    topOffset = startY + trackHeight * i,
                    playOnce = playOnce,
                    fixedIndex = if (playOnce) i else null
                )
            }
        }
    }
}

@Composable
fun DanmakuTrack(
    comments: List<String>, 
    nextIndex: java.util.concurrent.atomic.AtomicInteger?, 
    topOffset: Dp,
    playOnce: Boolean,
    fixedIndex: Int?
) {
    var text by remember { mutableStateOf("") }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val density = LocalDensity.current
    val startX = with(density) { screenWidth.toPx() }
    val endX = -1000f

    val anim = remember { Animatable(startX) }

    LaunchedEffect(comments, playOnce, fixedIndex) {
        if (playOnce) {
            val index = fixedIndex ?: 0
            if (index in comments.indices) {
                text = comments[index]
                anim.snapTo(startX)
                val duration = (8000..15000).random()
                anim.animateTo(endX, animationSpec = tween(duration, easing = LinearEasing))
            }
        } else {
            while(true) {
                val index = (nextIndex?.getAndIncrement() ?: 0) % comments.size
                text = comments[index]
                
                anim.snapTo(startX)
                val duration = (8000..15000).random()
                anim.animateTo(endX, animationSpec = tween(duration, easing = LinearEasing))
                
                val delayMin = 500L
                val delayMax = 2000L
                kotlinx.coroutines.delay((delayMin..delayMax).random())
            }
        }
    }

    if (text.isNotEmpty()) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier
                .offset(y = topOffset)
                .offset { IntOffset(anim.value.roundToInt(), 0) }
                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
