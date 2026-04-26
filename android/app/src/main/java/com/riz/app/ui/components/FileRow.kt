package com.riz.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riz.app.R
import com.riz.app.ui.theme.ThemePrimary

@Composable
fun SelectedFileRow(
    name: String,
    size: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FileRowBase(
        name = name,
        size = size,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        contentPadding = PaddingValues(12.dp),
        onClick = null,
    ) {
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun ResultFileRow(
    name: String,
    fileSize: String,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    isSaved: Boolean = false,
) {
    FileRowBase(
        name = name,
        size = fileSize,
        modifier =
            modifier
                .heightIn(min = 64.dp)
                .drawBehind {
                    val strokeWidth = 4.dp.toPx()
                    drawLine(
                        // Using ThemePrimary for result accent
                        color = ThemePrimary,
                        start = Offset(strokeWidth / 2, 0f),
                        end = Offset(strokeWidth / 2, size.height),
                        strokeWidth = strokeWidth,
                    )
                },
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        iconTint = MaterialTheme.colorScheme.primary,
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
        onClick = onDownload,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = onShare,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = stringResource(R.string.share),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            val containerColor =
                if (isSaved) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }

            val contentColor =
                if (isSaved) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }

            FilledTonalButton(
                onClick = onDownload,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(10.dp),
                colors =
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = containerColor,
                        contentColor = contentColor,
                    ),
            ) {
                AnimatedContent(
                    targetState = isSaved,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "saveState",
                ) { saved ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (saved) Icons.Outlined.Check else Icons.Outlined.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (saved) stringResource(R.string.saved) else stringResource(R.string.save),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRowBase(
    name: String,
    size: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Description,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    onClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(containerColor)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = size,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }

        actions()
    }
}
