package com.hdfc.docupload.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hdfc.docupload.R
import com.hdfc.docupload.ui.components.PrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onSearchSuccess: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.navigateTo) {
        state.navigateTo?.let {
            onSearchSuccess(it)
            viewModel.onNavigated()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = {
                        viewModel.logout()
                        onLogout()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = stringResource(R.string.logout),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Enter the application number to continue.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = state.applicationNumber,
                onValueChange = viewModel::onApplicationNumberChange,
                label = { Text(stringResource(R.string.application_number)) },
                leadingIcon = { Icon(Icons.Default.Numbers, null) },
                singleLine = true,
                isError = state.error != null,
                supportingText = { state.error?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(28.dp))

            PrimaryButton(
                text = stringResource(R.string.search),
                onClick = viewModel::search,
                loading = state.isLoading
            )
        }
    }
}
