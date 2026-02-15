package com.voicelike.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.draw.scale

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    batchSize: Int,
    queueOrder: String,
    muteVideos: Boolean,
    soundEffectsEnabled: Boolean,
    hapticsEnabled: Boolean,
    avatarType: String,
    onBatchSizeChange: (Int) -> Unit,
    onQueueOrderChange: (String) -> Unit,
    onMuteVideosChange: (Boolean) -> Unit,
    onSoundEffectsChange: (Boolean) -> Unit,
    onHapticsEnabledChange: (Boolean) -> Unit,
    onAvatarChange: (String) -> Unit,
    onReleaseKept: () -> Unit,
    onLanguageClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onOpenSourceClick: () -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val currentLang = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val hapticsAllowed = LocalHapticsEnabled.current
    val layoutDirection = LocalLayoutDirection.current
    var releasedMessageVisible by remember { mutableStateOf(false) }
    var showReleaseDialog by remember { mutableStateOf(false) }

    if (showReleaseDialog) {
        AlertDialog(
            onDismissRequest = { showReleaseDialog = false },
            title = { Text(strings.releaseDialogTitle, fontWeight = FontWeight.Bold) },
            text = { Text(strings.releaseDialogContent) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReleaseKept()
                        scope.launch {
                            releasedMessageVisible = true
                            delay(2000)
                            releasedMessageVisible = false
                        }
                        showReleaseDialog = false
                    }
                ) {
                    Text(strings.confirm, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReleaseDialog = false }) {
                    Text(strings.cancel, color = Color.Gray)
                }
            },
            containerColor = Color.White,
            textContentColor = Color.Black,
            titleContentColor = Color.Black
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, bottom = 24.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .bounceClick(onClick = onBack)
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
                text = strings.settings,
                fontSize = if (LocalAppLanguage.current == AppLanguage.Russian) 16.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = if (LocalAppLanguage.current == AppLanguage.Russian) 18.sp else androidx.compose.ui.unit.TextUnit.Unspecified
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            // Language
            Text(
                text = strings.language,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.then(staggeredRevealModifier(0))
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF3F4F6))
                    .bounceClick(onClick = onLanguageClick)
                    .then(staggeredRevealModifier(1))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentLang.displayName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = if (layoutDirection == LayoutDirection.Rtl) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = strings.privacyButton,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.then(staggeredRevealModifier(2))
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF3F4F6))
                    .bounceClick(onClick = {
                        if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPrivacyClick()
                    })
                    .then(staggeredRevealModifier(3))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.privacyTitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = if (layoutDirection == LayoutDirection.Rtl) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(staggeredRevealModifier(4)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = strings.muteVideos,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = muteVideos,
                    onCheckedChange = {
                        if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMuteVideosChange(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color.Black,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.Gray.copy(alpha = 0.3f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sound Effects
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(staggeredRevealModifier(5)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = strings.soundEffects,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = soundEffectsEnabled,
                    onCheckedChange = {
                        if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSoundEffectsChange(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color.Black,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.Gray.copy(alpha = 0.3f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(staggeredRevealModifier(6)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = strings.haptics,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = hapticsEnabled,
                    onCheckedChange = {
                        if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onHapticsEnabledChange(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color.Black,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.Gray.copy(alpha = 0.3f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Batch Size
            Text(
                text = strings.batchSize,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.then(staggeredRevealModifier(7))
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(staggeredRevealModifier(8))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "10", fontSize = 12.sp, color = Color.Gray)
                        Text(text = "$batchSize", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(text = "30", fontSize = 12.sp, color = Color.Gray)
                    }
                    Slider(
                        value = batchSize.toFloat(),
                        onValueChange = { value ->
                            val rounded = value.roundToInt().coerceIn(10, 30)
                            if (rounded != batchSize) {
                                if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onBatchSizeChange(rounded)
                            }
                        },
                        valueRange = 10f..30f,
                        steps = 19, // (30-10)/1 - 1 = 19 steps
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Black,
                            activeTrackColor = Color.Black,
                            inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Queue Order
            Text(
                text = strings.queueOrder,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.then(staggeredRevealModifier(9))
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(staggeredRevealModifier(10)),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Random Option
                FilterChip(
                    selected = queueOrder == "random",
                    onClick = {
                        if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onQueueOrderChange("random")
                    },
                    label = { Text(strings.orderRandom) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.Black,
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFFF3F4F6),
                        labelColor = Color.Black
                    ),
                    border = null,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(48.dp).bounceClick()
                )
                
                // Time Option
                FilterChip(
                    selected = queueOrder == "time",
                    onClick = {
                        if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onQueueOrderChange("time")
                    },
                    label = { Text(strings.orderTime) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.Black,
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFFF3F4F6),
                        labelColor = Color.Black
                    ),
                    border = null,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(48.dp).bounceClick()
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            // Avatar
            if (currentLang != AppLanguage.Arabic) {
                Text(
                    text = strings.avatar,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.then(staggeredRevealModifier(11))
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(staggeredRevealModifier(12)),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Girl Option
                    FilterChip(
                        selected = avatarType == "girl",
                        onClick = { 
                                if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAvatarChange("girl") 
                        },
                        label = { Text(strings.avatarGirl) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.Black,
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFFF3F4F6),
                            labelColor = Color.Black
                        ),
                        border = null,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(48.dp).bounceClick()
                    )
                    
                    // Boy Option
                    FilterChip(
                        selected = avatarType == "boy",
                        onClick = { 
                            if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAvatarChange("boy") 
                        },
                        label = { Text(strings.avatarBoy) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.Black,
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFFF3F4F6),
                            labelColor = Color.Black
                        ),
                        border = null,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(48.dp).bounceClick()
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            } else {
                // Enforce boy avatar for Arabic if not already set
                LaunchedEffect(Unit) {
                    if (avatarType != "boy") {
                        onAvatarChange("boy")
                    }
                }
            }
            
            // Release Kept Photos
            Text(
                text = strings.releaseKept,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.then(staggeredRevealModifier(13))
            )
            Text(
                text = strings.releaseKeptDesc,
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .then(staggeredRevealModifier(14))
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { 
                    if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showReleaseDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .then(staggeredRevealModifier(15))
            ) {
                if (releasedMessageVisible) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.released)
                } else {
                    Text(strings.releaseKept)
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Privacy Links
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .then(staggeredRevealModifier(16)),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = { 
                    uriHandler.openUri("https://itlueqqx8t.feishu.cn/wiki/TOCQw5y08iJQ1FkOAKYcnJEWnlf")
                }) {
                    Text(strings.termsOfService ?: "Terms of Service", fontSize = 12.sp, color = Color.Gray)
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                TextButton(onClick = { 
                    uriHandler.openUri("https://itlueqqx8t.feishu.cn/wiki/NiG9wQ3LTigcGokeBHEcHvIjngd")
                }) {
                    Text(strings.privacyPolicy ?: "Privacy Policy", fontSize = 12.sp, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            
            // Open Source Licenses
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(staggeredRevealModifier(17)),
                contentAlignment = Alignment.Center
            ) {
                TextButton(onClick = onOpenSourceClick) {
                    Text(
                        text = strings.openSourceLicenses ?: "Open Source Licenses",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            // Version & Copyright
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(staggeredRevealModifier(18)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${strings.version} 2.0  ${strings.copyright} 2026",
                    fontSize = 12.sp,
                    color = Color.Gray.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
