package com.nandu.facecollage.ui.result

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.nandu.facecollage.ui.AppUiState
import com.nandu.facecollage.util.CollageSaveShare
import kotlinx.coroutines.launch

@Composable
fun ResultScreen(
    result: AppUiState.Result,
    onProcessAnother: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = result.videoLabel,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${result.people.size} distinct people · ${result.totalAppearances} total appearances",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            Image(
                bitmap = result.collage.asImageBitmap(),
                contentDescription = "Generated collage",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            val uri = CollageSaveShare.saveToGallery(context, result.collage)
                            val msg = if (uri != null) "Saved to gallery" else "Couldn't save collage"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) { Text("Save to gallery") }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val uri = CollageSaveShare.cacheForSharing(context, result.collage)
                            context.startActivity(CollageSaveShare.shareIntent(context, uri))
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) { Text("Share") }
            }
        }

        item {
            Text(
                text = "Appearance counts",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        items(result.people) { person ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Image(
                        bitmap = person.representativeShot.asImageBitmap(),
                        contentDescription = "Person ${person.personIndex}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(56.dp)
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = "Person ${person.personIndex}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        val word = if (person.appearanceCount == 1) "appearance" else "appearances"
                        Text(
                            text = "${person.appearanceCount} $word",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onProcessAnother,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) { Text("Process another video") }
        }
    }
}
