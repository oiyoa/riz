package com.riz.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riz.app.R
import com.riz.app.crypto.FileExtensions
import com.riz.app.ui.formatCreatedAt

@Composable
fun SelectedFileRow(
    name: String,
    size: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier,
        headlineContent = {
            Text(
                text = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = { Text(text = size) },
        leadingContent = {
            FileLeadingIcon(
                icon = iconForFile(name),
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                badge = false,
            )
        },
        trailingContent = {
            // "Remove from this list," not "delete from disk" — softer Close icon, neutral tint.
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
fun ResultFileRow(
    name: String,
    fileSize: String,
    isExtractMode: Boolean,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    isSaved: Boolean = false,
    createdAt: Long? = null,
) {
    // Default tap follows the operation: extract → open the file; compress → share the blob.
    // The bottom-of-screen action buttons handle the "all" variants for multi-result; this
    // per-row tap is the per-file shortcut.
    val primaryAction = if (isExtractMode) onOpen else onShare

    ListItem(
        modifier = modifier.clickable(onClick = primaryAction),
        headlineContent = {
            Text(
                text = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                Text(text = fileSize)
                if (createdAt != null) {
                    Text(
                        text = formatCreatedAt(createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        leadingContent = {
            FileLeadingIcon(
                icon = iconForFile(name),
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                badge = isSaved,
            )
        },
        trailingContent = {
            // Inline Save button (the secondary action — primary is the row tap).
            // We dropped the overflow menu: the bottom-of-screen primary action
            // row covers the higher-level actions, so per-row stays one-tap.
            IconButton(onClick = onDownload) {
                Icon(
                    imageVector = Icons.Outlined.FileDownload,
                    contentDescription = stringResource(R.string.save),
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun FileLeadingIcon(
    icon: ImageVector,
    backgroundColor: Color,
    iconTint: Color,
    badge: Boolean,
) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
        if (badge) {
            // "Saved" badge — a small check overlay so the row reads at a
            // glance instead of forcing the user to scan supporting text.
            Box(
                modifier =
                    Modifier
                        .size(16.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = stringResource(R.string.saved),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}

private fun iconForFile(name: String): ImageVector {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        in FileExtensions.IMAGE -> Icons.Outlined.Image
        in FileExtensions.VIDEO -> Icons.Outlined.VideoFile
        in FileExtensions.AUDIO -> Icons.Outlined.AudioFile
        in FileExtensions.PDF -> Icons.Outlined.PictureAsPdf
        in FileExtensions.ARCHIVE -> Icons.Outlined.FolderZip
        in FileExtensions.APK -> Icons.Outlined.Android
        in FileExtensions.PLAIN_TEXT, in FileExtensions.DOCUMENT -> Icons.Outlined.Description
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}
