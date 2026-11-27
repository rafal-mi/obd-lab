package com.example.obdlab

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.obdlab.storage.RecordingFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun RecordedFilesScreen() {
    val context = LocalContext.current
    var recordings by remember { mutableStateOf<List<RecordingFiles.Saved>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        recordings = withContext(Dispatchers.IO) { RecordingFiles(context.filesDir).list() }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Recorded files", style = MaterialTheme.typography.titleLarge)
        if (recordings.isEmpty()) Text("No saved recordings yet.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(recordings, key = { it.csv.name }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.csv.name.substringBeforeLast('_'), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = {
                            try {
                                val uris = listOf(item.csv, item.log).map {
                                    FileProvider.getUriForFile(context, context.packageName + ".recordings", it)
                                }
                                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "text/*"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                                    clipData = android.content.ClipData.newRawUri("Recording", uris.first()).apply {
                                        addItem(android.content.ClipData.Item(uris.last()))
                                    }
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share diagnostic files"))
                            } catch (e: Exception) { error = e.message ?: "Share failed" }
                        }) { Text("Share CSV and raw log") }
                    }
                }
            }
        }
    }
}
