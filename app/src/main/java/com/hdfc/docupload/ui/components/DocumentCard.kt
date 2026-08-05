package com.hdfc.docupload.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hdfc.docupload.domain.model.Document
import com.hdfc.docupload.domain.model.DocumentType
import com.hdfc.docupload.domain.model.UploadStatus
import com.hdfc.docupload.util.FileUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Displays a single document as a card: thumbnail, name, meta line, and one or
 * two trailing actions (remove/delete and optional preview).
 */
@Composable
fun DocumentCard(
    document: Document,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
    onRemove: (() -> Unit)? = null,
    onPreview: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Thumbnail(document)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = document.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = FileUtils.readableSize(document.sizeBytes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (showDate) {
                    Text(
                        text = dateFormatter.format(Date(document.createdAt)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                StatusChip(document.status)
            }

            onPreview?.let {
                IconButton(onClick = it) {
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = "Preview",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            onRemove?.let {
                IconButton(onClick = it) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(document: Document) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (document.type == DocumentType.PDF) {
            Icon(
                Icons.Default.PictureAsPdf,
                contentDescription = "PDF",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp)
            )
        } else {
            AsyncImage(
                model = document.localUri,
                contentDescription = document.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp)
            )
        }
    }
}

@Composable
private fun StatusChip(status: UploadStatus) {
    val (label, color) = when (status) {
        UploadStatus.PENDING -> "Pending" to MaterialTheme.colorScheme.secondary
        UploadStatus.UPLOADING -> "Uploading" to MaterialTheme.colorScheme.primary
        UploadStatus.UPLOADED -> "Uploaded" to MaterialTheme.colorScheme.primary
        UploadStatus.FAILED -> "Failed" to MaterialTheme.colorScheme.error
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = color
    )
}

private val dateFormatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
