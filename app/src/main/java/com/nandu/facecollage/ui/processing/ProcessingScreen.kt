package com.nandu.facecollage.ui.processing

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nandu.facecollage.pipeline.models.ProcessingPhase

@Composable
fun ProcessingScreen(phase: ProcessingPhase, videoLabel: String) {
    val progress by animateFloatAsState(
        targetValue = phase.overallProgress(),
        animationSpec = tween(durationMillis = 250),
        label = "progress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = videoLabel,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = phaseHeadline(phase),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            Text(
                text = phaseDetail(phase),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 28.dp)
            )

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

private fun phaseHeadline(phase: ProcessingPhase): String = when (phase) {
    is ProcessingPhase.Idle -> "Getting ready"
    is ProcessingPhase.LoadingVideo -> "Loading video"
    is ProcessingPhase.ExtractingFrames -> "Sampling frames"
    is ProcessingPhase.DetectingFaces -> "Detecting faces"
    is ProcessingPhase.BuildingAppearances -> "Tracking appearances"
    is ProcessingPhase.EmbeddingFaces -> "Computing face embeddings"
    is ProcessingPhase.ClusteringIdentities -> "Clustering identities"
    is ProcessingPhase.BuildingCollage -> "Building your collage"
    is ProcessingPhase.Done -> "Done"
}

private fun phaseDetail(phase: ProcessingPhase): String = when (phase) {
    is ProcessingPhase.Idle -> "Warming up the on-device pipeline"
    is ProcessingPhase.LoadingVideo -> "Reading video metadata"
    is ProcessingPhase.ExtractingFrames -> "${phase.done} of ${phase.total} frames sampled"
    is ProcessingPhase.DetectingFaces -> "${phase.done} of ${phase.total} frames scanned with ML Kit"
    is ProcessingPhase.BuildingAppearances -> "${phase.count} continuous appearances found so far"
    is ProcessingPhase.EmbeddingFaces -> "${phase.done} of ${phase.total} appearances embedded"
    is ProcessingPhase.ClusteringIdentities -> "Grouping appearances that are the same person"
    is ProcessingPhase.BuildingCollage -> "Picking the best shot for each person"
    is ProcessingPhase.Done -> "All set"
}
