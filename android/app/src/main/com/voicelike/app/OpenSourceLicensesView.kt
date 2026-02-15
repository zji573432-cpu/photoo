package com.voicelike.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.scale

data class OpenSourceLibrary(
    val name: String,
    val author: String,
    val license: String,
    val url: String
)

@Composable
fun OpenSourceLicensesView(
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val layoutDirection = LocalLayoutDirection.current
    val uriHandler = LocalUriHandler.current

    val libraries = listOf(
        OpenSourceLibrary(
            "Jetpack Compose",
            "Google",
            "Apache 2.0",
            "https://developer.android.com/jetpack/compose"
        ),
        OpenSourceLibrary(
            "Kotlin",
            "JetBrains",
            "Apache 2.0",
            "https://kotlinlang.org/"
        ),
        OpenSourceLibrary(
            "Coil",
            "Coil Contributors",
            "Apache 2.0",
            "https://coil-kt.github.io/coil/"
        ),
        OpenSourceLibrary(
            "ExoPlayer (Media3)",
            "Google",
            "Apache 2.0",
            "https://developer.android.com/media/media3/exoplayer"
        ),
        OpenSourceLibrary(
            "Gson",
            "Google",
            "Apache 2.0",
            "https://github.com/google/gson"
        ),
        OpenSourceLibrary(
            "ML Kit",
            "Google",
            "Apache 2.0",
            "https://developers.google.com/ml-kit"
        ),
        OpenSourceLibrary(
            "AndroidX Libraries",
            "Google",
            "Apache 2.0",
            "https://developer.android.com/jetpack/androidx"
        ),
        OpenSourceLibrary(
            "Material Components",
            "Google",
            "Apache 2.0",
            "https://github.com/material-components/material-components-android"
        )
    )

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
                Icon(
                    Icons.Outlined.ArrowBack,
                    "Back",
                    tint = Color.Black,
                    modifier = if (layoutDirection == LayoutDirection.Rtl) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier
                )
            }

            Text(
                text = strings.openSourceLicenses ?: "Open Source Licenses",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
                color = Color.Black
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
        ) {
            items(libraries) { lib ->
                OpenSourceLibraryItem(lib) {
                    uriHandler.openUri(lib.url)
                }
                HorizontalDivider(
                    color = Color.Gray.copy(alpha = 0.1f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
fun OpenSourceLibraryItem(
    library: OpenSourceLibrary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = library.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${library.author} • ${library.license}",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        
        Icon(
            imageVector = Icons.Outlined.OpenInNew,
            contentDescription = "Open",
            tint = Color.Gray.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}
