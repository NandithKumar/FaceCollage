package com.nandu.facecollage

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nandu.facecollage.ui.AppUiState
import com.nandu.facecollage.ui.AppViewModel
import com.nandu.facecollage.ui.home.HomeScreen
import com.nandu.facecollage.ui.processing.ProcessingScreen
import com.nandu.facecollage.ui.result.ResultScreen
import com.nandu.facecollage.ui.theme.FaceCollageTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.processVideo(uri, displayNameFor(uri))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FaceCollageTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val state by viewModel.state.collectAsState()

                    when (val s = state) {
                        is AppUiState.Home -> HomeScreen(
                            onPickVideo = { pickVideoLauncher.launch(arrayOf("video/*")) }
                        )
                        is AppUiState.Processing -> ProcessingScreen(
                            phase = s.phase,
                            videoLabel = s.videoLabel
                        )
                        is AppUiState.Result -> ResultScreen(
                            result = s,
                            onProcessAnother = { viewModel.reset() }
                        )
                        is AppUiState.Error -> ErrorScreen(
                            message = s.message,
                            onRetry = { viewModel.reset() }
                        )
                    }
                }
            }
        }
    }

    private fun displayNameFor(uri: Uri): String {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                } ?: "Selected video"
        } catch (t: Throwable) {
            "Selected video"
        }
    }
}

@androidx.compose.runtime.Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            Button(onClick = onRetry) { Text("Try again") }
        }
    }
}
