package com.voicelike.app

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import java.util.UUID
import kotlin.math.absoluteValue

@Composable
fun WelcomeView(
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    hasManageStorage: Boolean,
    onRequestManageStorage: () -> Unit,
    onFinish: () -> Unit
) {
    val strings = LocalAppStrings.current
    val haptic = LocalHapticFeedback.current
    val hapticsAllowed = LocalHapticsEnabled.current
    var step by remember { mutableIntStateOf(0) }
    val maxStep = 4 // Intro, Lang, Agreement, Permission, Start
    var isAgreementChecked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6))
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        StepIndicator(current = step, total = maxStep + 1)
        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it * direction } + fadeIn())
                    .togetherWith(slideOutHorizontally { -it * direction } + fadeOut())
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { target ->
            when (target) {
                0 -> WelcomeIntroCard(
                    title = strings.onboardingWelcomeTitle,
                    subtitle = strings.onboardingWelcomeSubtitle
                )
                1 -> WelcomeLanguageStep(
                    title = strings.onboardingLanguageTitle,
                    subtitle = strings.onboardingLanguageSubtitle,
                    currentLanguage = currentLanguage,
                    onLanguageSelected = onLanguageSelected
                )
                2 -> WelcomeAgreementStep(
                    onAgreementChanged = { agreed -> 
                        isAgreementChecked = agreed
                    },
                    isChecked = isAgreementChecked
                )
                3 -> WelcomePermissionStep(
                    title = if (!hasPermission) strings.onboardingPermissionTitle else strings.manageFilesPermission,
                    subtitle = if (!hasPermission) strings.onboardingPermissionSubtitle else strings.manageFilesPermissionDesc,
                    buttonLabel = if (!hasPermission) strings.onboardingPermissionButton else strings.manageFilesPermission,
                    grantedLabel = strings.onboardingPermissionGranted,
                    hasPermission = hasPermission,
                    hasManageStorage = hasManageStorage,
                    onRequestPermission = onRequestPermission,
                    onRequestManageStorage = onRequestManageStorage
                )
                else -> WelcomeStartCard(
                    title = strings.onboardingStartTitle,
                    subtitle = strings.onboardingStartSubtitle
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > 0) {
                TextButton(
                    onClick = { step = (step - 1).coerceAtLeast(0) },
                    modifier = Modifier
                        .height(44.dp)
                        .bounceClick(onClick = { step = (step - 1).coerceAtLeast(0) })
                ) {
                    Text(strings.onboardingBack, fontSize = 14.sp, color = Color.Black)
                }
            } else {
                Spacer(modifier = Modifier.width(64.dp))
            }

            val isLast = step == maxStep
            // Validation Logic
            val canContinue = when(step) {
                2 -> isAgreementChecked
                3 -> hasPermission
                else -> true
            }
            
            val buttonLabel = if (isLast) strings.onboardingStartButton else strings.onboardingNext
            Button(
                onClick = {
                    if (isLast) {
                        onFinish()
                    } else if (canContinue) {
                        if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        step += 1
                    }
                },
                enabled = isLast || canContinue,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .height(44.dp)
                    .bounceClick(onClick = {
                        if (isLast) {
                            onFinish()
                        } else if (canContinue) {
                            if (hapticsAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            step += 1
                        }
                    })
            ) {
                Text(buttonLabel, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StepIndicator(current: Int, total: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        repeat(total) { index ->
            val isActive = index == current
            Box(
                modifier = Modifier
                    .size(if (isActive) 12.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (isActive) Color(0xFF111827) else Color(0xFFE5E7EB))
            )
        }
    }
}

@Composable
private fun WelcomeIntroCard(title: String, subtitle: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start // Changed from implicit default (Start) to explicit Start for clarity, or just ensure Image aligns Start
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(100.dp)
                    .offset(x = (-24).dp) // Move logo left to compensate for built-in padding
                    .align(Alignment.Start)
                    .then(staggeredRevealModifier(0))
            )
            // No Spacer needed if we want it tighter, but keep spacing.
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier
                    .align(Alignment.Start) // Explicitly align to start
                    .then(staggeredRevealModifier(1))
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = Color.Black.copy(alpha = 0.7f),
                lineHeight = 20.sp,
                modifier = Modifier
                    .align(Alignment.Start) // Explicitly align to start
                    .then(staggeredRevealModifier(2))
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WelcomeLanguageStep(
    title: String,
    subtitle: String,
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit
) {
    // Generate a unique ID for this view session to control animation reset
    val pageId = remember { UUID.randomUUID().toString() }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.then(staggeredRevealModifier(0, key = pageId + "title"))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.then(staggeredRevealModifier(1, key = pageId + "subtitle"))
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            val colors = listOf(
                Color(0xFFFFD700),
                Color(0xFFFF6B6B),
                Color(0xFF4ECDC4),
                Color(0xFF45B7D1),
                Color(0xFF96CEB4),
                Color(0xFFFFBE76),
                Color(0xFFDFF9FB)
            )
            val sortedLanguages = remember(currentLanguage) {
                 AppLanguage.values().sortedByDescending { it == currentLanguage }
            }
            val listState = rememberLazyListState()

            // Scroll to top when language changes (so the user sees the selected item at top)
            LaunchedEffect(currentLanguage) {
                listState.scrollToItem(0)
            }
            
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(sortedLanguages, key = { _, lang -> lang.name }) { index, lang ->
                    val isSelected = lang == currentLanguage
                    // Use stable color based on language ordinal, not list index
                    val colorIndex = lang.ordinal.absoluteValue % colors.size
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors[colorIndex])
                            .bounceClick(onClick = { onLanguageSelected(lang) })
                            // Only apply reveal animation for the first few items to prevent "scroll reveal" effect
                            // Use pageId + lang.name as key to ensure animation resets on page re-entry but persists on scroll
                            .then(staggeredRevealModifier(
                                index = index + 2, 
                                key = pageId + lang.name,
                                skipAnimation = index > 8 // Only animate top items
                            ))
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = lang.displayName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(26.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeAgreementStep(
    onAgreementChanged: (Boolean) -> Unit,
    isChecked: Boolean
) {
    val strings = LocalAppStrings.current
    val uriHandler = LocalUriHandler.current

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(100.dp)
                    .offset(x = (-24).dp)
                    .align(Alignment.Start)
                    .then(staggeredRevealModifier(0))
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = strings.termsOfService + " & " + strings.privacyPolicy,
                fontSize = 20.sp, 
                fontWeight = FontWeight.Bold, 
                color = Color.Black,
                modifier = Modifier.then(staggeredRevealModifier(1))
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // Links
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                 TextButton(onClick = { 
                    uriHandler.openUri("https://itlueqqx8t.feishu.cn/wiki/TOCQw5y08iJQ1FkOAKYcnJEWnlf")
                }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                   modifier = Modifier
                       .height(32.dp)
                       .then(staggeredRevealModifier(2))) {
                    Text(strings.termsOfService, fontSize = 14.sp, color = Color(0xFF3B82F6))
                }
                
                TextButton(onClick = { 
                    uriHandler.openUri("https://itlueqqx8t.feishu.cn/wiki/NiG9wQ3LTigcGokeBHEcHvIjngd")
                }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                   modifier = Modifier
                       .height(32.dp)
                       .then(staggeredRevealModifier(3))) {
                    Text(strings.privacyPolicy, fontSize = 14.sp, color = Color(0xFF3B82F6))
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Checkbox
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAgreementChanged(!isChecked) }
                    .then(staggeredRevealModifier(4))
                    .padding(vertical = 8.dp)
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { onAgreementChanged(it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF111827),
                        uncheckedColor = Color.Gray
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.agreeToTerms,
                    fontSize = 14.sp,
                    color = Color.Black,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun WelcomePermissionStep(
    title: String,
    subtitle: String,
    buttonLabel: String,
    grantedLabel: String,
    hasPermission: Boolean,
    hasManageStorage: Boolean,
    onRequestPermission: () -> Unit,
    onRequestManageStorage: () -> Unit
) {
    val strings = LocalAppStrings.current
    val showManageStorage = hasPermission && !hasManageStorage
    
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.then(staggeredRevealModifier(0))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.then(staggeredRevealModifier(1))
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!showManageStorage) {
                        PrivacySummaryRow(
                            title = strings.privacySection1Title,
                            content = strings.privacySection1Content,
                            icon = Icons.Outlined.Storage,
                            color = Color(0xFFF3F4F6),
                            iconColor = Color(0xFF4B5563),
                            modifier = Modifier.then(staggeredRevealModifier(2))
                        )
                        PrivacySummaryRow(
                            title = strings.privacySection2Title,
                            content = strings.privacySection2Content,
                            icon = Icons.Outlined.Security,
                            color = Color(0xFFDCFCE7),
                            iconColor = Color(0xFF16A34A),
                            modifier = Modifier.then(staggeredRevealModifier(3))
                        )
                        PrivacySummaryRow(
                            title = strings.privacySection3Title,
                            content = strings.privacySection3Content,
                            icon = Icons.Outlined.Lock,
                            color = Color(0xFFE0F2FE),
                            iconColor = Color(0xFF0284C7),
                            modifier = Modifier.then(staggeredRevealModifier(4))
                        )
                        PrivacySummaryRow(
                            title = strings.privacySection4Title,
                            content = strings.privacySection4Content,
                            icon = Icons.Outlined.Code,
                            color = Color(0xFFE0E7FF),
                            iconColor = Color(0xFF4338CA),
                            modifier = Modifier.then(staggeredRevealModifier(5))
                        )
                    } else {
                        PrivacySummaryRow(
                            title = strings.manageFilesPermission,
                            content = strings.manageFilesPermissionDesc,
                            icon = Icons.Outlined.Storage,
                            color = Color(0xFFF3F4F6),
                            iconColor = Color(0xFF4B5563),
                            modifier = Modifier.then(staggeredRevealModifier(2))
                        )
                    }
                }
            }
            Button(
                onClick = { 
                    if (!hasPermission) onRequestPermission()
                    else if (showManageStorage) onRequestManageStorage()
                },
                enabled = !hasPermission || showManageStorage,
                colors = ButtonDefaults.buttonColors(containerColor = if (!hasPermission || showManageStorage) Color(0xFF111827) else Color(0xFFE5E7EB)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Text(
                    text = if (!hasPermission) buttonLabel else if (showManageStorage) strings.manageFilesPermission else grantedLabel,
                    color = if (!hasPermission || showManageStorage) Color.White else Color.Black.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun WelcomeStartCard(title: String, subtitle: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .offset(x = (-16).dp)
                    .align(Alignment.Start)
                    .then(staggeredRevealModifier(0))
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.then(staggeredRevealModifier(1))
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = Color.Black.copy(alpha = 0.7f),
                lineHeight = 20.sp,
                modifier = Modifier.then(staggeredRevealModifier(2))
            )
        }
    }
}

@Composable
private fun PrivacySummaryRow(
    title: String,
    content: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black.copy(alpha = 0.8f))
            Text(
                text = content,
                fontSize = 11.sp,
                color = Color.Black.copy(alpha = 0.6f),
                lineHeight = 16.sp
            )
        }
    }
}
