package com.voicelike.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.clickable

@Composable
fun PrivacyView(
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val uriHandler = LocalUriHandler.current

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
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.05f))
            ) {
                Icon(Icons.Outlined.ArrowBack, strings.back, tint = Color.Black)
            }

            Text(
                text = strings.privacyTitle,
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
            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: Data Collection
            PrivacyCard(
                title = strings.privacySection1Title,
                content = strings.privacySection1Content,
                icon = Icons.Outlined.Storage,
                color = Color(0xFFF3F4F6), // Neutral Gray/Whiteish
                iconColor = Color(0xFF4B5563), // Gray 600
                modifier = Modifier.then(staggeredRevealModifier(0))
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section 2: Privacy Protection (Green)
            PrivacyCard(
                title = strings.privacySection2Title,
                content = strings.privacySection2Content,
                icon = Icons.Outlined.Security,
                color = Color(0xFFDCFCE7), // Green 100
                iconColor = Color(0xFF16A34A), // Green 600
                modifier = Modifier.then(staggeredRevealModifier(1))
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section 3: Data Safety (Blue)
            PrivacyCard(
                title = strings.privacySection3Title,
                content = strings.privacySection3Content,
                icon = Icons.Outlined.Lock,
                color = Color(0xFFE0F2FE), // Blue 100
                iconColor = Color(0xFF0284C7), // Blue 600
                modifier = Modifier.then(staggeredRevealModifier(2))
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section 4: Open Source (Indigo)
            PrivacyCard(
                title = strings.privacySection4Title,
                content = strings.privacySection4Content,
                icon = Icons.Outlined.Code,
                color = Color(0xFFE0E7FF), // Indigo 100
                iconColor = Color(0xFF4338CA), // Indigo 700
                modifier = Modifier.then(staggeredRevealModifier(3)),
                onClick = {
                    uriHandler.openUri("https://github.com/xigua222/photoo")
                }
            )

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun PrivacyCard(
    title: String,
    content: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(color)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black.copy(alpha = 0.8f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = content,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = Color.Black.copy(alpha = 0.7f)
        )
    }
}
