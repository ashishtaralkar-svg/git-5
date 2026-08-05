package com.hdfc.docupload.ui.upload

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hdfc.docupload.R
import com.hdfc.docupload.ui.components.DocumentCard
import com.hdfc.docupload.ui.components.PrimaryButton
import com.hdfc.docupload.util.Constants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    onBack: () -> Unit,
    onViewUploaded: (String) -> Unit,
    viewModel: UploadViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pending by viewModel.pendingDocuments.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Gallery / file picker (images + PDF, multiple selection) via SAF — no runtime permission.
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.addFromGallery(uris) }

    // Document scanner (opens camera directly with full auto-detect experience).
    val launchScanner = rememberDocumentScanner(
        onResult = viewModel::addScannedPages,
        onError = viewModel::reportError
    )

    // Surface transient errors as a snackbar.
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.upload_documents))
                        Text(
                            text = "App No: ${viewModel.applicationNumber}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OptionCard(
                        icon = Icons.Default.PhotoLibrary,
                        label = stringResource(R.string.upload_from_mobile),
                        modifier = Modifier.weight(1f),
                        onClick = { galleryLauncher.launch(Constants.SUPPORTED_MIME_TYPES) }
                    )
                    OptionCard(
                        icon = Icons.Default.DocumentScanner,
                        label = stringResource(R.string.scan_document),
                        modifier = Modifier.weight(1f),
                        onClick = launchScanner
                    )
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Documents (${pending.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))

                if (pending.isEmpty()) {
                    EmptyState(modifier = Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(pending, key = { it.id }) { doc ->
                            DocumentCard(
                                document = doc,
                                onRemove = { viewModel.removeDocument(doc.id) }
                            )
                        }
                    }
                }

                if (state.isUploading) {
                    UploadProgress(progress = state.progress)
                }

                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = stringResource(R.string.upload),
                    onClick = viewModel::upload,
                    enabled = pending.isNotEmpty(),
                    loading = state.isUploading
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (state.showSuccess) {
        SuccessDialog(
            onViewUploaded = {
                viewModel.dismissSuccess()
                onViewUploaded(viewModel.applicationNumber)
            },
            onUploadMore = viewModel::dismissSuccess
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionCard(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.height(120.dp),
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.DocumentScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No documents yet.\nScan or upload to get started.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun UploadProgress(progress: Float) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            "Uploading… ${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@Composable
private fun SuccessDialog(
    onViewUploaded: () -> Unit,
    onUploadMore: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onUploadMore,
        title = { Text(stringResource(R.string.upload_success)) },
        text = { Text("Your documents have been uploaded. What would you like to do next?") },
        confirmButton = {
            TextButton(onClick = onViewUploaded) {
                Text(stringResource(R.string.view_uploaded_documents))
            }
        },
        dismissButton = {
            TextButton(onClick = onUploadMore) {
                Text(stringResource(R.string.upload_more))
            }
        }
    )
}
