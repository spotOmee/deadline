package com.deadline.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deadline.ui.theme.BrandAmberStart

/**
 * Standardized Apple HIG Profile Avatar Component.
 * Permanently fixed in the top-left across all main screens.
 * Displays the user's active profile picture with smooth circle clipping,
 * or falls back to a crisp SF Person vector icon (never generic letters like 'D').
 */
@Composable
fun AppleProfileAvatar(
    avatarUri: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val context = LocalContext.current

    val avatarBitmap = remember(avatarUri) {
        if (!avatarUri.isNullOrBlank()) {
            try {
                val input = context.contentResolver.openInputStream(Uri.parse(avatarUri))
                BitmapFactory.decodeStream(input)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        } else null
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(BrandAmberStart.copy(alpha = 0.15f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = CircleShape
            )
            .clickable {
                HapticHelper.click(context)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        if (avatarBitmap != null) {
            Image(
                bitmap = avatarBitmap,
                contentDescription = "User Profile Picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            PersonIcon(
                color = BrandAmberStart,
                size = size * 0.55f
            )
        }
    }
}
